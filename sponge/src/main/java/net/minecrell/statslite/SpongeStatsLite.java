package net.minecrell.statslite;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Singleton
public final class SpongeStatsLite extends StatsLite {

    private final PluginContainer plugin;
    private Task task;

    @Inject
    public SpongeStatsLite(PluginContainer plugin, @ConfigDir(sharedRoot = true) Path configDir) {
        super(new SimpleConfigFileProvider(configDir.resolve("statslite.properties")));
        this.plugin = plugin;
    }

    @Override
    protected boolean register(int interval, TimeUnit unit) {
        if (this.task == null) {
            this.task = Sponge.getScheduler().createTaskBuilder()
                    .async()
                    .interval(interval, unit)
                    .execute(this)
                    .submit(this.plugin);
            return true;
        }
        return false;
    }

    @Override
    protected void handleException(Exception e) {
        this.plugin.getLogger().debug("Failed to submit plugin statistics: {}", Throwables.getRootCause(e).toString());
    }

    @Override
    protected boolean cancel() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
            return true;
        }
        return false;
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
