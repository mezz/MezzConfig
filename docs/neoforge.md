# NeoForge ModDevGradle setup

## Gradle

In `build.gradle.kts`, choose the MezzConfig version, set the versions your mod
supports, and add its Maven repository:

```kotlin
val mezzConfigVersion = "<version>"
val mezzConfigVersionRange = "[0.3.0,1.0.0)"

repositories {
	maven("https://maven.blamejared.com")
}
```

Add all three dependencies:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	additionalRuntimeClasspath("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion")
	jarJar("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

With this setup, your code can use MezzConfig, local game runs can load it, and
your release jar includes it for players. The exact version is the copy placed
in your jar; the range lets NeoForge share one compatible copy when several
mods include MezzConfig.

Run `./gradlew build` and distribute the production jar from `build/libs`.

## Tell NeoForge about MezzConfig

Keep MezzConfig as a required dependency in `META-INF/neoforge.mods.toml`.
Replace `your_mod_id` with your mod id:

```toml
[[dependencies.your_mod_id]]
    modId="mezz_config"
    type="required"
    versionRange="[0.3.0,1.0.0)"
    ordering="AFTER"
    side="BOTH"
```

This tells NeoForge to start MezzConfig before your mod. The copy in your jar
satisfies the dependency. Keep this range the same as
`mezzConfigVersionRange`.

See the official
[ModDevGradle Jar-in-Jar documentation](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar).
