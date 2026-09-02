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

Its network features are optional. A client with MezzConfig can connect to a
vanilla server or any server that does not have MezzConfig installed.

The current branch targets Minecraft 1.21.1 and Java 21.

## Add MezzConfig to your mod

Add MezzConfig as a normal required mod dependency. This is preferred because
mods can share one MezzConfig installation and keep their own jars smaller.
Choose the guide for your loader:

- [Fabric Loom](docs/fabric.md)
- [ForgeGradle](docs/forge.md)
- [NeoForge ModDevGradle](docs/neoforge.md)

Each guide also explains how to include MezzConfig inside your mod as an
optional Jar-in-Jar dependency.

Use classes under `net.mezzdev.config.api`; other packages are internal.

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
