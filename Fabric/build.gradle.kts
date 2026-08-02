import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask

plugins {
    java
    idea
    `maven-publish`
    id("fabric-loom")
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
}

// gradle.properties
val fabricLoaderVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val parchmentMinecraftVersion: String by extra
val parchmentVersionFabric: String by extra
val jsr305Version: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-fabric"
base {
    archivesName.set(baseArchivesName)
}

val commonProject: Project = project(":Common")
val dependencyProjects: List<Project> = listOf(
    commonProject,
)
val configApiProject: Project = project(":CommonApi")
val testModProject: Project = project(":FabricTest")

(listOf(configApiProject, testModProject) + dependencyProjects).forEach {
    project.evaluationDependsOn(it.path)
}
val testModSourceSet = testModProject.sourceSets.main.get()
val commonModShadeJarTask = commonProject.tasks.named<Jar>("modShadeJar")
val commonModShadeSourcesJarTask = commonProject.tasks.named<Jar>("modShadeSourcesJar")
fun zipTreeArchive(archiveTask: TaskProvider<Jar>) =
    zipTree(archiveTask.flatMap { it.archiveFile })

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

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchmentMinecraftVersion}:${parchmentVersionFabric}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
    compileOnly(configApiProject)
    dependencyProjects.forEach {
        compileOnly(it)
    }
    runtimeOnly(project(commonProject.path)) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
        }
    }
}

loom {
    mods {
        create(configModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(configApiProject.sourceSets.main.get())
        }
    }
    runs {
        val dependencyJarPaths = listOf(commonModShadeJarTask.get().archiveFile.get().asFile)
        val classPaths = sourceSets.main.get().output.classesDirs
        val resourcesPaths = listOfNotNull(
            sourceSets.main.get().output.resourcesDir
        )
        val classPathGroups = listOf(dependencyJarPaths, classPaths, resourcesPaths).flatten()
        val classPathGroupsString = classPathGroups
            .filterNotNull()
            .joinToString(separator = File.pathSeparator) {
                it.absoluteFile.toString()
            }

        val loomRunDir = File("run")

        named("client") {
            client()
            configName = "MezzConfig Fabric Client"
            ideConfigGenerated(true)
            runDir(loomRunDir.resolve("client").toString())
            vmArgs(
                "-Dfabric.classPathGroups=${classPathGroupsString}",
                "-Dfabric.log.level=info"
            )
        }
        named("server") {
            server()
            configName = "MezzConfig Fabric Server"
            ideConfigGenerated(true)
            runDir(loomRunDir.resolve("server").toString())
            vmArgs(
                "-Dfabric.classPathGroups=${classPathGroupsString}",
                "-Dfabric.log.level=info"
            )
        }
    }
}

sourceSets {
    named("main") {
        runtimeClasspath += configApiProject.sourceSets.main.get().output
    }
}

tasks.jar {
    dependsOn(commonModShadeJarTask)
    from(configApiProject.sourceSets.main.get().output)
    from(sourceSets.main.get().output)
    from(zipTreeArchive(commonModShadeJarTask))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("sourcesJar") {
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
    destinationDirectory.set(layout.buildDirectory.dir("maven-intermediates"))
}

val remapMavenJarTask = tasks.register<RemapJarTask>("remapMavenJar") {
    inputFile.set(mavenJarTask.flatMap { it.archiveFile })
    addNestedDependencies.set(false)
    archiveBaseName.set(baseArchivesName)
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("maven-libs"))
}

val mavenSourcesJarTask = tasks.register<Jar>("mavenSourcesJar") {
    from(sourceSets.main.get().allJava)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("maven-intermediates"))
}

val remapMavenSourcesJarTask = tasks.register<RemapSourcesJarTask>("remapMavenSourcesJar") {
    inputFile.set(mavenSourcesJarTask.flatMap { it.archiveFile })
    archiveBaseName.set(baseArchivesName)
    archiveClassifier.set("sources")
    destinationDirectory.set(layout.buildDirectory.dir("maven-libs"))
}

tasks.assemble {
    dependsOn(tasks.remapJar, tasks.remapSourcesJar)
}

val testModClassesTask = testModProject.tasks.named(testModSourceSet.classesTaskName)
val testModPath = testModProject.layout.buildDirectory.dir("resources/main").get().asFile.absolutePath
val testModRunTasks = setOf("runClient", "runServer")
tasks.matching { it.name in testModRunTasks }.configureEach {
    dependsOn(testModClassesTask)
    if (this is JavaExec) {
        classpath(testModSourceSet.output)
        jvmArgs("-Dfabric.addMods=$testModPath")
    }
}

publishing {
    publications {
        register<MavenPublication>("configFabricJar") {
            @Suppress("UnstableApiUsage")
            loom.disableDeprecatedPomGeneration(this)
            artifactId = baseArchivesName
            artifact(remapMavenJarTask)
            artifact(remapMavenSourcesJarTask)

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
