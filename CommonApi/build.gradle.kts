plugins {
    id("idea")
    id("java")
    id("net.neoforged.moddev")
    id("maven-publish")
}


// gradle.properties
val minecraftVersion: String by extra
val neoformTimestamp: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val jetbrainsAnnotationsVersion: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-config-api"
base {
    archivesName.set(baseArchivesName)
}

neoForge {
    neoFormVersion = "$minecraftVersion-$neoformTimestamp"
}

sourceSets {
    named("main") {
        //The API has no resources
        resources.setSrcDirs(emptyList<String>())
    }
    named("test") {
        //The test module has no resources
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    implementation("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
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

publishing {
    publications {
        register<MavenPublication>("configApiJar") {
            artifactId = base.archivesName.get()
            artifact(tasks.jar)
            artifact(tasks.named("sourcesJar"))

            val dependencyInfos = listOf(
                mapOf(
                    "groupId" to "org.jetbrains",
                    "artifactId" to "annotations",
                    "version" to jetbrainsAnnotationsVersion
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
