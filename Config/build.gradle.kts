import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("idea")
    id("java")
    id("net.neoforged.moddev")
    id("maven-publish")
}

// gradle.properties
val jUnitVersion: String by extra
val minecraftVersion: String by extra
val neoformTimestamp: String by extra
val configModId: String by extra
val configModGroup: String by extra
val modJavaVersion: String by extra
val mixinVersion: String by extra
val guavaVersion: String by extra
val jetbrainsAnnotationsVersion: String by extra
val log4jVersion: String by extra

group = configModGroup

val baseArchivesName = "${configModId}-${minecraftVersion}-config"
base {
    archivesName.set(baseArchivesName)
}

val dependencyProjects: List<Project> = listOf(
    project(":ConfigApi"),
)

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
    compileOnly("org.spongepowered:mixin:$mixinVersion")
    implementation("com.google.guava:guava:$guavaVersion")
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
    include("net/mezzdev/config/test/**")
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

val sourcesJarTask = tasks.named<Jar>("sourcesJar")

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
            artifact(tasks.jar.get())
            artifact(sourcesJarTask.get())

            val dependencyInfos = listOf(
                mapOf(
                    "groupId" to "com.google.guava",
                    "artifactId" to "guava",
                    "version" to guavaVersion
                ),
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
