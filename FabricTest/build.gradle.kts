plugins {
    java
    idea
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
val fabricLoaderVersion: String by extra
val minecraftVersion: String by extra
val configModId: String by extra
val fabricTestModId: String by extra
val modJavaVersion: String by extra
val parchmentMinecraftVersion: String by extra
val parchmentVersionFabric: String by extra
val jsr305Version: String by extra

val baseArchivesName = "${fabricTestModId}-${minecraftVersion}"
base {
    archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":CommonApi")
project.evaluationDependsOn(configApiProject.path)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(modJavaVersion))
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

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    @Suppress("UnstableApiUsage")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${parchmentMinecraftVersion}:${parchmentVersionFabric}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
    compileOnly(configApiProject)
}

loom {
    mods {
        create(fabricTestModId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    enabled = false
}

tasks.withType<Test>().configureEach {
    enabled = false
    classpath = files()
    testClassesDirs = files()
}

idea {
    module {
        for (fileName in listOf("build", "run", "out", "logs")) {
            excludeDirs.add(file(fileName))
        }
    }
}
