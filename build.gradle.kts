import me.modmuss50.mpp.ModPublishExtension
import me.modmuss50.mpp.PublishModTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.util.Locale
import java.util.zip.ZipFile

plugins {
    id("me.modmuss50.mod-publish-plugin") apply(false)

    // https://github.com/mezz/JavaFormatting
    id("net.mezzdev.java-formatting") version("0.4.0")

    // https://central.sonatype.com/artifact/net.mezzdev.gradle/modshade-plugin
    id("net.mezzdev.modshade") version("0.7.0") apply(false)

    // https://plugins.gradle.org/plugin/com.dorongold.task-tree
    id("com.dorongold.task-tree") version("4.0.2")

    // https://github.com/neoforged/JarCompatibilityChecker
    id("net.neoforged.jarcompatibilitychecker") version("0.1.19") apply(false)


}
repositories {
    mavenCentral()
}

// gradle.properties
val configModId: String by extra
val fabricTestModId: String by extra
val fabricLoaderVersion: String by extra
val fabricLoaderVersionRange: String by extra
val fabricApiVersionRange: String by extra
val forgeVersionRange = findProperty("forgeVersionRange")?.toString().orEmpty()
val forgeTestModId: String by extra
val githubUrl: String by extra
val forgeLoaderVersionRange = findProperty("forgeLoaderVersionRange")?.toString().orEmpty()
val neoforgeVersionRange = findProperty("neoforgeVersionRange")?.toString().orEmpty()
val neoforgeLoaderVersionRange = findProperty("neoforgeLoaderVersionRange")?.toString().orEmpty()
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
val modPublishDryRun = providers.gradleProperty("publishDryRun").orElse("true")
    .map { it.toBooleanStrict() }.get()

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

abstract class ValidateDocumentationLinks : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val markdownFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun validate() {
        val rootDir = rootDirectory.get().asFile
        val failures = mutableListOf<String>()
        for (sourceFile in markdownFiles.files.sorted()) {
            for (match in MARKDOWN_LINK_PATTERN.findAll(sourceFile.readText())) {
                val target = match.groupValues[1]
                if (target.startsWith("http://") ||
                    target.startsWith("https://") ||
                    target.startsWith("mailto:")
                ) {
                    continue
                }
                val location = target.substringBefore('#')
                val anchor = target.substringAfter('#', "")
                val targetFile = if (location.isEmpty()) {
                    sourceFile
                } else {
                    sourceFile.parentFile.toPath().resolve(location).normalize().toFile()
                }
                val sourcePath = sourceFile.relativeTo(rootDir)
                if (!targetFile.isFile) {
                    failures.add("$sourcePath links to missing file '$target'.")
                    continue
                }
                if (anchor.isNotEmpty()) {
                    val anchors = targetFile.useLines { lines ->
                        lines.mapNotNull { line ->
                            MARKDOWN_HEADING_PATTERN.matchEntire(line)
                                ?.groupValues
                                ?.get(1)
                                ?.let(::markdownHeadingAnchor)
                        }.toSet()
                    }
                    if (anchor !in anchors) {
                        failures.add("$sourcePath links to missing heading '#$anchor' in '$location'.")
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString(separator = "\n"))
        }
    }

    private fun markdownHeadingAnchor(heading: String): String = heading
        .replace(Regex("""\s+#+\s*$"""), "")
        .replace(Regex("""[`*_~]"""), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}\p{M} _-]"""), "")
        .trim()
        .replace(Regex("""\s+"""), "-")

    private companion object {
        val MARKDOWN_LINK_PATTERN = Regex("""(?<!!)\[[^]]+]\(([^)\s]+)\)""")
        val MARKDOWN_HEADING_PATTERN = Regex("""^#{1,6}\s+(.+?)\s*$""")
    }
}

abstract class ValidateFabricPublication : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fabricJar: RegularFileProperty

    @TaskAction
    fun validate() {
        val fabricJar = fabricJar.get().asFile
        val requiredEntries = setOf(
            "net/mezzdev/config/fabric/ConfigFabric.class",
            "net/mezzdev/config/registration/ConfigProvider.class",
            "net/mezzdev/config/minecraft/MinecraftConfigRuntime.class"
        )
        ZipFile(fabricJar).use { archive ->
            for (requiredEntry in requiredEntries) {
                if (archive.getEntry(requiredEntry) == null) {
                    throw GradleException("Fabric Maven artifact '${fabricJar.name}' is missing '$requiredEntry'.")
                }
            }
        }
    }
}

fun normalizeReleaseVersion(value: String): String {
    val tagName = value.trim().substringAfterLast('/')
    return tagName.removePrefix("v")
}

fun Configuration.singleFileContents(): Provider<String> =
    incoming.files.elements.map { elements -> elements.single().asFile.readText() }

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

val validatePublishing = tasks.register("validatePublishing") {
    group = "verification"
    description = "Publishes every Maven publication to a local validation repository."
}

val validatePublishedApiConsumers = tasks.register("validatePublishedApiConsumers") {
    group = "verification"
    description = "Resolves the published API against every supported loader's Minecraft dependency constraints."
}

val publishMavenRelease = tasks.register("publishMavenRelease") {
    group = "publishing"
    description = "Publishes the supported Maven artifacts to the release repository."
    dependsOn(tasks.named("validateReleaseVersion"))
}

val projectMarkdownFiles = fileTree(rootDir) {
    include("*.md", "docs/**/*.md")
}
tasks.register<ValidateDocumentationLinks>("validateDocumentationLinks") {
    group = "verification"
    description = "Checks local Markdown file links and heading anchors."
    markdownFiles.from(projectMarkdownFiles)
    rootDirectory.set(layout.projectDirectory)
}

tasks.register<ValidateFabricPublication>("validateFabricEmbedding") {
    group = "verification"
    description = "Checks that the published Fabric artifact contains the complete runtime."
    dependsOn(validatePublishing)
    val publicationGroupPath = modGroup.replace('.', '/')
    val fabricModule = "${configModId}-${minecraftVersion}-fabric"
    fabricJar.set(layout.buildDirectory.file(
        "publication-validation/$publicationGroupPath/$fabricModule/$projectVersion/$fabricModule-$projectVersion.jar"
    ))
}

tasks.register<GradleBuild>("validateNeoForgeEmbedding") {
    group = "verification"
    description = "Builds a ModDevGradle consumer and checks its embedded runtime and API."
    dependsOn(validatePublishing)
    dir = file("validation/neoforge-embedding")
    tasks = listOf("check")
    startParameter.projectCacheDir = file(".gradle/targets/$minecraftVersion/neoforge-embedding")
    startParameter.projectProperties = mapOf("mezzConfigVersion" to projectVersion, "minecraftVersion" to minecraftVersion)
    notCompatibleWithConfigurationCache("Runs a separate consumer build against the local validation repository.")
}

subprojects {
    version = projectVersion
    group = modGroup

    if (name in setOf("Fabric", "Forge", "NeoForge")) {
        plugins.withId("java") {
            afterEvaluate {
                val compileClasspath = configurations.getByName("compileClasspath")
                val consumer = configurations.create("publishedApiConsumer") {
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    extendsFrom(compileClasspath)
                    compileClasspath.attributes.keySet().forEach { key ->
                        @Suppress("UNCHECKED_CAST")
                        val typedKey = key as Attribute<Any>
                        attributes.attribute(typedKey, compileClasspath.attributes.getAttribute(typedKey)!!)
                    }
                }
                val apiModule = "${configModId}-${minecraftVersion}-config-api"
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven { url = rootProject.layout.buildDirectory.dir("publication-validation").get().asFile.toURI() }
                        }
                        filter { includeModule(modGroup, apiModule) }
                    }
                }
                dependencies.add(consumer.name, "$modGroup:$apiModule:$projectVersion")
                val consumerConfigurationName = consumer.name
                val validateConsumer = tasks.register("validatePublishedApiConsumer") {
                    group = "verification"
                    dependsOn(validatePublishing)
                    notCompatibleWithConfigurationCache("Resolves artifacts published by this build during task execution.")
                    doLast {
                        // Capturing the configuration would let the cache resolve it before publication.
                        val files = project.configurations.getByName(consumerConfigurationName).files
                        logger.lifecycle("Published API resolves against the loader compile classpath ({} files).", files.size)
                    }
                }
                validatePublishedApiConsumers.configure { dependsOn(validateConsumer) }
            }
        }
    }

    plugins.withId("me.modmuss50.mod-publish-plugin") {
        val loaderName = project.name
        val changelogHtml = configurations.create("changelogHtml") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>("changelogHtml"))
            }
        }
        val changelogMarkdown = configurations.create("changelogMarkdown") {
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>("changelogMarkdown"))
            }
        }
        dependencies {
            add(changelogHtml.name, project(":Changelog"))
            add(changelogMarkdown.name, project(":Changelog"))
        }
        extensions.configure<ModPublishExtension> {
            dryRun.set(modPublishDryRun)
            version.set(projectVersion)
            displayName.set("$modName $projectVersion for $loaderName $minecraftVersion")
            type.set(BETA)
            modLoaders.add(loaderName.lowercase(Locale.ROOT))
            changelog.set(changelogMarkdown.singleFileContents())

            curseforge {
                projectId.set(providers.gradleProperty("curseProjectId"))
                projectSlug.set("mezzconfig")
                accessToken.set(providers.gradleProperty("curseforgeApikey"))
                changelog.set(changelogHtml.singleFileContents())
                changelogType.set("html")
                minecraftVersions.add(minecraftVersion)
                javaVersions.add(JavaVersion.toVersion(modJavaVersion))
                clientRequired.set(true)
                serverRequired.set(true)
                if (loaderName == "Fabric") {
                    requires("fabric-api")
                }
            }

            modrinth {
                projectId.set(providers.gradleProperty("modrinthId"))
                accessToken.set(providers.gradleProperty("modrinthToken"))
                minecraftVersions.add(minecraftVersion)
                if (loaderName == "Fabric") {
                    requires("fabric-api")
                }
            }
        }
        tasks.withType<PublishModTask>().configureEach {
            if (!modPublishDryRun) {
                dependsOn(rootProject.tasks.named("validateReleaseVersion"))
            }
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "validation"
                    url = rootProject.layout.buildDirectory.dir("publication-validation").get().asFile.toURI()
                }
                providers.gradleProperty("DEPLOY_DIR").orNull?.let { deployDir ->
                    maven {
                        name = "release"
                        url = uri(deployDir)
                    }
                }
            }
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set("$modName ${project.name}")
                    description.set(modDescription)
                    url.set(githubUrl)

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/license/mit")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set(modAuthor)
                            name.set(modAuthor)
                        }
                    }
                    scm {
                        connection.set("scm:git:$githubUrl.git")
                        developerConnection.set("scm:git:$githubUrl.git")
                        url.set(githubUrl)
                    }
                }
            }
        }

        val validationPublicationTaskPath = "$path:publishAllPublicationsToValidationRepository"
        validatePublishing.configure {
            dependsOn(validationPublicationTaskPath)
        }
    }

    tasks.withType<PublishToMavenRepository>().configureEach {
        // The compatibility baseline may come from the same local validation repository.
        // Finish reading it before any publication task can replace the artifact.
        dependsOn(":Common:checkJarCompatibility")
    }

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
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename("LICENSE", "LICENSE-MezzConfig")
        }
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
			"fabricApiVersionRange" to fabricApiVersionRange,
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

publishMavenRelease.configure {
    if (providers.gradleProperty("DEPLOY_DIR").isPresent) {
        dependsOn(":Common:publishConfigApiJarPublicationToReleaseRepository")
        listOf("Fabric", "Forge", "NeoForge").filter { findProject(":$it") != null }.forEach {
            dependsOn(":$it:publishConfig${it}JarPublicationToReleaseRepository")
        }
    } else {
        doFirst {
            throw GradleException("No Maven release repository was provided; set DEPLOY_DIR.")
        }
    }
}

subprojects {
    tasks.withType<JavaCompile> {
        options.isDeprecation = true
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
