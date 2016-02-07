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

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * A simple {@link Sponge} implementation of {@link StatsLite}.
 *
 * <p>Example usage for plugins:
 * <pre>
 *     {@link Inject @Inject} public {@link SpongeStatsLite} stats;
 *
 *     {@link Listener @Listener}
 *     public void onPreInitialize({@link GamePreInitializationEvent} event) {
 *         this.stats.start();
 *     }
 * </pre>
 *
 * @see StatsLite
 */
@Singleton
public final class SpongeStatsLite extends StatsLite {

    private final PluginContainer plugin;
    private Task task;

    /**
     * Constructs a new {@link SpongeStatsLite} client. Normally this should
     * be not called manually, but rather through an Guice {@link Inject}.
     *
     * @param plugin The plugin container
     * @param configDir The shared config directory
     * @see SpongeStatsLite
     */
    @Inject
    public SpongeStatsLite(PluginContainer plugin, @ConfigDir(sharedRoot = true) Path configDir) {
        super(configDir);
        this.plugin = requireNonNull(plugin, "plugin");
    }

    @Override
    protected void register(int interval, TimeUnit unit) {
        this.task = Sponge.getScheduler().createTaskBuilder()
                .async()
                .interval(interval, unit)
                .execute(this)
                .submit(this.plugin);
    }

    @Override
    protected void log(String message) {
        this.plugin.getLogger().info(message);
    }

    @Override
    protected void handleException(String message, Exception e) {
        this.plugin.getLogger().warn(message, e);
    }

    @Override
    protected void handleSubmitException(Exception e) {
        this.plugin.getLogger().debug("Failed to submit plugin statistics: {}", Throwables.getRootCause(e).toString());
    }

    @Override
    protected void cancel() {
        this.task.cancel();
        this.task = null;
    }

    @Override
    protected String getPluginName() {
        return this.plugin.getName();
    }

    @Override
    protected String getPluginVersion() {
        return this.plugin.getVersion();
    }

    @Override
    protected String getServerVersion() {
        final Platform platform = Sponge.getPlatform();
        return platform.getImplementation().getName() + ' ' + platform.getImplementation().getVersion()
                + " (MC: " + platform.getMinecraftVersion().getName() + ')';
    }

    @Override
    protected int getOnlinePlayerCount() {
        return Sponge.getServer().getOnlinePlayers().size();
    }

    @Override
    protected boolean isOnlineMode() {
        return Sponge.getServer().getOnlineMode();
    }

}
