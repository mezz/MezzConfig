import groovy.json.JsonSlurper
import java.io.ByteArrayInputStream
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile

plugins {
    java
    id("net.neoforged.moddev")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

val minecraftVersion = providers.gradleProperty("minecraftVersion").orElse("1.21.1").get()
layout.buildDirectory.set(layout.projectDirectory.dir("build/$minecraftVersion"))
val mezzConfigVersion: String by project
val mezzConfigVersionRange = "[0.5.0,1.0.0)"
val runtimeModule = "mezz_config-$minecraftVersion-neoforge"
val runtimeFileName = "$runtimeModule-$mezzConfigVersion.jar"

repositories {
    maven { url = uri("../../build/$minecraftVersion/publication-validation") }
}

// Keep this dependency block aligned with docs/neoforge.md.
dependencies {
    jarJar("net.mezzdev.config:mezz_config-$minecraftVersion-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}

fun ZipFile.readEmbeddedRuntime(expectedRange: String): ByteArray {
    val metadataEntry = getEntry("META-INF/jarjar/metadata.json") ?: error("Missing Jar-in-Jar metadata.")
    val metadata = getInputStream(metadataEntry).use { JsonSlurper().parse(it) } as Map<*, *>
    val embedded = (metadata["jars"] as List<*>).single() as Map<*, *>
    check(embedded["identifier"] == mapOf("group" to "net.mezzdev.config", "artifact" to runtimeModule)) {
        "Embedded runtime does not use the Maven artifact's identity."
    }
    check(embedded["version"] == mapOf("range" to expectedRange, "artifactVersion" to mezzConfigVersion)) {
        "Wrong embedded version or supported range."
    }
    val nestedJar = getEntry(embedded["path"] as String) ?: error("Missing nested runtime jar.")
    return getInputStream(nestedJar).use { it.readBytes() }
}

val consumerJar = tasks.named<Jar>("jar")
val validateEmbeddedRuntime = tasks.register("validateEmbeddedRuntime") {
    dependsOn(consumerJar)
    inputs.file(consumerJar.flatMap { it.archiveFile })
    doLast {
        val missingEntries = mutableSetOf(
            "net/mezzdev/config/neoforge/ConfigNeoForge.class",
            "META-INF/neoforge.mods.toml",
            "net/mezzdev/config/api/Configs.class",
            "net/mezzdev/config/registration/ConfigProvider.class",
            "net/mezzdev/config/minecraft/MinecraftConfigRuntime.class",
            "net/mezzdev/config/modshade/net/mezzdev/filewatcher/FileWatcher.class",
            "net/mezzdev/config/modshade/net/mezzdev/deduplicatingrunner/DeduplicatingRunner.class",
            "META-INF/services/net.mezzdev.config.api.Configs\$IConfigProvider"
        )
        ZipFile(consumerJar.get().archiveFile.get().asFile).use { archive ->
            val runtime = archive.readEmbeddedRuntime(mezzConfigVersionRange)
            JarInputStream(ByteArrayInputStream(runtime)).use { nested ->
                while (true) {
                    val entry = nested.nextEntry ?: break
                    missingEntries.remove(entry.name)
                }
            }
        }
        check(missingEntries.isEmpty()) { "Embedded runtime is missing $missingEntries" }
    }
}

val validateStandaloneDistribution = tasks.register("validateStandaloneDistribution") {
    val standaloneJar = file("../../NeoForge/build/$minecraftVersion/libs/$runtimeModule-$mezzConfigVersion-standalone.jar")
    val runtimeJar = file("../../build/$minecraftVersion/publication-validation/net/mezzdev/config/$runtimeModule/$mezzConfigVersion/$runtimeFileName")
    inputs.file(standaloneJar)
    inputs.file(runtimeJar)
    doLast {
        ZipFile(standaloneJar).use { archive ->
            val manifest = archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF")).use(::Manifest)
            check(manifest.mainAttributes.getValue("FMLModType") == "GAMELIBRARY") { "Wrapper is not a game library." }
            check(archive.getEntry("META-INF/neoforge.mods.toml") == null) { "Wrapper declares a top-level mod." }
            check(archive.entries().asSequence().none { it.name.endsWith(".class") }) { "Wrapper contains runtime classes." }
            val runtimeBytes = archive.readEmbeddedRuntime("[$mezzConfigVersion,)")
            check(runtimeBytes.contentEquals(runtimeJar.readBytes())) { "Wrapper does not contain the published Maven runtime." }
        }
    }
}

tasks.check { dependsOn(validateEmbeddedRuntime, validateStandaloneDistribution) }
