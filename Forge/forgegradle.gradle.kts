plugins {
    java
    idea
    `maven-publish`
    id("net.minecraftforge.gradle")
    id("me.modmuss50.mod-publish-plugin")
    id("net.mezzdev.modshade")
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
val testMod = sourceSets.create("testMod") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
val smokeTest = sourceSets.create("smokeTest") {
    java.setSrcDirs(emptyList<String>())
    resources.setSrcDirs(emptyList<String>())
    runtimeClasspath = sourceSets.main.get().runtimeClasspath
}
configurations.named(testMod.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(testMod.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
configurations.named(smokeTest.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }

sourceSets.main {
    java.srcDir("src/payload/java")
    java.srcDir(rootProject.file("Minecraft/src/main/java"))
    java.srcDir(rootProject.file("Minecraft/src/payload/java"))
    java.srcDir(rootProject.file("Minecraft/src/resource/java"))
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    withSourcesJar()
}

minecraft.mavenizer(repositories)
repositories {
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}
dependencies {
    val forgeDependency = create("net.minecraftforge:forge:$minecraftVersion-$forgeVersion") as ExternalModuleDependency
    forgeDependency.attributes {
        attribute(Attribute.of("net.minecraftforge.mappings.channel", String::class.java), "official")
        attribute(Attribute.of("net.minecraftforge.mappings.version", String::class.java), minecraftVersion)
    }
    implementation(minecraft.dependency(forgeDependency))
    compileOnly(common)
    compileOnly(commonSources["api"].output)
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")
    compileOnly("org.jetbrains:annotations:26.1.0")
    modShadeImplementation("net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion") { isTransitive = false }
    modShadeImplementation("net.mezzdev:filewatcher:$fileWatcherVersion") { isTransitive = false }
}

tasks.named<JavaCompile>(sourceSets.main.get().compileJavaTaskName) {
    source(commonSources["main"].allJava)
    source(commonSources["api"].allJava)
}
tasks.named<ProcessResources>(sourceSets.main.get().processResourcesTaskName) {
    from(commonSources["main"].resources)
    from(commonSources["api"].resources)
}

val smokeDirectory = layout.buildDirectory.dir("run/server-smoke")
val smokeResult = smokeDirectory.map { it.file("smoke-test-passed") }

minecraft {
    mappings("official", minecraftVersion)
    runs {
        configureEach {
            systemProperty("forge.logging.console.level", "info")
            mods {
                create(configModId) { source(sourceSets.main.get()) }
                create(forgeTestModId) { source(testMod) }
            }
        }
        create("client") {
            workingDir.set(layout.projectDirectory.dir("run/$minecraftVersion/client"))
            if (providers.systemProperty("os.name").get().startsWith("Mac")) {
                jvmArgs("-XstartOnFirstThread")
            }
        }
        create("server") {
            workingDir.set(layout.projectDirectory.dir("run/$minecraftVersion/server"))
            args("nogui")
            with(smokeTest) {
                workingDir.set(smokeDirectory)
                systemProperty("com.mojang.eula.agree", "true")
                systemProperty("mezzConfig.loaderSmokeTest.successFile", smokeResult.get().asFile.absolutePath)
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    })
    classpath(testMod.output)
}
tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.named<Jar>("sourcesJar") {
    from(commonSources["main"].allJava)
    from(commonSources["api"].allJava)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
val shadedJar = modShade.shadeJar()
val shadedSourcesJar = modShade.shadeSourcesJar()
modShade {
    shadedJar.configure { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    shadedSourcesJar.configure { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
}
publishMods { file.set(shadedJar.flatMap { it.archiveFile }) }
publishing {
    publications {
        register<MavenPublication>("configForgeJar") {
            artifactId = artifactName
            artifact(shadedJar)
            artifact(shadedSourcesJar)
        }
    }
}
tasks.assemble { dependsOn(shadedJar, shadedSourcesJar) }
tasks.check { dependsOn(tasks.named(testMod.classesTaskName)) }
val smokeRunTaskName = smokeTest.getTaskName("run", "server")
tasks.matching { it.name in setOf("runClient", "runServer", smokeRunTaskName) }.configureEach {
    dependsOn(tasks.named(testMod.classesTaskName))
}
tasks.matching { it.name == smokeRunTaskName }.configureEach {
    outputs.file(smokeResult)
    outputs.upToDateWhen { false }
    doFirst {
        val result = outputs.files.singleFile
        result.parentFile.mkdirs()
        result.resolveSibling("eula.txt").writeText("eula=true\n")
        result.resolveSibling("server.properties").writeText(
            """
                generate-structures=false
                generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
                level-type=minecraft:flat
                online-mode=false
                server-port=0
                simulation-distance=2
                spawn-protection=0
                view-distance=2
            """.trimIndent() + "\n"
        )
        result.delete()
    }
    doLast {
        check(outputs.files.singleFile.isFile) { "The Forge loader smoke test did not report success." }
    }
}
tasks.register("runServerSmokeTest") {
    group = "Slime Launcher"
    description = "Runs the Forge dedicated-server smoke test."
    dependsOn(smokeRunTaskName)
}
