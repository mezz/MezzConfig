pluginManagement {
	repositories {
		fun exclusiveMaven(url: String, filter: Action<InclusiveRepositoryContentDescriptor>) =
			exclusiveContent {
				forRepository { maven(url) }
				filter(filter)
			}
		maven("https://maven.minecraftforge.net") {
			content {
				includeGroupByRegex("net\\.minecraftforge.*")
			}
		}
		exclusiveMaven("https://maven.parchmentmc.org") {
			includeGroupByRegex("org\\.parchmentmc.*")
		}
		exclusiveMaven("https://maven.fabricmc.net/") {
			includeGroupByRegex("net\\.fabricmc.*")
			includeGroup("fabric-loom")
		}
		exclusiveMaven("https://maven.neoforged.net/releases") {
			includeGroupByRegex("net\\.neoforged.*")
			includeGroup("codechicken")
			includeGroup("net.covers1624")
		}
		exclusiveMaven("https://maven.blamejared.com/") {
			includeGroup("net.mezzdev.java-formatting")
			includeModule("net.mezzdev.gradle", "JavaFormatting")
		}
		gradlePluginPortal()
	}
	resolutionStrategy {
		eachPlugin {
			if (requested.id.id == "net.minecraftforge.gradle") {
				useModule("${requested.id}:ForgeGradle:${requested.version}")
			}
		}
	}
}

rootProject.name = "MezzConfig"
include(
	"Changelog",
	"Common",
	"Fabric", "Forge", "NeoForge"
)
