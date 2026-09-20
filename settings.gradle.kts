pluginManagement {
    val target = providers.gradleProperty("minecraftVersion").orElse("1.21.1").get()
    val targetCache = file(".gradle/targets/$target").canonicalFile
    require(gradle.startParameter.projectCacheDir?.canonicalFile == targetCache) {
        "Use the Gradle wrapper, or configure --project-cache-dir $targetCache when importing Minecraft $target."
    }
    val pins = java.util.Properties().apply {
        val manifest = file("versions/$target.properties")
        require(manifest.isFile) { "Unsupported Minecraft target '$target'. See versions/." }
        manifest.inputStream().use { load(it) }
    }
    require(gradle.gradleVersion.substringBefore('.') == "9") {
        "Use ./gradlew to build with the project's Gradle 9 distribution."
    }
    val buildJava = if (target.startsWith("26.")) 25 else 21
    require(JavaVersion.current().isCompatibleWith(JavaVersion.toVersion(buildJava))) {
        "Minecraft $target requires Java $buildJava to run Gradle. Set JAVA_HOME or the IDE's Gradle JVM."
    }
    plugins {
        id("net.fabricmc.fabric-loom") version pins.getProperty("loomVersion")
        id("net.neoforged.moddev") version providers.gradleProperty("moddevVersion").get()
        id("net.neoforged.moddev.legacyforge") version providers.gradleProperty("moddevVersion").get()
        id("net.minecraftforge.gradle") version providers.gradleProperty("forgeGradleVersion").get()
        id("me.modmuss50.mod-publish-plugin") version providers.gradleProperty("publishPluginVersion").get()
    }

	repositories {
		fun exclusiveMaven(url: String, filter: Action<InclusiveRepositoryContentDescriptor>) =
			exclusiveContent {
				forRepository { maven(url) }
				filter(filter)
			}
		exclusiveMaven("https://maven.parchmentmc.org") {
			includeGroupByRegex("org\\.parchmentmc.*")
		}
		exclusiveMaven("https://maven.fabricmc.net/") {
			includeGroupByRegex("net\\.fabricmc.*")
		}
		exclusiveMaven("https://maven.neoforged.net/releases") {
			includeGroupByRegex("net\\.neoforged.*")
			includeGroup("codechicken")
			includeGroup("net.covers1624")
		}
		maven("https://maven.minecraftforge.net") {
			content { includeGroupByRegex("net\\.minecraftforge.*") }
		}
		maven("https://repo.spongepowered.org/repository/maven-public/") {
			content {
				includeGroupByRegex("org\\.spongepowered.*")
				includeGroupByRegex("net\\.minecraftforge.*")
			}
		}
		exclusiveMaven("https://maven.blamejared.com/") {
			includeGroup("net.mezzdev.java-formatting")
			includeModule("net.mezzdev.gradle", "JavaFormatting")
		}
		gradlePluginPortal()
	}
}

rootProject.name = "MezzConfig"
val target = providers.gradleProperty("minecraftVersion").orElse("1.21.1").get()
val pins = java.util.Properties().apply {
    file("versions/$target.properties").inputStream().use { load(it) }
}
val loaders = pins.getProperty("loaders").split(",")
include("Changelog", "Common")
loaders.forEach { include(it) }
if ("Forge" in loaders && pins.getProperty("forgeTool") == "forgegradle") {
    project(":Forge").buildFileName = "forgegradle.gradle.kts"
}
gradle.beforeProject {
    pins.forEach { key, value -> extensions.extraProperties.set(key.toString(), value) }
    layout.buildDirectory.set(layout.projectDirectory.dir("build/$target"))
}
