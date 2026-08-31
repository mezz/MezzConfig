# NeoForge ModDevGradle setup

Jar-in-Jar embeds MezzConfig in your production mod while keeping it available
in development. This branch targets Minecraft 1.21.1 and Java 21.

## Gradle

Set the preferred version, its compatible range, and the Maven repository:

```kotlin
val mezzConfigVersion = "<version>"
val mezzConfigVersionRange = "[0.3.0,1.0.0)"

repositories {
	maven("https://maven.blamejared.com")
}
```

Add the API, local runtime, and nested jar together:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	additionalRuntimeClasspath("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion")
	jarJar("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

`compileOnly` restricts source integrations to the public API.
`additionalRuntimeClasspath` supplies MezzConfig to development runs without
publishing it as a transitive dependency. The rich `jarJar` version embeds the
preferred version while preserving the supported range for dependency
selection.

Run `./gradlew build` and distribute the production jar from `build/libs`.

## Mod metadata

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

The nested mod satisfies this dependency and establishes load ordering. Keep
this range aligned with `mezzConfigVersionRange`.

See the official
[ModDevGradle Jar-in-Jar documentation](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar).
