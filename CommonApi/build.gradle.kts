import groovy.json.JsonSlurper
import net.neoforged.jarcompatibilitychecker.gradle.CompatibilityTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task

// CompatibilityTask.fail delegates to ConsoleTool's System.exit in JCC 0.1.18.
class FailOnJccErrors : Action<Task> {
    override fun execute(task: Task) {
        val compatibilityTask = task as CompatibilityTask
        val report = JsonSlurper().parse(compatibilityTask.output.get().asFile)
        if (containsErrors(report)) {
            throw GradleException("JarCompatibilityChecker found incompatible CommonApi changes.")
        }
    }

    private fun containsErrors(value: Any?): Boolean = when (value) {
        is Map<*, *> -> value["isError"] == true || value.values.any(::containsErrors)
        is Iterable<*> -> value.any(::containsErrors)
        else -> false
    }
}

plugins {
    id("idea")
    id("java")
    id("net.neoforged.moddev")
    id("maven-publish")
    id("net.neoforged.jarcompatibilitychecker")
}

// gradle.properties
val minecraftVersion: String by extra
val neoformTimestamp: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val jetbrainsAnnotationsVersion: String by extra
val jspecifyVersion: String by extra
val apiBaselineVersion: String by extra
val specificationVersion: String by extra
val isInitialApiRelease = apiBaselineVersion == specificationVersion

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-config-api"
base {
    archivesName.set(baseArchivesName)
}

neoForge {
    neoFormVersion = "$minecraftVersion-$neoformTimestamp"
}

repositories {
    maven {
        name = "publicationValidation"
        url = rootProject.layout.buildDirectory.dir("publication-validation").get().asFile.toURI()
        content {
            includeGroup(configModGroup)
        }
    }
    maven("https://maven.blamejared.com") {
        name = "mezzReleases"
        content {
            includeGroup(configModGroup)
        }
    }
    mavenCentral()
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
    implementation("org.jspecify:jspecify:$jspecifyVersion")
}

val apiBaseline by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    apiBaseline("$group:$baseArchivesName:$apiBaselineVersion")
}

val apiBaselineArchives = apiBaseline.incoming.artifactView {
    isLenient = isInitialApiRelease
}.files
val missingApiBaselineArchive = layout.buildDirectory.file("api-baseline/missing-$apiBaselineVersion.jar")
val apiBaselineArchive = layout.file(apiBaselineArchives.elements.map { archives ->
    archives.singleOrNull()?.asFile ?: missingApiBaselineArchive.get().asFile
})

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    javaToolchains {
        compilerFor {
            languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
        }
    }
}

val checkJarCompatibility = tasks.named<CompatibilityTask>("checkJarCompatibility") {
    group = "verification"
    description = "Checks CommonApi API compatibility with the latest released baseline."

    baseJar.set(apiBaselineArchive)
    doLast(FailOnJccErrors())
    onlyIf("the initial CommonApi $apiBaselineVersion baseline has been published") {
        baseJar.get().asFile.exists()
    }
}

tasks.check {
    dependsOn(checkJarCompatibility)
}

publishing {
    publications {
        register<MavenPublication>("configApiJar") {
            artifactId = base.archivesName.get()
            artifact(tasks.jar)
            artifact(tasks.named("sourcesJar"))
            artifact(tasks.named("javadocJar"))

            val dependencyInfos = listOf(
                mapOf(
                    "groupId" to "org.jetbrains",
                    "artifactId" to "annotations",
                    "version" to jetbrainsAnnotationsVersion
                ),
                mapOf(
                    "groupId" to "org.jspecify",
                    "artifactId" to "jspecify",
                    "version" to jspecifyVersion
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
