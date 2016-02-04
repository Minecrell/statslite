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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;

final class SimpleConfigFileProvider implements StatsLite.ConfigProvider {

    private final Path configFile;

    private boolean optOut;
    private String uniqueId;

    SimpleConfigFileProvider(Path configFile) {
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
