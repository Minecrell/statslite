package net.minecrell.statslite;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;

public final class SimpleConfigFileProvider implements StatsLite.ConfigProvider {

    private final Path configFile;

    private boolean optOut;
    private String uniqueId;

    public SimpleConfigFileProvider(Path configFile) {
        this.configFile = requireNonNull(configFile, "configFile");
    }

    @Override
    public void reload() throws IOException {
        if (Files.exists(this.configFile)) {
            Properties properties = new Properties();
            try (InputStream in = Files.newInputStream(this.configFile)) {
                properties.load(in);
            }

            this.optOut = Boolean.parseBoolean(properties.getProperty("opt-out"));
            this.uniqueId = properties.getProperty("guid");
        } else {
            Properties properties = new Properties();
            properties.put("opt-out", "false");
            properties.put("guid", UUID.randomUUID().toString());

            try (OutputStream out = Files.newOutputStream(this.configFile, StandardOpenOption.CREATE)) {
                properties.store(out, "StatsLite configuration, set opt-out to true to stop contacting http://mcstats.org");
            }
        }
    }

    @Override
    public boolean isOptOut() {
        return this.optOut;
    }

    @Override
    public String getUniqueId() {
        return this.uniqueId;
    }

}
