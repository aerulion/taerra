pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Taerra"

include("api", "plugin", "mod")

fun git(vararg arguments: String): String? {
    val output = providers.exec {
        commandLine(listOf("git") + arguments)
        isIgnoreExitValue = true
    }
    return if (output.result.get().exitValue != 0) null
    else output.standardOutput.asText.get().trim()
}

val buildNumber =
    providers.environmentVariable("TAERRA_BUILD_NUMBER").orNull?.takeIf(String::isNotBlank)
val pullRequestNumber =
    providers.environmentVariable("TAERRA_PR_NUMBER").orNull?.takeIf(String::isNotBlank)
val commitHash = git("rev-parse", "--short=7", "HEAD") ?: "unknown"
val workingTreeDirty = !git("status", "--porcelain").isNullOrEmpty()

// Only a release starts with a number, so PR and local builds always sort below one. The package
// cleanup workflows match these shapes.
val taerraVersion =
    when {
        pullRequestNumber != null -> "pr$pullRequestNumber-${buildNumber ?: "0"}-$commitHash"
        buildNumber != null -> "$buildNumber-$commitHash"
        workingTreeDirty -> "$commitHash-dirty-dev"
        else -> "$commitHash-dev"
    }

gradle.beforeProject { version = taerraVersion }
