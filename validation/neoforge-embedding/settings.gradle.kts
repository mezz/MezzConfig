pluginManagement {
    val buildPlugins = java.util.Properties().apply {
        file("../../gradle.properties").inputStream().use { load(it) }
    }
    plugins {
        id("net.neoforged.moddev") version buildPlugins.getProperty("moddevVersion")
    }
    repositories {
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
    }
}

rootProject.name = "mezzconfig-neoforge-embedding-test"
