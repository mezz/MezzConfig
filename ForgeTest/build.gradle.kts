import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact
import net.minecraftforge.gradle.common.tasks.JarExec

plugins {
	id("java")
	id("idea")
	id("eclipse")
	id("net.minecraftforge.gradle")
}

// gradle.properties
val forgeVersion: String by extra
val minecraftVersion: String by extra
val forgeTestModId: String by extra
val modJavaVersion: String by extra
val jsr305Version: String by extra

val baseArchivesName = "${forgeTestModId}-${minecraftVersion}"
base {
	archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":CommonApi")
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
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	javaToolchains {
		compilerFor {
			languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
		}
	}
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
	compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
	compileOnly(configApiProject)
}

minecraft {
	mappings("official", minecraftVersion)
	reobf = false
	copyIdeResources.set(true)
}

tasks.withType<AbstractArchiveTask>().configureEach {
	enabled = false
}

tasks.withType<Test>().configureEach {
	enabled = false
	classpath = files()
	testClassesDirs = files()
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
