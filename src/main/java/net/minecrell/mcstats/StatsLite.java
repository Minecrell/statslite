/*
 * Copyright (c) 2016, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.minecrell.mcstats;

import static java.util.Objects.requireNonNull;

import com.google.gson.stream.JsonWriter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * A simple client implementation for <a href="http://mcstats.org">MCStats</a>
 * that works on multiple platforms. It can be used to send statistical data
 * for plugins to MCStats. Currently, the following data is transmitted:
 *
 * <ul>
 *     <li>Plugin name</li>
 *     <li>Plugin version</li>
 *     <li>Server version</li>
 *     <li>Online player count</li>
 *     <li>Online mode</li>
 *     <li>Operating system name, version, architecture and the number of cores
 *         on the host system</li>
 *     <li>Java version</li>
 * </ul>
 *
 * <p>Currently, custom graphs are not supported.</p>
 *
 * <p>Implementations for platforms need to extend this class to provide the
 * getters for the platform-specific data and to schedule the task.</p>
 */
public abstract class StatsLite implements Runnable {

    public static final String VERSION = "0.2.2";

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("mcstats.debug"));
    private static final String DEFAULT_CONFIG_FILE = "mcstats.properties";

    private static final int REVISION = 7; // Plugin-Metrics revision
    private static final String BASE_URL = "http://report.mcstats.org";
    private static final String REPORT_URL = BASE_URL + "/plugin/";

    private static final int PING_INTERVAL = 15; // In minutes
    private static final TimeUnit PING_INTERVAL_UNIT = TimeUnit.MINUTES;

    private final ConfigProvider config;

    private boolean running;
    private boolean ping;

    /**
     * Constructs a new {@link StatsLite} client using a custom
     * {@link ConfigProvider}.
     *
     * <p><b>Note:</b> In most cases it is recommended to use the default config
     * provider that stores the settings for disabling in a file.</p>
     *
     * @param config The custom config provider
     */
    protected StatsLite(ConfigProvider config) {
        this.config = requireNonNull(config, "config");
    }

    /**
     * Constructs a new {@link StatsLite} client using the standard
     * {@link ConfigProvider} that stores the configuration in a file in the
     * given config directory.
     *
     * @param configDir The directory to store the config file in
     */
    protected StatsLite(Path configDir) {
        requireNonNull(configDir, "configDir");
        this.config = new SimpleConfigFileProvider(configDir.resolve(DEFAULT_CONFIG_FILE));
    }

    /**
     * Registers this {@link Runnable} with the specified interval on the
     * platform-specific scheduler.
     *
     * @param interval The interval to execute the task in
     * @param unit The unit the interval is in
     */
    protected abstract void register(int interval, TimeUnit unit);

    /**
     * Attempts to start the {@link StatsLite} client and returns whether the
     * operation was successful.
     *
     * <p>Unsuccessful starting on the client does not necessarily mean
     * something is broken, because it the client will disable itself
     * if the user has chosen to opt-out the plugin statistics.</p>
     *
     * @return True if the operation was successful
     */
    public final boolean start() {
        if (!this.running) {
            try {
                this.config.reload();
                if (!this.config.isOptOut()) {
                    this.running = true;
                    this.ping = false;
                    register(PING_INTERVAL, PING_INTERVAL_UNIT);
                    return true;
                }
            } catch (Exception e) {
                this.handleException("Failed to start plugin statistic client", e);
            }
        }

        return false;
    }

    /**
     * Submits the statistic data on the current thread. If the user has
     * modified the configuration in the meantime the task will disable
     * itself if the user has chosen to opt-out.
     */
    @Override
    public final void run() {
        try {
            this.config.reload();
        } catch (IOException e) {
            // If we can't read the configuration we can likely not
            // later either, so just stop trying
            this.handleException("Failed to reload mcstats configuration", e);
            this.stop();
        }

        // Check if the user has opted out in the meantime
        if (this.config.isOptOut()) {
            this.stop();
            return;
        }

        // Submit statistics
        try {
            post(this.config.getUniqueId(), this.ping);
            this.ping = true;
        } catch (Exception e) {
            if (DEBUG) {
                this.handleException("Failed to submit plugin statistics", e);
            } else {
                this.handleSubmitException(e);
            }
        }
    }

    /**
     * Sends the specified message to the logger.
     * <p><b>Note:</b> This should generally report on the <i>info</i> level.</p>
     *
     * @param message The message to log
     */
    protected abstract void log(String message);

    /**
     * Reports the exceptions together with the specified message on the logger.
     * <p><b>Note:</b> This should generally report on the <i>warn</i> level.</p>
     *
     * @param message The message to log
     * @param e The exception to log
     */
    protected abstract void handleException(String message, Exception e);

    /**
     * Reports an exception that has happened while submitting the statistic data
     * on the logger.
     * <p><b>Note:</b> This should generally report on the <i>debug</i> level.</p>
     *
     * @param e The exception to log
     */
    protected abstract void handleSubmitException(Exception e);

    /**
     * Cancels the scheduled task so it is no longer executed. This method is
     * only called if the task was actually scheduled before.
     */
    protected abstract void cancel();

    /**
     * Attempts to stop the currently scheduled task so the client will no longer
     * submit statistic data to the server.
     *
     * @return If the client was running before
     */
    public final boolean stop() {
        if (this.running) {
            this.running = false;
            this.cancel();
            return true;
        }

        return false;
    }

    /**
     * Returns the display name of the plugin.
     *
     * @return The plugin display name
     */
    protected abstract String getPluginName();

    /**
     * Returns the display version of the plugin.
     *
     * @return The plugin display version
     */
    protected abstract String getPluginVersion();

    /**
     * Returns an unique version string for the currently running server
     * implementation.
     *
     * <p>The underlying Minecraft version should be defined as a suffix in the
     * following format: {@code (MC: 1.8.9)}</p>
     *
     * @return The server version
     */
    protected abstract String getServerVersion();

    /**
     * Returns the current count of online players on the server.
     *
     * @return The online player count
     */
    protected abstract int getOnlinePlayerCount();

    /**
     * Returns whether the server is running in online mode and is authenticating
     * the users with Mojang.
     *
     * @return Whether the server is running in online mode
     */
    protected abstract boolean isOnlineMode();

    private void post(String guid, boolean ping) throws IOException {
        final String pluginName = this.getPluginName();
        final String pluginVersion = this.getPluginVersion();
        final String serverVersion = this.getServerVersion();
        final int online = this.getOnlinePlayerCount();
        final boolean onlineMode = this.isOnlineMode();

        final StringWriter writer = new StringWriter();

        try (JsonWriter json = new JsonWriter(writer)) {
            json.beginObject();

            // Plugin and server information
            json.name("guid").value(guid);
            json.name("plugin_version").value(pluginVersion);

            json.name("server_version").value(serverVersion);
            json.name("players_online").value(online);
            json.name("auth_mode").value(onlineMode);

            // New data as of R6, system information
            json.name("osname").value(System.getProperty("os.name"));
            final String osArch = System.getProperty("os.arch");
            json.name("osarch").value(osArch.equals("amd64") ? "x86_64" : osArch);
            json.name("osversion").value(System.getProperty("os.version"));
            json.name("cores").value(Runtime.getRuntime().availableProcessors());
            json.name("java_version").value(System.getProperty("java.version"));

            if (ping) {
                json.name("ping").value(true);
            }

            json.endObject();
        }

        // Get json output from GSON
        final String json = writer.toString();

        if (DEBUG) {
            this.log("Generated json request: " + json);
        }

        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        byte[] gzip = null;

        try { // Compress using GZIP
            gzip = gzip(data);
        } catch (Exception ignored) {
        }

        // Connect to server
        URL url = new URL(REPORT_URL + URLEncoder.encode(pluginName, "UTF-8"));
        URLConnection con = url.openConnection();

        // Add request headers
        con.addRequestProperty("User-Agent", "MCStats/" + REVISION);
        con.addRequestProperty("Content-Type", "application/json");
        if (gzip != null) {
            con.addRequestProperty("Content-Encoding", "gzip");
            data = gzip;
        }
        con.addRequestProperty("Content-Length", Integer.toString(data.length));
        con.addRequestProperty("Accept", "application/json");
        con.addRequestProperty("Connection", "close");

        con.setDoOutput(true);

        if (DEBUG) {
            this.log("Sending " + data.length + " bytes to " + url);
        }

        // Write json data to the opened stream
        try (OutputStream out = con.getOutputStream()) {
            out.write(data);
        }

        String response; // Read the response
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            response = reader.readLine();
        }

        if (DEBUG) {
            HttpURLConnection http = (HttpURLConnection) con;
            this.log("Server replied with '" + response + "' (" + http.getResponseCode() + " - " + http.getResponseMessage() + ')');
        }

        if (response == null || response.startsWith("ERR") || response.startsWith("7")) {
            if (response == null) {
                response = "null";
            } else if (response.startsWith("7")) {
                response = response.substring(response.startsWith("7,") ? 2 : 1);
            }

            throw new IOException(response);
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gout = new GZIPOutputStream(out)) {
            gout.write(data);
        }
        return out.toByteArray();
    }

    /**
     * Represents a configuration provider for a {@link StatsLite} client.
     */
    public interface ConfigProvider {

        /**
         * Reloads the configuration from the source.
         *
         * @throws IOException If the configuration can't be read
         */
        void reload() throws IOException;

        /**
         * Returns whether the user has opted-out and chosen to disable the
         * statistic client.
         *
         * @return Whether the user has opted-out
         */
        boolean isOptOut();

        /**
         * Returns the string representation of an {@link UUID} for the current
         * server.
         *
         * @return The unique identifier
         */
        String getUniqueId();

    }

}
