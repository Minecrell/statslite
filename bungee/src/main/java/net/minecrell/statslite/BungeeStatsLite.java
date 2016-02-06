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
package net.minecrell.statslite;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * A simple BungeeCord implementation of {@link StatsLite}.
 *
 * <p>Example usage for plugins:
 * <pre>
 *     private final {@link BungeeStatsLite} stats = new {@link BungeeStatsLite}(this);
 *
 *     {@link Override @Override}
 *     public void onEnable() {
 *         this.stats.start();
 *     }
 * </pre>
 *
 * @see StatsLite
 */
public final class BungeeStatsLite extends StatsLite {

    private final Plugin plugin;
    private ScheduledTask task;

    /**
     * Constructs a new {@link BungeeStatsLite} client for the specified plugin.
     *
     * @param plugin The plugin
     */
    public BungeeStatsLite(Plugin plugin) {
        super(BungeeConfigProvider.INSTANCE);
        this.plugin = requireNonNull(plugin, "plugin");
    }

    @Override
    protected void register(int interval, TimeUnit unit) {
        this.task = this.plugin.getProxy().getScheduler().schedule(this.plugin, this, 0, interval, unit);
    }

    @Override
    protected void debug(String message) {
        this.plugin.getLogger().log(Level.FINE, message);
    }

    @Override
    protected void handleException(String message, Exception e) {
        this.plugin.getLogger().log(Level.WARNING, message, e);
    }

    @Override
    protected void handleSubmitException(Exception e) {
        this.plugin.getLogger().log(Level.FINE, "Failed to submit plugin statistics: {0}", Throwables.getRootCause(e).toString());
    }

    @Override
    protected void cancel() {
        this.task.cancel();
        this.task = null;
    }

    @Override
    protected String getPluginName() {
        return this.plugin.getDescription().getName();
    }

    @Override
    protected String getPluginVersion() {
        return this.plugin.getDescription().getVersion();
    }

    @Override
    protected String getServerVersion() {
        return this.plugin.getProxy().getVersion() + " (MC: " + this.plugin.getProxy().getGameVersion() + ')';
    }

    @Override
    protected int getOnlinePlayerCount() {
        return this.plugin.getProxy().getOnlineCount();
    }

    @Override
    protected boolean isOnlineMode() {
        return this.plugin.getProxy().getConfig().isOnlineMode();
    }

}
