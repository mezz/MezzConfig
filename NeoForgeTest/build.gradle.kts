plugins {
    id("java")
    id("idea")
    id("eclipse")
    id("net.neoforged.moddev")
}

// gradle.properties
val neoforgeVersion: String by extra
val minecraftVersion: String by extra
val neoforgeTestModId: String by extra
val modJavaVersion: String by extra
val jsr305Version: String by extra

val baseArchivesName = "${neoforgeTestModId}-${minecraftVersion}"
base {
    archivesName.set(baseArchivesName)
}

val configApiProject: Project = project(":CommonApi")
project.evaluationDependsOn(configApiProject.path)

neoForge {
    version = neoforgeVersion

    mods {
        create(neoforgeTestModId) {
            sourceSet(sourceSets.main.get())
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
    compileOnly("com.google.code.findbugs:jsr305:$jsr305Version")
    compileOnly(configApiProject)
}

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
