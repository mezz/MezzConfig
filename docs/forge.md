# ForgeGradle setup

## Gradle

In `build.gradle.kts`, choose the MezzConfig version and add its Maven
repository:

```kotlin
val mezzConfigVersion = "<version>"

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

dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	mezzConfigLocalRuntime(fg.deobf("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersion"))
}
```

This lets your code use MezzConfig and makes it available to local game runs.
Your release jar does not contain MezzConfig, so players and modpacks can
install one shared copy.

Run `./gradlew build` and distribute your normal release jar from `build/libs`.

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

This tells Forge to require MezzConfig and start it before your mod. Change the
version range only when your mod supports a different range.

## Optional: include MezzConfig in your jar

You can include MezzConfig when your mod must work as a single download. This
makes installation simpler, but increases your jar size and packages another
copy in every mod that uses this option.

Add the supported range and Jar-in-Jar dependency:

```kotlin
val mezzConfigVersionRange = "[0.3.0,1.0.0)"

jarJar.enable()

dependencies {
	jarJar("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersionRange") { jarJar.pin(this, mezzConfigVersion) }
}
```

Keep the range the same as the one in `mods.toml`. Run `./gradlew jarJar` and
distribute the generated `-all.jar`, not the plain jar. The included copy
satisfies the required dependency; do not remove the `mods.toml` entry.

See the official
[ForgeGradle Jar-in-Jar documentation](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/).
