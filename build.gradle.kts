plugins {
    base
    id("taerra.java-conventions") apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.paperweight.userdev) apply false
    alias(libs.plugins.fabric.loom) apply false
}

repositories { mavenCentral() }

object Docker {
    fun run(vararg arguments: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf("docker") + arguments).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        return process.waitFor() to output
    }

    fun require(vararg arguments: String): String {
        val (exitCode, output) = run(*arguments)
        if (exitCode != 0) {
            throw GradleException("docker ${arguments.joinToString(" ")} failed:\n$output")
        }
        return output
    }
}

// container, image, host port, client binary, environment prefix
val devDatabases =
    listOf(
        listOf("taerra-dev-mariadb", "mariadb:11.4", "3316", "mariadb", "MARIADB"),
        listOf("taerra-dev-mysql", "mysql:8.4", "3317", "mysql", "MYSQL"),
    )

spotless {
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "site/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    format("webMap") {
        target(
            "plugin/src/main/resources/web/js/**/*.js",
            "plugin/src/test/js/**/*.mjs",
            "plugin/eslint.config.js",
        )
        prettier(libs.versions.prettier.get())
            .config(
                mapOf(
                    "printWidth" to 120,
                    "tabWidth" to 4,
                    "singleQuote" to true,
                    "bracketSpacing" to false,
                    "trailingComma" to "none",
                )
            )
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

    register("startDevDatabases") {
        group = "taerra"
        description =
            "Starts a MariaDB and a MySQL in Docker for switching the dev server's tile storage."
        val databases = devDatabases
        doLast {
            if (Docker.run("version").first != 0) {
                throw GradleException("Docker is not running, so the dev databases can't start.")
            }
            for ((container, image, port, _, prefix) in databases) {
                val (exitCode, running) =
                    Docker.run("inspect", "-f", "{{.State.Running}}", container)
                when {
                    exitCode != 0 ->
                        Docker.require(
                            "run",
                            "--detach",
                            "--name",
                            container,
                            "--publish",
                            "$port:3306",
                            "--env",
                            "${prefix}_DATABASE=taerra",
                            "--env",
                            "${prefix}_USER=taerra",
                            "--env",
                            "${prefix}_PASSWORD=taerra",
                            "--env",
                            "${prefix}_ROOT_PASSWORD=taerra",
                            image,
                        )
                    running != "true" -> Docker.require("start", container)
                }
            }
            for ((container, image, port, client) in databases) {
                val deadline = System.currentTimeMillis() + 120_000
                while (
                    Docker.run(
                            "exec",
                            container,
                            client,
                            "--host=127.0.0.1",
                            "--user=taerra",
                            "--password=taerra",
                            "--execute=select 1",
                            "taerra",
                        )
                        .first != 0
                ) {
                    if (System.currentTimeMillis() > deadline) {
                        throw GradleException(
                            "$container didn't come up in two minutes, see docker logs $container"
                        )
                    }
                    Thread.sleep(1000)
                }
                logger.lifecycle("${image.substringBefore(':')} is up on localhost:$port")
            }
            logger.lifecycle(
                "Under Tile storage in the settings: host localhost, port 3316 for MariaDB or " +
                    "3317 for MySQL, database, user and password taerra."
            )
        }
    }

    register("stopDevDatabases") {
        group = "taerra"
        description = "Stops the dev databases, keeping their tiles for the next start."
        val containers = devDatabases.map { it[0] }
        doLast { containers.forEach { Docker.run("stop", it) } }
    }

    register("removeDevDatabases") {
        group = "taerra"
        description = "Removes the dev databases and every tile in them."
        val containers = devDatabases.map { it[0] }
        doLast { containers.forEach { Docker.run("rm", "--force", "--volumes", it) } }
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
