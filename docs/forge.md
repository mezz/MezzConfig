# ForgeGradle setup

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

Create a private development-runtime bucket before declaring dependencies:

```kotlin
val mezzConfigLocalRuntime by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = false
}
configurations.runtimeClasspath {
	extendsFrom(mezzConfigLocalRuntime)
}

jarJar.enable()

dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	mezzConfigLocalRuntime(fg.deobf("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersion"))
	jarJar("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersionRange") { jarJar.pin(this, mezzConfigVersion) }
}
```

The private bucket prevents ForgeGradle's mapped development artifact from
appearing in your published Maven or Gradle metadata. `jarJar.pin` selects the
preferred embedded version while preserving the supported range for dependency
selection.

Run `./gradlew jarJar` and distribute the generated `-all.jar`, not the plain
jar.

## Mod metadata

Keep MezzConfig as a required dependency in `META-INF/mods.toml`. Replace
`your_mod_id` with your mod id:

```toml
[[dependencies.your_mod_id]]
    modId="mezz_config"
    mandatory=true
    versionRange="[0.3.0,1.0.0)"
    ordering="AFTER"
    side="BOTH"
```

The nested mod satisfies this dependency and establishes load ordering. Keep
this range aligned with `mezzConfigVersionRange`.

See the official
[ForgeGradle Jar-in-Jar documentation](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/).
