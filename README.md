# MezzConfig

MezzConfig is a lightweight client-side configuration API and runtime for
Minecraft mods. It provides typed config schemas, file-backed values, migration
support, and loader-based plugin discovery on Fabric, Forge, and NeoForge.

## Using the API

The stable integration surface is published from the `CommonApi` module. See
the [API guide](CommonApi/README.md) for plugin registration, config schemas,
custom serializers, editor hints, and sorting configs.

MezzConfig currently targets Minecraft 1.21.1 and Java 21. Loader-specific
artifacts are built for Fabric, Forge, and NeoForge.

## Project layout

- `CommonApi` contains the public API.
- `Common` contains the loader-independent runtime.
- `Fabric`, `Forge`, and `NeoForge` contain loader integrations and produce the
  distributed mod jars.
- The corresponding `*Test` modules provide in-game integration fixtures.

## Building

Use the included Gradle wrapper with Java 21:

```text
./gradlew build
```

Build outputs are written beneath each module's `build/libs` directory.
