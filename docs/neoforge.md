# NeoForge ModDevGradle setup

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
	additionalRuntimeClasspath("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion")
}
```

This lets your code use MezzConfig and makes it available to local game runs.

Run `./gradlew build` and distribute your normal release jar from `build/libs`.

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

This tells NeoForge to require MezzConfig and start it before your mod. Change
the version range only when your mod supports a different range.

## Optional: include MezzConfig in your jar

You can include MezzConfig when your mod must work as a single download. This
makes installation simpler, but increases your jar size and packages another
copy in every mod that uses this option.

Add the supported range and Jar-in-Jar dependency:

```kotlin
val mezzConfigVersionRange = "[0.3.0,1.0.0)"

dependencies {
	jarJar("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

Keep the range the same as the one in `neoforge.mods.toml`. Run
`./gradlew build` as usual. The release jar now contains MezzConfig and
satisfies the required dependency; do not remove the `neoforge.mods.toml`
entry.

See the official
[ModDevGradle Jar-in-Jar documentation](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar).
