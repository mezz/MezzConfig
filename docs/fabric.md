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

## Optional: include MezzConfig in your jar

You can include MezzConfig when your mod must work as a single download. This
makes installation simpler, but increases your jar size and packages another
copy in every mod that uses this option.

Fabric Loom's `include` configuration is not transitive, so add both the Fabric
loader adapter and the Common runtime:

```kotlin
dependencies {
	include("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
	include("net.mezzdev.config:mezz_config-1.21.1-config:$mezzConfigVersion")
}
```

Run `./gradlew build` as usual. The release jar now contains MezzConfig's loader
adapter and runtime, and satisfies the same required dependency in
`fabric.mod.json`; do not remove that entry.

For more detail, see the official
[Fabric Loom dependency documentation](https://docs.fabricmc.net/develop/loom/#dependency-configurations).
