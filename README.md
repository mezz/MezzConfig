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

## Documentation

- [API guide](docs/API.md): a complete first config and an overview of the API.
- [Config schemas](docs/config-schemas.md): ownership, locations, lifecycle,
  synchronization, categories, and updates.
- [Custom values](docs/custom-values.md): serializers, lists, validation, and
  editor metadata.
- [Migrations](docs/migrations.md): rename values, move files, and import older
  formats.
- [Sorting configs](docs/sorting.md): persist user-defined order for dynamic
  values.

The guides explain the intended workflows. The Javadocs published with the API
artifact are the reference for exact method contracts and exceptions.

## Add MezzConfig to your mod

Add the Maven repository:

```kotlin
repositories {
	maven("https://maven.blamejared.com")
}
```

Then declare the artifact for your loader, replacing `<version>` with the
MezzConfig version you use.

### Fabric Loom

```kotlin
dependencies {
	modImplementation("net.mezzdev.config:mezz_config-1.21.1-fabric:<version>")
}
```

### ForgeGradle

```kotlin
dependencies {
	implementation(fg.deobf(
		"net.mezzdev.config:mezz_config-1.21.1-forge:<version>"
	))
}
```

### NeoForge ModDevGradle

```kotlin
dependencies {
	implementation("net.mezzdev.config:mezz_config-1.21.1-neoforge:<version>")
}
```

Declare one loader artifact in each loader module. It brings in the public API
and common runtime through its Maven metadata.

A shared source module that must not depend on a loader can compile against the
API-only artifact:

```kotlin
dependencies {
	compileOnly(
		"net.mezzdev.config:mezz_config-1.21.1-config-api:<version>"
	)
}
```

The final mod still needs its loader-specific MezzConfig artifact at runtime.
Compile integrations against `net.mezzdev.config.api`; implementation packages
and loader internals are not compatibility-guaranteed API.

## Building MezzConfig

Build all artifacts and run the test suite with Java 21:

```text
./gradlew build
```

Run the complete release validation with:

```text
./gradlew spotlessCheck build :Common:apiJavadoc :Common:checkJarCompatibility validatePublishing
```

The Gradle projects are:

- `Common` — public API and loader-independent runtime;
- `Fabric`, `Forge`, and `NeoForge` — loader integrations and distributable
  mod jars.

Each loader project keeps its in-game integration fixture in a `testMod` source
set.

## License

MezzConfig is available under the [MIT License](LICENSE).
