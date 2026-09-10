import se.bjurr.gitchangelog.plugin.gradle.GitChangelogTask

plugins {
    base
    id("se.bjurr.gitchangelog.git-changelog-gradle-plugin") version("3.1.2")
}

val specificationVersion: String by extra
val changelogUntaggedName = "Current release $specificationVersion"

val makeHtmlChangelog = tasks.register<GitChangelogTask>("makeHtmlChangelog") {
    val output = layout.buildDirectory.file("changelog.html")

    fromRepo.set(rootProject.rootDir.absolutePath)
    file.set(output.get().asFile)
    untaggedName.set(changelogUntaggedName)
    fromRevision.set("HEAD~30")
    toRevision.set("HEAD")
    templateContent.set(providers.fileContents(layout.projectDirectory.file("changelog.mustache")).asText)

    outputs.file(output)
}

val makeMarkdownChangelog = tasks.register<GitChangelogTask>("makeMarkdownChangelog") {
    val output = layout.buildDirectory.file("changelog.md")

    fromRepo.set(rootProject.rootDir.absolutePath)
    file.set(output.get().asFile)
    untaggedName.set(changelogUntaggedName)
    fromRevision.set(providers.environmentVariable("GIT_PREVIOUS_SUCCESSFUL_COMMIT").orElse("HEAD~10"))
    toRevision.set("HEAD")
    templateContent.set(providers.fileContents(layout.projectDirectory.file("changelog-markdown.mustache")).asText)

    outputs.file(output)
}

tasks.register("makeChangelog") {
    group = "documentation"
    description = "Generates the CurseForge HTML and Modrinth Markdown changelogs from Git history."
    dependsOn(makeHtmlChangelog, makeMarkdownChangelog)
}

tasks.withType<GitChangelogTask>().configureEach {
    outputs.upToDateWhen { false }
}

configurations.create("changelogHtml") {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>("changelogHtml"))
    }
    outgoing.artifact(makeHtmlChangelog.map { it.outputs.files.singleFile }) {
        type = "html"
    }
}

configurations.create("changelogMarkdown") {
    isCanBeConsumed = true
    isCanBeResolved = false
    isVisible = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named<Usage>("changelogMarkdown"))
    }
    outgoing.artifact(makeMarkdownChangelog.map { it.outputs.files.singleFile }) {
        type = "markdown"
    }
}
