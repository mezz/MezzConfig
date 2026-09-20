import org.slf4j.event.Level

plugins {
    java
    idea
    `maven-publish`
    id("net.neoforged.moddev.legacyforge")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraftVersion: String by extra
val forgeVersion: String by extra
val modJavaVersion: String by extra
val configModId: String by extra
val jspecifyVersion: String by extra
val configModGroup: String by extra
val forgeTestModId: String by extra
val deduplicatingRunnerVersion: String by extra
val fileWatcherVersion: String by extra
group = configModGroup
val artifactName = "${configModId}-${minecraftVersion}-forge"
base.archivesName.set(artifactName)

val common = project(":Common")
evaluationDependsOn(common.path)
val commonSources = common.extensions.getByType<SourceSetContainer>()
val commonJar = common.tasks.named<Jar>("modShadeJar")
val commonSourcesJar = common.tasks.named<Jar>("modShadeSourcesJar")
val testMod = sourceSets.create("testMod") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations.named(testMod.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(testMod.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }

sourceSets.main {
    java.srcDir("src/packet-buffer/java")
    java.srcDir(rootProject.file("Minecraft/src/main/java"))
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    withSourcesJar()
}
dependencies {
    compileOnly(common)
    compileOnly(commonSources["api"].output)
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")
    compileOnly("org.jetbrains:annotations:26.1.0")
}

val smokeDirectory = layout.buildDirectory.dir("run/server-smoke")
val smokeResult = smokeDirectory.map { it.file("smoke-test-passed") }
val serverSmokeTestProperties = """
    generate-structures=false
    generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
    level-type=minecraft:flat
    online-mode=false
    server-port=0
    simulation-distance=2
    spawn-protection=0
    view-distance=2
""".trimIndent() + "\n"
val forgeArtifactVersion = "$minecraftVersion-$forgeVersion"
legacyForge {
    enable {
        setForgeVersion(forgeArtifactVersion)
        setEnabledSourceSets(setOf(sourceSets.main.get(), testMod))
        setDisableRecompilation(false)
    }
    mods {
        create(configModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(commonSources["main"])
            sourceSet(commonSources["api"])
        }
        create(forgeTestModId) { sourceSet(testMod) }
    }
    runs {
        create("client") {
            client()
            gameDirectory = file("run/$minecraftVersion/client")
        }
        create("server") {
            server()
            gameDirectory = file("run/$minecraftVersion/server")
            programArguments.add("nogui")
        }
        create("serverSmokeTest") {
            server()
            gameDirectory = smokeDirectory.get().asFile
            programArguments.add("nogui")
            systemProperty("com.mojang.eula.agree", "true")
            systemProperty("mezzConfig.loaderSmokeTest.successFile", smokeResult.get().asFile.absolutePath)
            logLevel = Level.INFO
        }
    }
}
tasks.jar {
    from(commonJar.map { zipTree(it.archiveFile) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.named<Jar>("sourcesJar") {
    from(commonSourcesJar.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val reobfJar = tasks.named<AbstractArchiveTask>("reobfJar")
publishMods { file.set(reobfJar.flatMap { it.archiveFile }) }
publishing {
    publications {
        register<MavenPublication>("configForgeJar") {
            artifactId = artifactName
            artifact(reobfJar)
            artifact(tasks.named("sourcesJar"))
        }
    }
}
tasks.check { dependsOn(tasks.named(testMod.classesTaskName)) }
tasks.matching { it.name in setOf("runClient", "runServer", "runServerSmokeTest") }.configureEach {
    dependsOn(tasks.named(testMod.classesTaskName))
}
tasks.named("runServerSmokeTest") {
    outputs.file(smokeResult)
    outputs.upToDateWhen { false }
    doFirst {
        val result = outputs.files.singleFile
        result.parentFile.mkdirs()
        result.resolveSibling("eula.txt").writeText("eula=true\n")
        result.resolveSibling("server.properties").writeText(serverSmokeTestProperties)
        result.delete()
    }
    doLast {
        check(outputs.files.singleFile.isFile) { "The Forge loader smoke test did not report success." }
    }
}

dependencies {
    add("additionalRuntimeClasspath", "net.mezzdev:filewatcher:$fileWatcherVersion")
    add("additionalRuntimeClasspath", "net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion")
}
