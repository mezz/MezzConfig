import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
    idea
    `maven-publish`
    id("net.fabricmc.fabric-loom") apply false
    id("me.modmuss50.mod-publish-plugin")
}

// gradle.properties
val fabricLoaderVersion: String by extra
val fabricApiVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val jspecifyVersion: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val jsr305Version: String by extra

val unobfuscatedMinecraft = minecraftVersion.startsWith("26.")
val packetBufferNetworking = minecraftVersion == "1.19.2" || minecraftVersion == "1.20.1"
pluginManager.apply(if (unobfuscatedMinecraft) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap")

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

val modDependencyConfiguration = if (unobfuscatedMinecraft) "implementation" else "modImplementation"
dependencies {
    add(modDependencyConfiguration, "net.fabricmc:fabric-loader:$fabricLoaderVersion")
    add(modDependencyConfiguration, "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")
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

val loom = extensions.getByType<LoomGradleExtensionAPI>()
if (!unobfuscatedMinecraft) {
    val parchmentMinecraftVersion: String by extra
    val parchmentVersionFabric: String by extra
    repositories {
        exclusiveContent {
            forRepository { maven("https://maven.parchmentmc.org") }
            filter { includeGroupByRegex("org\\.parchmentmc.*") }
        }
    }
    dependencies {
        add("mappings", loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-${parchmentMinecraftVersion}:${parchmentVersionFabric}@zip")
        })
    }
}

val runtimeJar = tasks.named<AbstractArchiveTask>(if (unobfuscatedMinecraft) "jar" else "remapJar")
val publishedSourcesJar = tasks.named<AbstractArchiveTask>(if (unobfuscatedMinecraft) "sourcesJar" else "remapSourcesJar")
tasks.assemble { dependsOn(runtimeJar, publishedSourcesJar) }

publishMods {
    file.set(runtimeJar.flatMap { it.archiveFile })
}
publishing {
    publications {
        register<MavenPublication>("configFabricJar") {
            if (!unobfuscatedMinecraft) {
                @Suppress("UnstableApiUsage")
                loom.disableDeprecatedPomGeneration(this)
            }
            artifactId = baseArchivesName
            artifact(runtimeJar)
            artifact(publishedSourcesJar)
        }
    }
}

configure<LoomGradleExtensionAPI> {
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

        val loomRunDir = File("run/$minecraftVersion")

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
            runDir(serverSmokeTestRunDir.get().asFile.relativeTo(projectDir).path)
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

idea {
    module {
        for (fileName in listOf("build", "run", "out", "logs")) {
            excludeDirs.add(file(fileName))
        }
    }
}

sourceSets.main {
    java.srcDir(rootProject.file("Minecraft/src/main/java"))
    if (packetBufferNetworking) {
        java.srcDir("src/packet-buffer/java")
    } else {
        java.srcDir("src/payload/java")
        java.srcDir("src/${if (unobfuscatedMinecraft) "payload-clientbound" else "payload-s2c"}/java")
        java.srcDir(rootProject.file("Minecraft/src/payload/java"))
        java.srcDir(rootProject.file("Minecraft/src/${if (minecraftVersion == "1.21.1") "resource" else "identifier"}/java"))
    }
}
