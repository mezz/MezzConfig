import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

plugins {
    java
    id("net.neoforged.moddev") version "2.0.146"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

val mezzConfigVersion: String by project
val mezzConfigVersionRange = "[0.5.0,1.0.0)"

repositories {
    maven { url = uri("../../build/publication-validation") }
}

// Keep this dependency block aligned with docs/neoforge.md.
dependencies {
    jarJar("net.mezzdev.config:mezz_config-1.21.1-neoforge:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
    jarJar("net.mezzdev.config:mezz_config-1.21.1-config:$mezzConfigVersion") { version { strictly(mezzConfigVersionRange); prefer(mezzConfigVersion) } }
}

val consumerJar = tasks.named<Jar>("jar")
val validateEmbeddedRuntime = tasks.register("validateEmbeddedRuntime") {
    dependsOn(consumerJar)
    inputs.file(consumerJar.flatMap { it.archiveFile })
    doLast {
        val requiredEntries = mapOf(
            "mezz_config-1.21.1-neoforge" to setOf(
                "net/mezzdev/config/neoforge/ConfigNeoForge.class",
                "META-INF/neoforge.mods.toml"
            ),
            "mezz_config-1.21.1-config" to setOf(
                "net/mezzdev/config/api/Configs.class",
                "net/mezzdev/config/registration/ConfigProvider.class",
                "net/mezzdev/config/modshade/net/mezzdev/filewatcher/FileWatcher.class",
                "net/mezzdev/config/modshade/net/mezzdev/deduplicatingrunner/DeduplicatingRunner.class",
                "META-INF/services/net.mezzdev.config.api.Configs\$IConfigProvider"
            )
        )
        ZipFile(consumerJar.get().archiveFile.get().asFile).use { archive ->
            val metadataEntry = archive.getEntry("META-INF/jarjar/metadata.json")
                ?: error("Consumer jar has no Jar-in-Jar metadata.")
            val metadata = archive.getInputStream(metadataEntry).use { JsonSlurper().parse(it) } as Map<*, *>
            val embeddedJars = metadata["jars"] as List<*>
            val remainingModules = requiredEntries.toMutableMap()
            for (entry in embeddedJars) {
                val embedded = entry as Map<*, *>
                val identifier = embedded["identifier"] as Map<*, *>
                val module = identifier["artifact"] as String
                val required = remainingModules.remove(module) ?: continue
                check(identifier["group"] == "net.mezzdev.config") { "Unexpected group for $module" }
                val version = embedded["version"] as Map<*, *>
                check(version["artifactVersion"] == mezzConfigVersion) { "Wrong embedded version for $module" }
                check(version["range"] == mezzConfigVersionRange) { "Wrong supported range for $module" }
                val nestedJar = archive.getEntry(embedded["path"] as String)
                    ?: error("Missing nested jar for $module")
                val missingEntries = required.toMutableSet()
                ZipInputStream(archive.getInputStream(nestedJar)).use { nested ->
                    while (true) {
                        val nestedEntry = nested.nextEntry ?: break
                        missingEntries.remove(nestedEntry.name)
                    }
                }
                check(missingEntries.isEmpty()) { "$module is missing $missingEntries" }
            }
            check(remainingModules.isEmpty()) { "Consumer jar is missing ${remainingModules.keys}" }
        }
    }
}

tasks.check { dependsOn(validateEmbeddedRuntime) }
