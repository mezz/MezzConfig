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
val fabricApiVersion: String by extra
val fabricLoaderVersion: String by extra
val minecraftVersion: String by extra
val configApiModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val parchmentMinecraftVersion: String by extra
val parchmentVersionFabric: String by extra

group = configModGroup

val baseArchivesName = "${configApiModId}-${minecraftVersion}-fabric"
base {
    archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":ConfigApi")
project.evaluationDependsOn(configApiProject.path)

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
    implementation(configApiProject)
}

loom {
    mods {
        create(configApiModId) {
            sourceSet(sourceSets.main.get())
            sourceSet(configApiProject.sourceSets.main.get())
        }
    }
}

tasks.jar {
    from(sourceSets.main.get().output)
    from(configApiProject.sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("sourcesJar") {
    from(sourceSets.main.get().allJava)
    from(configApiProject.sourceSets.main.get().allJava)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("sources")
}

tasks.assemble {
    dependsOn(tasks.remapJar, tasks.remapSourcesJar)
}

publishing {
    publications {
        register<MavenPublication>("configApiFabricJar") {
            @Suppress("UnstableApiUsage")
            loom.disableDeprecatedPomGeneration(this)
            artifactId = baseArchivesName
            artifact(tasks.remapJar)
            artifact(tasks.remapSourcesJar)

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
