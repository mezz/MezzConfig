# MezzConfig

MezzConfig is a typed configuration library for Minecraft mods. It gives mods
one API for client preferences, per-world client settings, and
server-authoritative settings synchronized to connected clients.

MezzConfig focuses on the parts of configuration that are difficult to get
right across loaders:

- typed values with validation, defaults, and automatic file recovery
- Fabric, Forge, and NeoForge support through the same public API
- server-owned settings with one-way synchronization to clients
- atomic updates, change listeners, restart requirements, and migration tools
- persistent user-defined sorting for values discovered at runtime

Its network features are optional. A client with MezzConfig can connect to a
vanilla server or any server that does not have MezzConfig installed.

## Add MezzConfig to your mod

Choose the guide for your loader:

- [Fabric Loom](docs/fabric.md)
- [ForgeGradle](docs/forge.md)
- NeoForge with [ModDevGradle](docs/neoforge.md) or
  [NeoGradle](docs/neogradle.md)

Each guide also explains how to include MezzConfig inside your mod as an
optional Jar-in-Jar dependency.

Use classes under `net.mezzdev.config.api`, other packages are internal.

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

Run the automated release checks used by CI with:

```text
./gradlew spotlessCheck build :Common:apiJavadoc :Common:checkJarCompatibility validateDocumentationLinks validateFabricEmbedding
./gradlew --no-configuration-cache validateNeoForgeEmbedding
./gradlew :NeoForge:runGameTestServer
./gradlew --no-configuration-cache :Fabric:runServerSmokeTest :Forge:runServerSmokeTest
```

The embedding checks also run `validatePublishing`, which writes artifacts only
to `build/publication-validation`. The NeoForge check builds a separate consumer
using the documented dependencies and inspects its nested jars and metadata.

Before release, also check client joins, disconnects, reconnects, and world
switches on each loader. Confirm that per-world listeners observe context
changes and server settings synchronize correctly.

## License

MezzConfig is available under the [MIT License](LICENSE).
