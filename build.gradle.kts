import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    // https://github.com/mezz/JavaFormatting
    id("net.mezzdev.java-formatting") version("0.2.3")

    // https://central.sonatype.com/artifact/net.mezzdev.gradle/modshade-plugin
    id("net.mezzdev.modshade") version("0.3.0") apply(false)

    // https://plugins.gradle.org/plugin/com.dorongold.task-tree
    id("com.dorongold.task-tree") version("4.0.0")

    // https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/maven-metadata.xml
    id("fabric-loom") version("1.11.0-alpha.26") apply(false)

    // https://projects.neoforged.net/neoforged/moddevgradle
    id("net.neoforged.moddev") version("2.0.26-beta") apply(false)

    // https://files.minecraftforge.net/net/minecraftforge/gradle/ForgeGradle/index.html
    id("net.minecraftforge.gradle") version("6.0.54") apply(false)

    // https://mvnrepository.com/artifact/org.parchmentmc.librarian.forgegradle/org.parchmentmc.librarian.forgegradle.gradle.plugin
    id("org.parchmentmc.librarian.forgegradle") version("1.2.0") apply(false)
}
repositories {
    mavenCentral()
}

// gradle.properties
val configModId: String by extra
val fabricTestModId: String by extra
val fabricLoaderVersion: String by extra
val fabricLoaderVersionRange: String by extra
val forgeVersionRange: String by extra
val forgeTestModId: String by extra
val githubUrl: String by extra
val forgeLoaderVersionRange: String by extra
val neoforgeVersionRange: String by extra
val neoforgeLoaderVersionRange: String by extra
val neoforgeTestModId: String by extra
val minecraftVersion: String by extra
val minecraftVersionRange: String by extra
val modAuthor: String by extra
val modDescription: String by extra
val modGroup: String by extra
val modId: String by extra
val modJavaVersion: String by extra
val modName: String by extra
val specificationVersion: String by extra
val releaseSpecificationVersion = specificationVersion

abstract class ValidateReleaseVersion : DefaultTask() {
    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val specificationVersion: Property<String>

    @TaskAction
    fun validate() {
        val releaseVersion = releaseVersion.get()
        val specificationVersion = specificationVersion.get()
        if (releaseVersion.isBlank()) {
            throw GradleException("No release version was provided; set RELEASE_VERSION or TAG_NAME.")
        }
        if (releaseVersion != specificationVersion) {
            throw GradleException(
                "Release version '$releaseVersion' does not match specificationVersion '$specificationVersion'."
            )
        }
    }
}

fun normalizeReleaseVersion(value: String): String {
    val tagName = value.trim().substringAfterLast('/')
    return tagName.removePrefix("v")
}

val configuredReleaseVersion = providers.gradleProperty("RELEASE_VERSION")
    .orElse(providers.environmentVariable("TAG_NAME"))
    .orNull
    ?.let(::normalizeReleaseVersion)
    ?.takeIf(String::isNotEmpty)
val buildNumber = providers.gradleProperty("BUILD_NUMBER")
    .orElse("9999")
    .get()
val projectVersion = configuredReleaseVersion ?: "${releaseSpecificationVersion}.${buildNumber}"

javaFormatting {
    target("*/src/*/java/net/mezzdev/**/*.java")
    all()
}

tasks.register<ValidateReleaseVersion>("validateReleaseVersion") {
    group = "verification"
    description = "Checks that a release tag matches specificationVersion."

    releaseVersion.set(configuredReleaseVersion ?: "")
    specificationVersion.set(releaseSpecificationVersion)
}

subprojects {
    version = projectVersion
    group = modGroup

    if (configuredReleaseVersion != null) {
        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(rootProject.tasks.named("validateReleaseVersion"))
        }
    }

    tasks.withType<Javadoc> {
        // workaround cast for https://github.com/gradle/gradle/issues/7038
        val standardJavadocDocletOptions = options as StandardJavadocDocletOptions
        // prevent java 8's strict doclint for javadocs from failing builds
        standardJavadocDocletOptions.addStringOption("Xdoclint:none", "-quiet")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(JavaLanguageVersion.of(modJavaVersion).asInt())
    }

    tasks.withType<Jar> {
        manifest {
            attributes(mapOf(
                "Specification-Title" to modName,
                "Specification-Vendor" to modAuthor,
                "Specification-Version" to specificationVersion,
                "Implementation-Title" to name,
                "Implementation-Version" to archiveVersion,
                "Implementation-Vendor" to modAuthor
            ))
        }
    }

    tasks.withType<ProcessResources> {
        exclude("**/.DS_Store")

        val properties = mapOf(
            "configModId" to configModId,
            "fabricTestModId" to fabricTestModId,
            "fabricLoaderVersion" to fabricLoaderVersion,
            "fabricLoaderVersionRange" to fabricLoaderVersionRange,
            "forgeVersionRange" to forgeVersionRange,
            "forgeTestModId" to forgeTestModId,
            "githubUrl" to githubUrl,
            "forgeLoaderVersionRange" to forgeLoaderVersionRange,
            "neoforgeVersionRange" to neoforgeVersionRange,
            "neoforgeLoaderVersionRange" to neoforgeLoaderVersionRange,
            "neoforgeTestModId" to neoforgeTestModId,
            "minecraftVersion" to minecraftVersion,
            "minecraftVersionRange" to minecraftVersionRange,
            "modAuthor" to modAuthor,
            "modDescription" to modDescription,
            "modId" to modId,
            "modJavaVersion" to modJavaVersion,
            "modName" to modName,
            "version" to version,
        )
        inputs.properties(properties)
        filesMatching(listOf("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta", "fabric.mod.json")) {
            expand(properties)
        }
    }

    // Activate reproducible builds
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.isDeprecation = true
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
