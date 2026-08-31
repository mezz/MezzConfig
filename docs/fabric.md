# Fabric Loom setup

Jar-in-Jar embeds MezzConfig in your production mod while keeping it available
as a remapped mod in development. This branch targets Minecraft 1.21.1 and Java
21.

## Gradle

Set the MezzConfig version and add its Maven repository:

```kotlin
val mezzConfigVersion = "<version>"

repositories {
	maven("https://maven.blamejared.com")
}
```

Add the API, local runtime, and nested jar together:

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	modLocalRuntime("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
	include("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
}
```

`compileOnly` restricts source integrations to the public API.
`modLocalRuntime` supplies the remapped development runtime without publishing
it as a transitive dependency. `include` embeds the loader jar and is not
transitive.

Run `./gradlew build` and distribute the remapped production jar from
`build/libs`.

## Mod metadata

Keep MezzConfig as a required dependency in `fabric.mod.json`. Merge this entry
into the existing `depends` object:

```json
"depends": {
  "mezz_config": ">=0.3.0 <1.0.0"
}
```

The nested mod satisfies this dependency and gives Fabric Loader the required
load ordering. Change the range only to versions your mod actually supports.

See the official Loom documentation for
[`modLocalRuntime` and `include`](https://docs.fabricmc.net/develop/loom/#dependency-configurations).
