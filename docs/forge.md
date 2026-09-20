# Forge setup

MezzConfig supports ForgeGradle 7 on Minecraft 1.21.1 and Legacy
ModDevGradle on Minecraft 1.19.2 and 1.20.1. Use your Minecraft version in
the artifact names below.

Choose the MezzConfig version and add its Maven repository:

```kotlin
val mezzConfigVersion = "<version>"

repositories {
	maven("https://maven.blamejared.com")
}
```

## ForgeGradle 7 (Minecraft 1.21.1)

After applying `net.minecraftforge.gradle`, use ForgeGradle's Mavenizer for the
Forge dependency and pin it to the official Minecraft mappings. The separate
runtime configuration keeps MezzConfig out of your published dependency
metadata while making it available to development game runs:

```kotlin
val minecraftVersion = "1.21.1"
val forgeVersion = "52.1.16"

minecraft {
	mappings("official", minecraftVersion)
}

val mezzConfigRuntime by configurations.creating {
	isCanBeConsumed = false
	isCanBeResolved = false
}
configurations.runtimeClasspath {
	extendsFrom(mezzConfigRuntime)
}

minecraft.mavenizer(repositories)
repositories {
	maven(fg.forgeMaven)
	maven(fg.minecraftLibsMaven)
}

dependencies {
	val forgeDependency = create(
		"net.minecraftforge:forge:$minecraftVersion-$forgeVersion"
	) as ExternalModuleDependency
	forgeDependency.attributes {
		attribute(Attribute.of("net.minecraftforge.mappings.channel", String::class.java), "official")
		attribute(Attribute.of("net.minecraftforge.mappings.version", String::class.java), minecraftVersion)
	}
	implementation(minecraft.dependency(forgeDependency))

	compileOnly("net.mezzdev.config:mezz_config-1.21.1-config-api:$mezzConfigVersion")
	mezzConfigRuntime("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersion")
}
```

## Legacy ModDevGradle (Minecraft 1.19.2 and 1.20.1)

After configuring `legacyForge`, create a remapped development dependency. This
example targets Minecraft 1.20.1:

```kotlin
val mezzConfigRuntime = obfuscation.createRemappingConfiguration(
	configurations.getByName("additionalRuntimeClasspath")
)

dependencies {
	compileOnly("net.mezzdev.config:mezz_config-1.20.1-config-api:$mezzConfigVersion")
	add(mezzConfigRuntime.name, "net.mezzdev.config:mezz_config-1.20.1-forge:$mezzConfigVersion")
}
```

The API dependency lets your code use MezzConfig. The runtime dependency makes
the complete Forge mod available to development game runs.

## Tell Forge about MezzConfig

Keep MezzConfig as a required dependency in `META-INF/mods.toml`. Replace
`your_mod_id` with your mod id:

```toml
[[dependencies.your_mod_id]]
    modId="mezz_config"
    mandatory=true
    versionRange="[0.5.0,1.0.0)"
    ordering="AFTER"
    side="BOTH"
```

This tells Forge to require MezzConfig and start it before your mod. Change the
version range only when your mod supports a different range.

## Optional: include MezzConfig in your jar

You can include MezzConfig when your mod must work as a single download. Keep
the embedded version range the same as the one in `mods.toml`.

For ForgeGradle 7, apply `net.minecraftforge.jarjar`, register its task, and add
the complete Forge artifact:

```kotlin
plugins {
	id("net.minecraftforge.jarjar")
}

val mezzConfigVersionRange = "[0.5.0,1.0.0)"
jarJar.register()

dependencies {
	"jarJar"("net.mezzdev.config:mezz_config-1.21.1-forge:$mezzConfigVersion") {
		isTransitive = false
		jarJar.configure(this) {
			setRange(mezzConfigVersionRange)
			setVersion(mezzConfigVersion)
		}
	}
}
```

For Legacy ModDevGradle, use its `jarJar` configuration:

```kotlin
val mezzConfigVersionRange = "[0.5.0,1.0.0)"

dependencies {
	jarJar("net.mezzdev.config:mezz_config-1.20.1-forge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}
```

Run `./gradlew build` and distribute the release jar produced by your Forge
tooling. The included copy satisfies the required dependency; keep the
`mods.toml` entry.
