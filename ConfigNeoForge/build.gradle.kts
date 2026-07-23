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
val configApiModId: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val deduplicatingRunnerVersion: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-neoforge"
base {
    archivesName.set(baseArchivesName)
}

val dependencyProjects: List<Project> = listOf(
    project(":Config"),
)
val configApiProject: Project = project(":ConfigApi")
val configApiRuntimeProject: Project = project(":ConfigApiNeoForge")

(listOf(configApiProject, configApiRuntimeProject) + dependencyProjects).forEach {
    project.evaluationDependsOn(it.path)
}

val embeddedLibraries: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
configurations.implementation {
    extendsFrom(embeddedLibraries)
}

extra["configLanguageDependencyProjects"] = dependencyProjects
apply(from = rootProject.file("buildtools/ConfigLanguageResources.gradle.kts"))

@Suppress("UNCHECKED_CAST")
val configLanguageResourceProjects = extra["configLanguageResourceProjects"] as List<Project>
val mergedConfigLanguageResources = tasks.named("mergeConfigLanguageResources")

neoForge {
    version = neoforgeVersion

    mods {
        create(configApiModId) {
            sourceSet(configApiRuntimeProject.sourceSets.main.get())
            sourceSet(configApiProject.sourceSets.main.get())
        }
        create(configModId) {
            sourceSet(sourceSets.main.get())
            for (dependencyProject in dependencyProjects) {
                sourceSet(dependencyProject.sourceSets.main.get())
            }
        }
    }

    runs {
        val configApiMod = mods.named(configApiModId)
        val configMod = mods.named(configModId)

        configureEach {
            getMods().set(setOf(configApiMod.get(), configMod.get()))
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

sourceSets {
    named("test") {
        //The test module has no resources
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    compileOnly(configApiProject)
    runtimeOnly(configApiRuntimeProject)
    dependencyProjects.forEach {
        implementation(it)
    }
    embeddedLibraries("net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion") {
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

tasks.named<ProcessResources>(sourceSets.main.get().processResourcesTaskName) {
    dependsOn(mergedConfigLanguageResources)
    for (p in configLanguageResourceProjects) {
        from(p.sourceSets.main.get().resources) {
            exclude("assets/mezz_config/lang/*.json")
        }
    }
    from(mergedConfigLanguageResources)
}

tasks.jar {
    dependsOn(mergedConfigLanguageResources, embeddedLibraries)
    exclude("net/mezzdev/config/api/**")
    from(sourceSets.main.get().output)
    for (p in dependencyProjects) {
        from(p.sourceSets.main.get().output) {
            exclude("assets/mezz_config/lang/*.json")
        }
    }
    from(mergedConfigLanguageResources)
    from(embeddedLibraries.map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourcesJarTask = tasks.named<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    for (p in dependencyProjects) {
        from(p.sourceSets.main.get().allJava)
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
}

tasks.assemble {
    dependsOn(sourcesJarTask)
}

publishing {
    publications {
        register<MavenPublication>("configNeoForgeJar") {
            artifactId = baseArchivesName
            artifact(tasks.jar.get())
            artifact(sourcesJarTask.get())

            val dependencyInfos = (listOf(configApiRuntimeProject) + dependencyProjects).map {
                mapOf(
                    "groupId" to it.group,
                    "artifactId" to it.base.archivesName.get(),
                    "version" to it.version
                )
            } + listOf(
                mapOf(
                    "groupId" to "net.mezzdev",
                    "artifactId" to "deduplicating-runner",
                    "version" to deduplicatingRunnerVersion
                )
            )

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
