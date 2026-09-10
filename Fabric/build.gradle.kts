import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask

plugins {
    java
    idea
    `maven-publish`
    id("fabric-loom")
    id("me.modmuss50.mod-publish-plugin")
}

publishMods {
    file.set(tasks.named<RemapJarTask>("remapJar").flatMap { it.archiveFile })
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
val fabricApiVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val configModGroup: String by extra
val fabricTestModId: String by extra
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

dependencyProjects.forEach {
    project.evaluationDependsOn(it.path)
}
val commonApiSourceSet = commonProject.sourceSets["api"]
val commonModShadeJarTask = commonProject.tasks.named<Jar>("modShadeJar")
val commonModShadeSourcesJarTask = commonProject.tasks.named<Jar>("modShadeSourcesJar")
val serverSmokeTestRunDir = layout.buildDirectory.dir("run/server-smoke")
val serverSmokeTestSuccessFile = serverSmokeTestRunDir.map { it.file("smoke-test-passed") }
fun zipTreeArchive(archiveTask: TaskProvider<Jar>) =
    zipTree(archiveTask.flatMap { it.archiveFile })

val testModSourceSet = sourceSets.create("testMod") {
    compileClasspath += sourceSets.main.get().output
    compileClasspath += sourceSets.main.get().compileClasspath
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

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchmentMinecraftVersion}:${parchmentVersionFabric}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
    dependencyProjects.forEach {
        compileOnly(it)
    }
    compileOnly(commonApiSourceSet.output)
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
        create("serverSmokeTest") {
            server()
            configName = "MezzConfig Fabric Server Smoke Test"
            runDir("build/run/server-smoke")
            programArgs("--nogui")
            vmArgs(
                "-Dfabric.classPathGroups=${classPathGroupsString}",
                "-Dfabric.log.level=info",
                "-DmezzConfig.loaderSmokeTest.successFile=${serverSmokeTestSuccessFile.get().asFile.absolutePath}"
            )
        }
    }
}

tasks.jar {
    dependsOn(commonModShadeJarTask)
    from(sourceSets.main.get().output)
    from(zipTreeArchive(commonModShadeJarTask))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonModShadeSourcesJarTask)
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

val testModClassesTask = tasks.named(testModSourceSet.classesTaskName)
val testModPath = layout.buildDirectory.dir("resources/${testModSourceSet.name}").get().asFile.absolutePath
val testModRunTasks = setOf("runClient", "runServer", "runServerSmokeTest")
tasks.matching { it.name in testModRunTasks }.configureEach {
    dependsOn(testModClassesTask)
    if (this is JavaExec) {
        classpath(testModSourceSet.output)
        jvmArgs("-Dfabric.addMods=$testModPath")
    }
}

tasks.check {
    dependsOn(testModClassesTask)
}

tasks.matching { it.name == "runServerSmokeTest" }.configureEach {
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
            throw GradleException("The Fabric loader smoke test did not report success.")
        }
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

            val dependencyInfos = dependencyProjects.map {
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
