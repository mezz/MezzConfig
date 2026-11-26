# Fabric Loom setup

The examples use Minecraft 1.21.1. In the MezzConfig artifact names, replace
`1.21.1` with your mod's [supported Minecraft version](../README.md#supported-minecraft-versions).

## Gradle

In `build.gradle.kts`, choose the MezzConfig version and add its Maven
repository:

```kotlin
val mezzConfigVersion = "<version>"

repositories {
	maven("https://maven.blamejared.com")
}
```

Add both dependencies:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	modLocalRuntime("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
}
```

This lets your code use MezzConfig and makes it available to local game runs.

Run `./gradlew build` and distribute your normal release jar from `build/libs`.

## Tell Fabric Loader about MezzConfig

Keep MezzConfig as a required dependency in `fabric.mod.json`. Merge this entry
into the existing `depends` object:

```json
"depends": {
  "mezz_config": ">=0.5.0 <1.0.0"
}
```

This tells Fabric Loader to require MezzConfig and start it before your mod.
Change the version range only when your mod supports a different range.
