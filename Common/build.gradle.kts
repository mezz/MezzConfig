import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("idea")
    id("java")
    id("net.neoforged.moddev")
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
val log4jVersion: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-config"
base {
    archivesName.set(baseArchivesName)
}

val dependencyProjects: List<Project> = listOf(
    project(":CommonApi"),
)
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

dependencyProjects.forEach {
    project.evaluationDependsOn(it.path)
}

neoForge {
    neoFormVersion = "$minecraftVersion-$neoformTimestamp"
    addModdingDependenciesTo(sourceSets.test.get())
}

sourceSets {
    named("test") {
        //The test module has no resources
        resources.setSrcDirs(emptyList<String>())
    }
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
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    dependencyProjects.forEach {
        implementation(it)
    }
    testImplementation("org.junit.jupiter:junit-jupiter:$jUnitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    include(
        "net/mezzdev/config/test/**",
        "net/mezzdev/config/file/**"
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

modShade {
    shadeJar()
    shadeSourcesJar()
}

tasks.withType<Jar>().configureEach {
    from(fileWatcherLicense) {
        into("META-INF")
        rename(".*", "LICENSE-FileWatcher")
    }
    from(deduplicatingRunnerLicense) {
        into("META-INF")
        rename(".*", "LICENSE-DeduplicatingRunner")
    }
}

tasks.named<Jar>("modShadeJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Jar>("modShadeSourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
                    "groupId" to "org.apache.logging.log4j",
                    "artifactId" to "log4j-api",
                    "version" to log4jVersion
                )
            ) + dependencyProjects.map {
                mapOf(
                    "groupId" to it.group,
                    "artifactId" to it.base.archivesName.get(),
                    "version" to it.version
                )
            }

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
