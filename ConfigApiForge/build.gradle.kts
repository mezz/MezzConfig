import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact
import net.minecraftforge.gradle.common.tasks.JarExec

plugins {
	id("java")
	id("idea")
	id("eclipse")
	id("maven-publish")
	id("net.minecraftforge.gradle")
}

// gradle.properties
val forgeVersion: String by extra
val minecraftVersion: String by extra
val configApiModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra

group = configModGroup

val baseArchivesName = "${configApiModId}-${minecraftVersion}-forge"
base {
	archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":ConfigApi")
project.evaluationDependsOn(configApiProject.path)

sourceSets {
	named("test") {
		//The test module has no resources
		resources.setSrcDirs(emptyList<String>())
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
	}
	withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	javaToolchains {
		compilerFor {
			languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
		}
	}
}

tasks.named<JavaCompile>(sourceSets.main.get().compileJavaTaskName) {
	source(configApiProject.sourceSets.main.get().allSource)
}

repositories {
	maven("https://libraries.minecraft.net")
}

dependencies {
	"minecraft"(
		group = "net.minecraftforge",
		name = "forge",
		version = "${minecraftVersion}-${forgeVersion}"
	)
	implementation(configApiProject)
}

minecraft {
	mappings("official", minecraftVersion)
	reobf = false
	copyIdeResources.set(true)
}

tasks.jar {
	from(sourceSets.main.get().output)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourcesJarTask = tasks.named<Jar>("sourcesJar") {
	from(sourceSets.main.get().allJava)
	from(configApiProject.sourceSets.main.get().allJava)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveClassifier.set("sources")
}

tasks.assemble {
	dependsOn(sourcesJarTask)
}

publishing {
	publications {
		register<MavenPublication>("configApiForgeJar") {
			artifactId = baseArchivesName
			artifact(tasks.jar.get())
			artifact(sourcesJarTask.get())

			val dependencyInfo = mapOf(
				"groupId" to configApiProject.group,
				"artifactId" to configApiProject.base.archivesName.get(),
				"version" to configApiProject.version
			)

			pom.withXml {
				val dependenciesNode = asNode().appendNode("dependencies")
				val dependencyNode = dependenciesNode.appendNode("dependency")
				dependencyInfo.forEach { (key, value) ->
					dependencyNode.appendNode(key, value)
				}
			}
		}
	}
	repositories {
		val deployDir = project.findProperty("DEPLOY_DIR")
		if (deployDir != null) {
			maven(deployDir)
		}
	}
}

tasks.withType<DownloadMavenArtifact> {
	notCompatibleWithConfigurationCache("uses Task.project at execution time")
}

tasks.withType<JarExec> {
	notCompatibleWithConfigurationCache("uses external process at execution time")
}

idea {
	module {
		for (fileName in listOf("build", "run", "out", "logs")) {
			excludeDirs.add(file(fileName))
		}
	}
}
