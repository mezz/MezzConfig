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
val serverSmokeTestRunDir = layout.buildDirectory.dir("run/server-smoke")
val serverSmokeTestSuccessFile = serverSmokeTestRunDir.map { it.file("smoke-test-passed") }
val configModRunSourceSet = sourceSets.create("configModRun") {
	java.setSrcDirs(emptyList<String>())
	resources.setSrcDirs(emptyList<String>())
	val outputDir = layout.buildDirectory.file("sourcesSets/$name").get().asFile
	output.setResourcesDir(outputDir)
	java.destinationDirectory.set(outputDir)
}
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
	compileOnly(configApiProject)
	dependencyProjects.forEach {
		compileOnly(it)
	}
}

val prepareConfigModRun = tasks.register<Sync>("prepareConfigModRun") {
	from(sourceSets.main.get().output)
	from(configApiProject.sourceSets.main.get().output)
	from(zipTreeArchive(commonModShadeJarTask))
	into(configModRunSourceSet.java.destinationDirectory)
}

tasks.named(configModRunSourceSet.classesTaskName) {
	dependsOn(prepareConfigModRun)
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
					source(configModRunSourceSet)
				}
				create(forgeTestModId) {
					source(testModSourceSet)
				}
			}
		}
		val server = create("server") {
			taskName("Server")
			property("forge.logging.console.level", "debug")
			workingDirectory(file("run/server"))
			mods {
				create(configModId) {
					source(configModRunSourceSet)
				}
				create(forgeTestModId) {
					source(testModSourceSet)
				}
			}
		}
		create("serverSmokeTest") {
			parent(server)
			taskName("runServerSmokeTest")
			property("forge.logging.console.level", "info")
			property("com.mojang.eula.agree", "true")
			property("mezzConfig.loaderSmokeTest.successFile", serverSmokeTestSuccessFile.get().asFile.absolutePath)
			args("--nogui")
			workingDirectory(serverSmokeTestRunDir.get().asFile)
		}
	}
}

val testModClassesTask = testModProject.tasks.named(testModSourceSet.classesTaskName)
val testModRunTasks = setOf("runClientDev", "Server", "runServerSmokeTest")
tasks.matching { it.name in testModRunTasks }.configureEach {
	dependsOn(testModClassesTask)
}

tasks.matching { it.name == "runServerSmokeTest" }.configureEach {
	doNotTrackState("ForgeGradle run configurations are not serializable")
	outputs.file(serverSmokeTestSuccessFile)
	outputs.upToDateWhen { false }
	doFirst {
		val successFile = outputs.files.singleFile
		successFile.parentFile.mkdirs()
		successFile.resolveSibling("eula.txt").writeText("eula=true\n")
		successFile.resolveSibling("server.properties").writeText("online-mode=false\nserver-port=0\n")
		successFile.delete()
	}
	doLast {
		if (!outputs.files.singleFile.isFile) {
			throw GradleException("The Forge loader smoke test did not report success.")
		}
	}
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
