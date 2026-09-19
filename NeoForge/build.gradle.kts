import org.slf4j.event.Level

plugins {
    id("java")
    id("idea")
    id("eclipse")
    id("maven-publish")
    id("net.neoforged.moddev")
    id("me.modmuss50.mod-publish-plugin")
}

publishMods {
    file.set(tasks.jar.flatMap { it.archiveFile })
}

// gradle.properties
val neoforgeVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val jspecifyVersion: String by extra
val configModGroup: String by extra
val neoforgeTestModId: String by extra
val modJavaVersion: String by extra
val deduplicatingRunnerVersion: String by extra
val fileWatcherVersion: String by extra
val jsr305Version: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-neoforge"
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
fun zipTreeArchive(archiveTask: TaskProvider<Jar>) =
    zipTree(archiveTask.flatMap { it.archiveFile })
// Run the same complete, shaded runtime that is included in the published mod.
val configModRunSourceSet = sourceSets.create("configModRun") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    val directory = layout.buildDirectory.dir("mod-run").get().asFile
    output.setResourcesDir(directory)
    java.destinationDirectory.set(directory)
}
val prepareConfigModRun = tasks.register<Sync>("prepareConfigModRun") {
    from(sourceSets.main.get().output)
    from(zipTreeArchive(commonModShadeJarTask)) { exclude("META-INF/MANIFEST.MF") }
    into(configModRunSourceSet.java.destinationDirectory)
}
tasks.named(configModRunSourceSet.classesTaskName) { dependsOn(prepareConfigModRun) }

val gameTestJunitResultsDir = layout.buildDirectory.dir("test-results/gameTest")
val testModSourceSet = sourceSets.create("testMod") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val gameTestSourceSet = sourceSets.create("gameTest") {
    java.srcDir("src/${if (minecraftVersion == "1.21.1") "gameTestLegacy" else "gameTestModern"}/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations.named(testModSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
configurations.named(testModSourceSet.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}
configurations.named(gameTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}
configurations.named(gameTestSourceSet.compileOnlyConfigurationName) {
    extendsFrom(configurations.compileOnly.get())
}

neoForge {
    version = neoforgeVersion

    addModdingDependenciesTo(testModSourceSet)
    addModdingDependenciesTo(gameTestSourceSet)

    mods {
        create(configModId) {
            sourceSet(configModRunSourceSet)
        }
        create(neoforgeTestModId) {
            sourceSet(testModSourceSet)
        }
        create("mezzConfigGameTests") {
            sourceSet(gameTestSourceSet)
        }
    }

    runs {
        val configMod = mods.named(configModId)
        val testMod = mods.named(neoforgeTestModId)
        val gameTestMod = mods.named("mezzConfigGameTests")

        configureEach {
            getLoadedMods().set(setOf(configMod.get(), testMod.get()))
        }
        create("client") {
            client()
            gameDirectory = file("run/$minecraftVersion/client/Dev")
            logLevel = Level.DEBUG
        }
        create("server") {
            server()
            gameDirectory = file("run/$minecraftVersion/server")
            programArguments.addAll("nogui")
            logLevel = Level.INFO
        }
        create("gameTestServer") {
            type.set("gameTestServer")
            gameDirectory = file("run/$minecraftVersion/gameTestServer")
            sourceSet = gameTestSourceSet
            getLoadedMods().add(gameTestMod.get())
            systemProperty("mezzConfig.gameTest.junitDir", gameTestJunitResultsDir.get().asFile.absolutePath)
            logLevel = Level.INFO
        }
    }
}

val testModClassesTask = tasks.named(testModSourceSet.classesTaskName)
val testModRunTasks = setOf("runClient", "runServer", "runGameTestServer")
tasks.matching { it.name in testModRunTasks }.configureEach {
    dependsOn(testModClassesTask, prepareConfigModRun)
}

tasks.check {
    dependsOn(testModClassesTask)
}

sourceSets {
    named("test") {
        //The test module has no resources
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    dependencyProjects.forEach {
        compileOnly(it)
    }
    compileOnly(commonApiSourceSet.output)
    add(testModSourceSet.compileOnlyConfigurationName, "com.google.code.findbugs:jsr305:$jsr305Version")
    add(gameTestSourceSet.implementationConfigurationName, "net.neoforged:testframework:$neoforgeVersion") {
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
    from(sourceSets.main.get().output)
    from(zipTreeArchive(commonModShadeJarTask))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourcesJarTask = tasks.named<Jar>("sourcesJar") {
    dependsOn(commonModShadeSourcesJarTask)
    from(sourceSets.main.get().allJava)
    from(zipTreeArchive(commonModShadeSourcesJarTask)) {
        exclude("META-INF/MANIFEST.MF", "MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
}

tasks.assemble {
    dependsOn(sourcesJarTask)
}

val cleanGameTestJunitResults = tasks.register<Delete>("cleanGameTestJunitResults") {
    description = "Deletes NeoForge game test JUnit result files before running game tests."
    delete(gameTestJunitResultsDir)
}

tasks.named("runGameTestServer") {
    dependsOn(cleanGameTestJunitResults)
}

publishing {
    publications {
        register<MavenPublication>("configNeoForgeJar") {
            artifactId = baseArchivesName
            artifact(tasks.jar)
            artifact(sourcesJarTask)
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
    java.srcDir("src/${if (minecraftVersion == "1.21.1") "loader4" else "loader10"}/java")
    java.srcDir(rootProject.file("Minecraft/src/main/java"))
    java.srcDir(rootProject.file("Minecraft/src/payload/java"))
    java.srcDir(rootProject.file("Minecraft/src/${if (minecraftVersion == "1.21.1") "resource" else "identifier"}/java"))
}
dependencies {
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")
}
