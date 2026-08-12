import org.slf4j.event.Level

plugins {
    id("java")
    id("idea")
    id("eclipse")
    id("maven-publish")
    id("net.neoforged.moddev")
}

// gradle.properties
val neoforgeVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val configModGroup: String by extra
val neoforgeTestModId: String by extra
val modJavaVersion: String by extra
val deduplicatingRunnerVersion: String by extra
val fileWatcherVersion: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-neoforge"
base {
    archivesName.set(baseArchivesName)
}

val commonProject: Project = project(":Common")
val dependencyProjects: List<Project> = listOf(
    commonProject,
)
val configApiProject: Project = project(":CommonApi")
val testModProject: Project = project(":NeoForgeTest")

(listOf(configApiProject, testModProject) + dependencyProjects).forEach {
    project.evaluationDependsOn(it.path)
}
val testModSourceSet = testModProject.sourceSets.main.get()
val commonModShadeJarTask = commonProject.tasks.named<Jar>("modShadeJar")
val commonModShadeSourcesJarTask = commonProject.tasks.named<Jar>("modShadeSourcesJar")
fun zipTreeArchive(archiveTask: TaskProvider<Jar>) =
    zipTree(archiveTask.flatMap { it.archiveFile })

neoForge {
    version = neoforgeVersion

    mods {
        create(configModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(configApiProject.sourceSets.main.get())
            sourceSet(commonProject.sourceSets.main.get())
        }
        create(neoforgeTestModId) {
            sourceSet(testModSourceSet)
        }
    }

    runs {
        val configMod = mods.named(configModId)
        val testMod = mods.named(neoforgeTestModId)

        configureEach {
            getMods().set(setOf(configMod.get(), testMod.get()))
        }
        create("client") {
            client()
            gameDirectory = file("run/client/Dev")
            logLevel = Level.DEBUG
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
            programArguments.addAll("nogui")
            logLevel = Level.INFO
        }
    }
}

val testModClassesTask = testModProject.tasks.named(testModSourceSet.classesTaskName)
val testModRunTasks = setOf("runClient", "runServer")
tasks.matching { it.name in testModRunTasks }.configureEach {
    dependsOn(testModClassesTask)
}

sourceSets {
    named("test") {
        //The test module has no resources
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    compileOnly(configApiProject)
    dependencyProjects.forEach {
        compileOnly(it)
    }
    add("additionalRuntimeClasspath", "net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion") {
        isTransitive = false
    }
    add("additionalRuntimeClasspath", "net.mezzdev:filewatcher:$fileWatcherVersion") {
        isTransitive = false
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    javaToolchains {
        compilerFor {
            languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
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
        register<MavenPublication>("configNeoForgeJar") {
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
