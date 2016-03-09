# statslite
statslite is a custom (unofficial) client implementation for MCStats (http://mcstats.org). It can be used to submit plugin statistic data (e.g. usage count, online players, ...) on one of the supported platforms. [See the Plugin-Metrics wiki for some details](https://github.com/Hidendra/Plugin-Metrics/wiki).

Currently the following platforms are supported:
- Sponge
- BungeeCord

## Usage
### Build system
statslite needs to be added as Maven or Gradle dependency, and later shaded and relocated into the plugin JAR.

#### Gradle
Add something like the following to your `build.gradle`. Replace `PLATFORM` with the platform you want to use.
```gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '1.2.3'
}

...

repositories {
    maven {
        name = 'minecrell'
        url = 'http://repo.minecrell.net/releases'
    }
}

dependencies {
    compile 'net.minecrell.mcstats:statslite-PLATFORM:0.2.2'
}

shadowJar {
    dependencies {
        include dependency('net.minecrell.mcstats:statslite-PLATFORM')
    }
    
    relocate 'net.minecrell.mcstats', 'YOUR.PLUGIN.PACKAGE.mcstats'
}

artifacts {
    archives shadowJar
}
```

This will result in an additional `-all.jar` with the shaded dependency to be created. See the [Shadow plugin documentation](https://github.com/johnrengelman/shadow#readme) for details.

### Platforms
statslite is currently supporting BungeeCord (`statslite-bungee`) and Sponge (`statslite-sponge`).

#### Sponge
Add the following to your plugin class:

```java
@Inject public SpongeStatsLite stats;

@Listener
public void onPreInitialize(GamePreInitializationEvent event) {
    this.stats.start();
}
```

The client will generate a configuration file in `config/mcstats.properties` where the users can opt-out the statistics.

#### BungeeCord
Add the following to your plugin class:

```java
private final BungeeStatsLite stats = new BungeeStatsLite(this);

@Override
public void onEnable() {
    this.stats.start();
}
```

The client will use the unique identifier (`guid`) from the BungeeCord configuration and can be disabled by setting that to `null`.
