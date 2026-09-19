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
- [Forge with Legacy ModDevGradle](docs/forge.md)
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

## Supported Minecraft versions

Choose the MezzConfig artifacts that match your mod's Minecraft version and loader.
Each runtime jar supports its exact Minecraft version.

| Minecraft | Java | Loaders |
| --- | --- | --- |
| 1.19.2, 1.20.1 | 17 | Fabric, Forge |
| 1.21.1 | 21 | Fabric, NeoForge |
| 1.21.11 | 21 | Fabric, NeoForge |
| 26.1.2, 26.2, 26.3 | 25 | Fabric, NeoForge |

## Contributing

Contributors must sign the [Contributor License Agreement](CLA.md) before their
pull requests can be merged. Sign through
[CLA Assistant](https://cla-assistant.io/mezz/MezzConfig) using the GitHub account
associated with your contributions.

See [Building MezzConfig](docs/multiversion.md) for build commands, JDK requirements,
and IDE setup.

## License

MezzConfig is available under the [MIT License](LICENSE).
