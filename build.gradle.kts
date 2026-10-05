plugins {
    base
    id("taerra.java-conventions") apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.paperweight.userdev) apply false
    alias(libs.plugins.fabric.loom) apply false
}

repositories { mavenCentral() }

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "site/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    json {
        target("plugin/src/main/resources/web/**/*.json", "mod/src/main/resources/**/*.json")
        jackson().version(libs.versions.jackson.get()).feature("INDENT_OUTPUT", true)
        endWithNewline()
    }
}

tasks {
    register("installGitHooks") {
        group = "taerra"
        description =
            "Points git at the committed hooks, so commit messages are checked locally and in the IDE."
        val gitDirectory = layout.projectDirectory.dir(".git").asFile
        val projectDirectory = layout.projectDirectory.asFile
        val hooksPath = ".githooks"
        onlyIf { gitDirectory.exists() }
        doLast {
            fun git(vararg arguments: String): Pair<Int, String> {
                val process =
                    ProcessBuilder(listOf("git") + arguments)
                        .directory(projectDirectory)
                        .redirectErrorStream(true)
                        .start()
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                return process.waitFor() to output
            }
            if (git("config", "--get", "core.hooksPath").second == hooksPath) {
                return@doLast
            }
            val (exitCode, output) = git("config", "core.hooksPath", hooksPath)
            if (exitCode != 0) {
                logger.warn(
                    "Could not point git at $hooksPath, commit messages will not be checked locally: $output"
                )
                return@doLast
            }
            logger.lifecycle("Git hooks installed from $hooksPath.")
        }
    }

    register("preCommit") {
        group = "taerra"
        description = "Reformats everything a commit carries, then builds and tests it."
        dependsOn(
            listOf("spotlessApply", "build") +
                subprojects.flatMap { listOf("${it.path}:spotlessApply", "${it.path}:build") }
        )
    }

    register("runDev") {
        group = "taerra"
        description = "Starts the dev server and a client that joins it."
        dependsOn(":plugin:runDevBundleServer", ":mod:runClient")
    }

    register("printVersion") {
        description = "Prints the version this build publishes under."
        group = "help"
        val version = project.version.toString()
        doLast { println(version) }
    }
}

val installGitHooks = tasks.named("installGitHooks")

subprojects { tasks.withType<JavaCompile>().configureEach { dependsOn(installGitHooks) } }
