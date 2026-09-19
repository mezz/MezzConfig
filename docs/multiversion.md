# Building MezzConfig

Install JDK 21. Minecraft 1.19.2 and 1.20.1 also require JDK 17; 26.x requires
JDK 25. Run Gradle with `JAVA_HOME` pointing to JDK 21 for 1.x targets or JDK 25
for 26.x targets.

The default Minecraft target is 1.21.1. Select another with `-PminecraftVersion`:

```sh
./gradlew build
./gradlew -PminecraftVersion=26.3 build
```

Jars are written to `<module>/build/<minecraft>/libs/`. `build` runs the unit
tests; [CI](../.github/workflows/ci.yml) also checks loader runtimes and published
artifacts.

## IntelliJ

Import using the Gradle wrapper and the JDK for your target. To change targets,
set both properties in the project's `gradle.properties`:

```properties
minecraftVersion=1.20.1
org.gradle.projectcachedir=.gradle/targets/1.20.1
```

## Source layout

`Common` contains the shared API and core; `Minecraft` contains game adapters;
`Fabric`, `Forge`, and `NeoForge` contain loader integration. Version-specific
source folders adapt differences in Minecraft and loader APIs.

Files in `versions/` define each target's supported loaders, dependencies, and
Java version.
