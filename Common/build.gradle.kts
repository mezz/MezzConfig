import groovy.json.JsonSlurper
import net.neoforged.jarcompatibilitychecker.gradle.CompatibilityTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Task
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

// CompatibilityTask.fail delegates to ConsoleTool's System.exit in JCC 0.1.18.
class FailOnJccErrors : Action<Task> {
    override fun execute(task: Task) {
        val compatibilityTask = task as CompatibilityTask
        val report = JsonSlurper().parse(compatibilityTask.output.get().asFile)
        if (containsErrors(report)) {
            throw GradleException("JarCompatibilityChecker found incompatible API changes.")
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
    id("net.neoforged.jarcompatibilitychecker")
    id("maven-publish")
    id("net.mezzdev.modshade")
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            ivy {
                name = "shadedDependencyLicenses"
                url = uri("https://raw.githubusercontent.com")
                patternLayout {
                    artifact("[organisation]/[module]/v[revision]/LICENSE")
                }
                metadataSources {
                    artifact()
                }
            }
        }
        filter {
            includeGroup("mezz")
        }
    }
}

// gradle.properties
val jUnitVersion: String by extra
val minecraftVersion: String by extra
val neoformTimestamp: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val deduplicatingRunnerVersion: String by extra
val fileWatcherVersion: String by extra
val jetbrainsAnnotationsVersion: String by extra
val jspecifyVersion: String by extra
val log4jVersion: String by extra
val apiBaselineVersion: String by extra
val specificationVersion: String by extra
val isInitialApiRelease = apiBaselineVersion == specificationVersion

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
}

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-config"
val apiArchivesName = "${configModId}-${minecraftVersion}-config-api"
base {
    archivesName.set(baseArchivesName)
}

val fileWatcherLicense by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val deduplicatingRunnerLicense by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

neoForge {
    neoFormVersion = "$minecraftVersion-$neoformTimestamp"
    addModdingDependenciesTo(sourceSets.test.get())
}

dependencies {
    fileWatcherLicense("mezz:FileWatcher:$fileWatcherVersion")
    deduplicatingRunnerLicense("mezz:DeduplicatingRunner:$deduplicatingRunnerVersion")
    modShadeImplementation("net.mezzdev:deduplicating-runner:$deduplicatingRunnerVersion") {
        isTransitive = false
    }
    modShadeImplementation("net.mezzdev:filewatcher:$fileWatcherVersion") {
        isTransitive = false
    }
    implementation("org.jetbrains:annotations:$jetbrainsAnnotationsVersion")
    implementation("org.jspecify:jspecify:$jspecifyVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$jUnitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    include(
        "net/mezzdev/config/test/**",
        "net/mezzdev/config/file/**",
        "net/mezzdev/config/registration/**",
        "net/mezzdev/config/server/**"
    )
    outputs.upToDateWhen { false }
    testLogging {
        events = setOf(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
    }
    withSourcesJar()
}

val apiJarTask = tasks.register<Jar>("apiJar") {
    archiveBaseName.set(apiArchivesName)
    from(sourceSets.main.get().output) {
        include("net/mezzdev/config/api/**")
    }
}

val apiSourcesJarTask = tasks.register<Jar>("apiSourcesJar") {
    archiveBaseName.set(apiArchivesName)
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allJava) {
        include("net/mezzdev/config/api/**")
    }
}

val apiJavadocDir = layout.buildDirectory.dir("docs/apiJavadoc")
val apiJavadocTask = tasks.register<Javadoc>("apiJavadoc") {
    source(sourceSets.main.get().allJava.matching {
        include("net/mezzdev/config/api/**")
    })
    classpath = sourceSets.main.get().compileClasspath
    destinationDir = apiJavadocDir.get().asFile
}

val apiJavadocJarTask = tasks.register<Jar>("apiJavadocJar") {
    dependsOn(apiJavadocTask)
    archiveBaseName.set(apiArchivesName)
    archiveClassifier.set("javadoc")
    from(apiJavadocDir)
}

tasks.assemble {
    dependsOn(apiJarTask, apiSourcesJarTask, apiJavadocJarTask)
}

val apiBaseline by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    apiBaseline("$group:$apiArchivesName:$apiBaselineVersion")
}

val apiBaselineArchives = apiBaseline.incoming.artifactView {
    isLenient = isInitialApiRelease
}.files
val missingApiBaselineArchive = layout.buildDirectory.file("api-baseline/missing-$apiBaselineVersion.jar")
val apiBaselineArchive = layout.file(apiBaselineArchives.elements.map { archives ->
    archives.singleOrNull()?.asFile ?: missingApiBaselineArchive.get().asFile
})

val checkJarCompatibility = tasks.named<CompatibilityTask>("checkJarCompatibility") {
    group = "verification"
    description = "Checks the public API artifact against the latest released baseline."

    inputJar.set(apiJarTask.flatMap { it.archiveFile })
    baseJar.set(apiBaselineArchive)
    doLast(FailOnJccErrors())
    onlyIf("the initial API $apiBaselineVersion baseline has been published") {
        baseJar.get().asFile.exists()
    }
}

tasks.check {
    dependsOn(checkJarCompatibility)
}

val shadedDependencyLicenses = copySpec {
    from(fileWatcherLicense) {
        into("META-INF")
        rename(".*", "LICENSE-FileWatcher")
    }
    from(deduplicatingRunnerLicense) {
        into("META-INF")
        rename(".*", "LICENSE-DeduplicatingRunner")
    }
}

modShade {
    shadeJar().configure {
        with(shadedDependencyLicenses)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    shadeSourcesJar().configure {
        with(shadedDependencyLicenses)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
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
            artifactId = apiArchivesName
            artifact(apiJarTask)
            artifact(apiSourcesJarTask)
            artifact(apiJavadocJarTask)

            pom {
                name.set("MezzConfig API")
            }

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
        register<MavenPublication>("configJar") {
            artifactId = baseArchivesName
            from(components["modShade"])

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
                ),
                mapOf(
                    "groupId" to "org.apache.logging.log4j",
                    "artifactId" to "log4j-api",
                    "version" to log4jVersion
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
