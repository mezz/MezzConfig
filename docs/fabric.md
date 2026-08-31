# Fabric Loom setup

## Gradle

In `build.gradle.kts`, choose the MezzConfig version and add its Maven
repository:

```kotlin
val mezzConfigVersion = "<version>"

repositories {
	maven("https://maven.blamejared.com")
}
```

Add all three dependencies:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	modLocalRuntime("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
	include("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
}
```

With this setup, your code can use MezzConfig, local game runs can load it, and
your release jar includes it for players.

Run `./gradlew build` and distribute your normal release jar from `build/libs`.

## Tell Fabric Loader about MezzConfig

Keep MezzConfig as a required dependency in `fabric.mod.json`. Merge this entry
into the existing `depends` object:

```json
"depends": {
  "mezz_config": ">=0.3.0 <1.0.0"
}
```

This tells Fabric Loader to start MezzConfig before your mod. The copy in your
jar satisfies the dependency. Change the version range only when your mod
supports a different range.

For more detail, see the official
[Fabric Loom dependency documentation](https://docs.fabricmc.net/develop/loom/#dependency-configurations).
