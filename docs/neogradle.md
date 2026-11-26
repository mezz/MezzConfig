# NeoForge NeoGradle setup

This guide is for projects using NeoGradle's UserDev plugin. If your project
uses ModDevGradle, follow the [ModDevGradle guide](neoforge.md) instead.

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

Compile against the standalone API and put the NeoForge runtime on the development
run classpath:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	runtimeOnly("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion")
}
```

The NeoForge artifact contains the Common runtime, so no additional runtime
coordinate is needed. Run `./gradlew build` and distribute your normal release
jar from `build/libs`.

## Tell NeoForge about MezzConfig

Keep MezzConfig as a required dependency in `META-INF/neoforge.mods.toml`.
Replace `your_mod_id` with your mod id:

```toml
[[dependencies.your_mod_id]]
    modId="mezz_config"
    type="required"
    versionRange="[0.5.0,1.0.0)"
    ordering="AFTER"
    side="BOTH"
```

This tells NeoForge to require MezzConfig and start it before your mod. Change
the version range only when your mod supports a different range.
