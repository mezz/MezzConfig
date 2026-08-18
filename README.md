# MezzConfig

MezzConfig is a lightweight configuration API and runtime for Minecraft mods.
It provides typed client-owned and server-authoritative config schemas,
file-backed values, synchronization, migration support, and direct config
registration.

## Using the API

The stable integration surface is published from the `CommonApi` module. See
the [API guide](CommonApi/README.md) for config schemas, ownership and scope,
custom serializers, editor hints, and sorting configs.

MezzConfig currently targets Minecraft 1.21.1 and Java 21. Loader-specific
artifacts are built for Fabric, Forge, and NeoForge.

Add the release repository to your Gradle build:

```kotlin
repositories {
	maven("https://maven.blamejared.com")
}
```

Then add the artifact for your loader, replacing `<version>` with the released
MezzConfig version:

```kotlin
// Fabric Loom
modImplementation("net.mezzdev.config:mezz_config-1.21.1-fabric:<version>")

// ForgeGradle
implementation(fg.deobf("net.mezzdev.config:mezz_config-1.21.1-forge:<version>"))

// NeoForge ModDevGradle
implementation("net.mezzdev.config:mezz_config-1.21.1-neoforge:<version>")
```

Choose one loader dependency; its Maven metadata brings in the public API and
common runtime. A shared compile-only module that deliberately must not depend
on a loader can instead use
`net.mezzdev.config:mezz_config-1.21.1-config-api:<version>`, while the final mod
still needs its loader-specific MezzConfig dependency at runtime.

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

Run the same release-blocking validation used by CI with:

```text
./gradlew spotlessCheck build :CommonApi:javadoc :CommonApi:checkJarCompatibility validatePublishing
```

Publication validation writes every Maven publication to
`build/publication-validation`. JarCompatibilityChecker compares CommonApi with
the latest released baseline. The baseline may be absent only while preparing
the initial release at the same version.

The NeoForge dedicated-server integration GameTest can be run directly with:

```text
./gradlew :NeoForge:runGameTestServer
```
