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
val configModGroup: String by extra
val modJavaVersion: String by extra

group = configModGroup

val baseArchivesName = "${configApiModId}-${minecraftVersion}-neoforge"
base {
    archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":ConfigApi")
project.evaluationDependsOn(configApiProject.path)

neoForge {
    version = neoforgeVersion

    mods {
        create(configApiModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(configApiProject.sourceSets.main.get())
        }
    }

    runs {
        val configApiMod = mods.named(configApiModId)

        configureEach {
            getMods().set(setOf(configApiMod.get()))
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
    implementation(configApiProject)
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
    from(sourceSets.main.get().output)
    from(configApiProject.sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val sourcesJarTask = tasks.named<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    from(configApiProject.sourceSets.main.get().allJava)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
}

tasks.assemble {
    dependsOn(sourcesJarTask)
}

publishing {
    publications {
        register<MavenPublication>("configApiNeoForgeJar") {
            artifactId = baseArchivesName
            artifact(tasks.jar.get())
            artifact(sourcesJarTask.get())

            val dependencyInfo = mapOf(
                "groupId" to configApiProject.group,
                "artifactId" to configApiProject.base.archivesName.get(),
                "version" to configApiProject.version
            )

            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")
                val dependencyNode = dependenciesNode.appendNode("dependency")
                dependencyInfo.forEach { (key, value) ->
                    dependencyNode.appendNode(key, value)
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
