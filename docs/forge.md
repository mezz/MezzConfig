# Forge Legacy ModDevGradle setup

This guide is for projects using `net.neoforged.moddev.legacyforge` on Minecraft
1.19.2 or 1.20.1. The examples use 1.20.1; use your Minecraft version in the
MezzConfig artifact names.

## Gradle

In `build.gradle.kts`, choose the MezzConfig version and add its Maven
repository:

```kotlin
val mezzConfigVersion = "<version>"

repositories {
	maven("https://maven.blamejared.com")
}
```

After configuring `legacyForge`, add the following setup and dependencies:

```kotlin
val mezzConfigRuntime = obfuscation.createRemappingConfiguration(
    configurations.getByName("additionalRuntimeClasspath")
)

dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.20.1-config-api:$mezzConfigVersion")
	add(mezzConfigRuntime.name, "net.mezzdev.config:mezz_config-1.20.1-forge:$mezzConfigVersion")
}
```

The API dependency lets your code use MezzConfig. The remapping configuration
makes its Forge runtime available to development game runs.

Run `./gradlew build` and distribute the release jar produced by `reobfJar`.

## Tell Forge about MezzConfig

Keep MezzConfig as a required dependency in `META-INF/mods.toml`. Replace
`your_mod_id` with your mod id:

```toml
[[dependencies.your_mod_id]]
    modId="mezz_config"
    mandatory=true
    versionRange="[0.5.0,1.0.0)"
    ordering="AFTER"
    side="BOTH"
```

This tells Forge to require MezzConfig and start it before your mod. Change the
version range only when your mod supports a different range.

## Optional: include MezzConfig in your jar

You can include MezzConfig when your mod must work as a single download. This
makes installation simpler, but increases your jar size and packages another
copy in every mod that uses this option.

Add the supported range and the complete Forge artifact:

```kotlin
val mezzConfigVersionRange = "[0.5.0,1.0.0)"

dependencies {
	jarJar("net.mezzdev.config:mezz_config-1.20.1-forge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

Keep the range the same as the one in `mods.toml`. Run `./gradlew build` and
distribute the release jar produced by `reobfJar`. The included copy satisfies
the required dependency; keep the `mods.toml` entry.
