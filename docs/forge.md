# ForgeGradle setup

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

Add the following setup and dependencies:

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

With this setup, your code can use MezzConfig, local game runs can load it, and
your release jar includes it for players. The exact version is the copy placed
in your jar; the range lets Forge share one compatible copy when several mods
include MezzConfig.

Run `./gradlew jarJar` and distribute the generated `-all.jar`, not the plain
jar.

## Tell Forge about MezzConfig

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

This tells Forge to start MezzConfig before your mod. The copy in your jar
satisfies the dependency. Keep this range the same as
`mezzConfigVersionRange`.

See the official
[ForgeGradle Jar-in-Jar documentation](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/).
