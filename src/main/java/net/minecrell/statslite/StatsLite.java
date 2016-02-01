package net.minecrell.statslite;

import static java.util.Objects.requireNonNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

public abstract class StatsLite implements Runnable {

    private static final int REVISION = 7; // Plugin-Metrics revision
    private static final String BASE_URL = "http://report.mcstats.org";
    private static final String REPORT_URL = BASE_URL + "/plugin/";

    private static final int PING_INTERVAL = 15; // In minutes
    private static final TimeUnit PING_INTERVAL_UNIT = TimeUnit.MINUTES;

    private static final Gson gson = new Gson();

    private final ConfigProvider config;

    private boolean running;
    private boolean ping;

    protected StatsLite(ConfigProvider config) {
        this.config = requireNonNull(config, "config");
    }

    protected abstract boolean register(int interval, TimeUnit unit);

    public final boolean start() throws IOException {
        if (!this.running) {
            this.config.reload();
            if (!this.config.isOptOut() && register(PING_INTERVAL, PING_INTERVAL_UNIT)) {
                this.running = true;
                return true;
            }
        }

        return false;
    }

    @Override
    public final void run() {
        try {
            this.config.reload();
        } catch (IOException e) {
            // If we can't read the configuration we can likely not
            // later either, so just stop trying
            this.handleException(e);
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
            this.handleException(e);
        }
    }

    protected abstract void handleException(Exception e);

    protected abstract boolean cancel();

    public final void stop() {
        if (this.running && this.cancel()) {
            this.running = false;
            this.ping = false;
        }
    }

    protected abstract String getPluginName();
    protected abstract String getPluginVersion();

    protected abstract String getServerVersion();
    protected abstract int getOnlinePlayerCount();
    protected abstract boolean isOnlineMode();

    private void post(String guid, boolean ping) throws IOException {
        final String pluginName = this.getPluginName();
        final String pluginVersion = this.getPluginVersion();
        final String serverVersion = this.getServerVersion();
        final int online = this.getOnlinePlayerCount();
        final boolean onlineMode = this.isOnlineMode();

        // Create data object
        JsonObject jsonData = new JsonObject();

        // Plugin and server information
        jsonData.addProperty("guid", guid);
        jsonData.addProperty("plugin_version", pluginVersion);

        jsonData.addProperty("server_version", serverVersion);
        jsonData.addProperty("players_online", online);
        jsonData.addProperty("auth_mode", onlineMode ? 1 : 0);

        // New data as of R6, system information
        jsonData.addProperty("osname", System.getProperty("os.name"));
        final String osArch = System.getProperty("os.arch");
        jsonData.addProperty("osarch", osArch.equals("amd64") ? "x86_64" : osArch);
        jsonData.addProperty("osversion", System.getProperty("os.version"));
        jsonData.addProperty("cores", Runtime.getRuntime().availableProcessors());
        jsonData.addProperty("java_version", System.getProperty("java.version"));

        if (ping) {
            jsonData.addProperty("ping", 1);
        }

        // Get json output from GSON
        String json = gson.toJson(jsonData);
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

        // Write json data to the opened stream
        try (OutputStream out = con.getOutputStream()) {
            out.write(data);
        }

        String response; // Read the response
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            response = reader.readLine();
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

    public interface ConfigProvider {

        void reload() throws IOException;

        boolean isOptOut();
        String getUniqueId();

    }

}
