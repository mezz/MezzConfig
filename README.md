# MezzConfig

MezzConfig is a typed configuration library for Minecraft mods. It gives mods
one API for client preferences, per-world client settings, and
server-authoritative settings synchronized to connected clients.

MezzConfig focuses on the parts of configuration that are difficult to get
right across loaders:

- typed values with validation, defaults, and automatic file recovery;
- Fabric, Forge, and NeoForge support through the same public API;
- server-owned settings with one-way synchronization to clients;
- atomic updates, change listeners, restart requirements, and migration tools;
- persistent user-defined sorting for values discovered at runtime.

The current branch targets Minecraft 1.21.1 and Java 21.

## Add MezzConfig to your mod

Jar-in-Jar is the recommended setup. It gives the development environment the
full MezzConfig runtime and embeds the matching loader jar in the production
mod, so players do not need to install MezzConfig separately.

Set the version used by every configuration and add the Maven repository:

```kotlin
val mezzConfigVersion = "<version>"
val mezzConfigVersionRange = "[0.3.0,1.0.0)"

repositories {
	maven("https://maven.blamejared.com")
}
```

The `config-api` artifact is compile-time only. The loader artifact provides the
development runtime and is embedded in the final jar. Keep every dependency on
the same preferred version.

### Fabric Loom

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	modRuntimeOnly("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
	include("net.mezzdev.config:mezz_config-1.21.1-fabric:$mezzConfigVersion")
}
```

### ForgeGradle

```kotlin
jarJar.enable()

dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	runtimeOnly(fg.deobf("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersion"))
	jarJar("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersionRange") { jarJar.pin(this, mezzConfigVersion) }
}
```

Build with `./gradlew jarJar` and distribute the generated `-all.jar`, not the
plain jar.

### NeoForge ModDevGradle

```kotlin
dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	runtimeOnly("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion")
	jarJar("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

For Fabric and NeoForge, the normal `build` task produces the jar with the
nested dependency. For all loaders, keep `mezz_config` as a required dependency
in the mod metadata; the nested mod satisfies that dependency and establishes
load ordering. Widen `mezzConfigVersionRange` only to versions your mod actually
supports.

See the loader references for [Loom `include`](https://docs.fabricmc.net/develop/loom/#dependency-configurations),
[ForgeGradle Jar-in-Jar](https://docs.minecraftforge.net/en/fg-6.x/dependencies/jarinjar/),
and [ModDevGradle Jar-in-Jar](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#jar-in-jar).

Compile integrations against `net.mezzdev.config.api`. Implementation packages
and loader internals are not compatibility-guaranteed API.

## Documentation

Start with the [API guide](docs/API.md). Deeper guides cover
[config schemas](docs/config-schemas.md),
[custom values](docs/custom-values.md), [migrations](docs/migrations.md), and
[sorting configs](docs/sorting.md). The published Javadocs are the reference for
exact contracts and exceptions.

## Building MezzConfig

Build all artifacts and run the test suite with Java 21:

```text
./gradlew build
```

Run the complete release validation with:

```text
./gradlew spotlessCheck build :Common:apiJavadoc :Common:checkJarCompatibility validatePublishing
```

## License

MezzConfig is available under the [MIT License](LICENSE).
