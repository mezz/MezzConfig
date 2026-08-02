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
val configModId: String by extra
val configModGroup: String by extra
val forgeTestModId: String by extra
val modJavaVersion: String by extra
val jetbrainsAnnotationsVersion: String by extra
val log4jVersion: String by extra
val jsr305Version: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-forge"
base {
	archivesName.set(baseArchivesName)
}

val commonProject: Project = project(":Common")
val dependencyProjects: List<Project> = listOf(
	commonProject,
)
val configApiProject: Project = project(":CommonApi")
val testModProject: Project = project(":ForgeTest")

(listOf(configApiProject, testModProject) + dependencyProjects).forEach {
	project.evaluationDependsOn(it.path)
}
val testModSourceSet = testModProject.sourceSets.main.get()
val commonModShadeJarTask = commonProject.tasks.named<Jar>("modShadeJar")
val commonModShadeSourcesJarTask = commonProject.tasks.named<Jar>("modShadeSourcesJar")
fun zipTreeArchive(archiveTask: TaskProvider<Jar>) =
	zipTree(archiveTask.flatMap { it.archiveFile })

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

// Hack fix: FG can't resolve deps like lwjgl-freetype-3.3.3-natives-macos-patch.jar without this
repositories {
	maven("https://libraries.minecraft.net")
}

dependencies {
	"minecraft"(
		group = "net.minecraftforge",
		name = "forge",
		version = "${minecraftVersion}-${forgeVersion}"
	)
	compileOnly("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
	compileOnly("org.apache.logging.log4j:log4j-api:$log4jVersion")
	compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
	runtimeOnly(project(commonProject.path)) {
		attributes {
			attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
		}
	}
	compileOnly(configApiProject)
	dependencyProjects.forEach {
		compileOnly(it)
	}
}

minecraft {
	mappings("official", minecraftVersion)

	// use Official mappings at runtime
	reobf = false

	copyIdeResources.set(true)

	runs {
		create("client") {
			taskName("runClientDev")
			property("forge.logging.console.level", "debug")
			workingDirectory(file("run/client/Dev"))
			mods {
				create(configModId) {
					source(sourceSets.main.get())
					source(configApiProject.sourceSets.main.get())
				}
				create(forgeTestModId) {
					source(testModSourceSet)
				}
			}
		}
		create("server") {
			taskName("Server")
			property("forge.logging.console.level", "debug")
			workingDirectory(file("run/server"))
			mods {
				create(configModId) {
					source(sourceSets.main.get())
					source(configApiProject.sourceSets.main.get())
				}
				create(forgeTestModId) {
					source(testModSourceSet)
				}
			}
		}
	}
}

val testModClassesTask = testModProject.tasks.named(testModSourceSet.classesTaskName)
val testModRunTasks = setOf("runClientDev", "Server")
tasks.matching { it.name in testModRunTasks }.configureEach {
	dependsOn(testModClassesTask)
}

tasks.jar {
	dependsOn(commonModShadeJarTask)
	from(configApiProject.sourceSets.main.get().output)
	from(sourceSets.main.get().output)
	from(zipTreeArchive(commonModShadeJarTask))
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourcesJarTask = tasks.named<Jar>("sourcesJar") {
	dependsOn(commonModShadeSourcesJarTask)
	from(configApiProject.sourceSets.main.get().allJava)
	from(sourceSets.main.get().allJava)
	from(zipTreeArchive(commonModShadeSourcesJarTask)) {
		exclude("META-INF/MANIFEST.MF", "MANIFEST.MF")
	}
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveClassifier.set("sources")
}

val mavenJarTask = tasks.register<Jar>("mavenJar") {
	from(sourceSets.main.get().output)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	destinationDirectory.set(layout.buildDirectory.dir("maven-libs"))
}

val mavenSourcesJarTask = tasks.register<Jar>("mavenSourcesJar") {
	from(sourceSets.main.get().allJava)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveClassifier.set("sources")
	destinationDirectory.set(layout.buildDirectory.dir("maven-libs"))
}

tasks.assemble {
	dependsOn(sourcesJarTask)
}

publishing {
	publications {
		register<MavenPublication>("configForgeJar") {
			artifactId = baseArchivesName
			artifact(mavenJarTask.get())
			artifact(mavenSourcesJarTask.get())

			val dependencyInfos = (listOf(configApiProject) + dependencyProjects).map {
				mapOf(
					"groupId" to it.group,
					"artifactId" to it.base.archivesName.get(),
					"version" to it.version
				)
			}

			pom.withXml {
				val dependenciesNode = asNode().appendNode("dependencies")
				dependencyInfos.forEach {
					val dependencyNode = dependenciesNode.appendNode("dependency")
					it.forEach { (key, value) ->
						dependencyNode.appendNode(key, value)
					}
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

idea {
	module {
		for (fileName in listOf("build", "run", "out", "logs")) {
			excludeDirs.add(file(fileName))
		}
	}
}

// Required because FG, copied from the MDK
sourceSets.forEach {
	val outputDir = layout.buildDirectory.file("sourcesSets/${it.name}").get().asFile
	it.output.setResourcesDir(outputDir)
	it.java.destinationDirectory.set(outputDir)
}

tasks.withType<DownloadMavenArtifact> {
	notCompatibleWithConfigurationCache("uses Task.project at execution time")
}

tasks.withType<JarExec> {
	notCompatibleWithConfigurationCache("uses external process at execution time")
}
