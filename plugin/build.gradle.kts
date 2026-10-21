import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.UUID
import net.minecrell.pluginyml.paper.PaperPluginDescription

plugins {
    id("taerra.java-conventions")
    alias(libs.plugins.paperweight.userdev)
    alias(libs.plugins.plugin.yml.paper)
    alias(libs.plugins.run.paper)
}

base {
    archivesName = "taerra-paper"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveVersion = project.extra["jarVersion"] as String
}

val runtimeLibraries =
    listOf<Provider<MinimalExternalModuleDependency>>(
        libs.caffeine,
        libs.javalin.core,
        libs.jackson.databind,
    )

val bundled =
    configurations.create("bundled") {
        isCanBeConsumed = false
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named("taerra-plugin-bundle"))
        }
    }

dependencies {
    paperweight.paperDevBundle(libs.versions.paper.get())
    implementation(project(":api"))
    bundled(project(":api"))
    compileOnly(libs.jetbrains.annotations)
    testImplementation(libs.junit.launcher)
    runtimeLibraries.forEach { implementation(it) }

    compileOnly(libs.worldguard.core) { isTransitive = false }
    compileOnly(libs.worldguard.libs) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.chunky.common) { isTransitive = false }
    compileOnly(libs.chunkyborder.common) { isTransitive = false }
    compileOnly(libs.luckperms.api)
    compileOnly(libs.towny) { isTransitive = false }
    compileOnly(libs.griefprevention) { isTransitive = false }
    compileOnly(libs.plotsquared.core) { isTransitive = false }
    compileOnly(libs.guice) { isTransitive = false }
}

val writeLibraries =
    tasks.register<WriteProperties>("writeLibraries") {
        destinationFile =
            layout.buildDirectory.file("generated/libraries/taerra-libraries.properties")
        property(
            "libraries",
            runtimeLibraries
                .map { library -> library.map { "${it.module}:${it.version}" }.get() }
                .joinToString(","),
        )
    }

tasks {
    processResources {
        from(writeLibraries)
    }

    jar {
        from(bundled)
    }

    runDevBundleServer {
        runDirectory = rootProject.layout.projectDirectory.dir("run")
        args("-wworld-${libs.versions.paper.get().substringBefore(".build").replace(".", "-")}")
        val properties = rootProject.layout.projectDirectory.file("run/server.properties").asFile
        doFirst {
            val lines =
                if (properties.exists())
                    properties.readLines().filterNot { it.startsWith("level-seed=") }
                else emptyList()
            properties.parentFile.mkdirs()
            properties.writeText(
                (lines + "level-seed=7212011082581230045").joinToString("\n", postfix = "\n")
            )
        }
        if (gradle.startParameter.taskNames.any { it.removePrefix(":") == "runDev" }) {
            args("-o", "false")
            val ops = rootProject.layout.projectDirectory.file("run/ops.json").asFile
            doFirst {
                val player = "TaerraDev"
                val uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$player".toByteArray()).toString()
                @Suppress("UNCHECKED_CAST")
                val entries =
                    if (ops.exists()) JsonSlurper().parse(ops) as List<Map<String, Any>>
                    else emptyList()
                if (entries.none { it["uuid"] == uuid }) {
                    val op =
                        mapOf(
                            "uuid" to uuid,
                            "name" to player,
                            "level" to 4,
                            "bypassesPlayerLimit" to false,
                        )
                    ops.parentFile.mkdirs()
                    ops.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(entries + op)))
                }
            }
        }
        downloadPlugins {
            modrinth("voidworld", "AhbiFdlx")
            modrinth("chunky", "MdY6JATr")
            modrinth("chunkyborder", "asaBBItO")
            url(
                "https://ci.enginehub.org/repository/download/bt10/30292:id/worldedit-bukkit-7.4.6-SNAPSHOT-dist.jar?branch=version/7.4.x&guest=1"
            )
            url(
                "https://ci.enginehub.org/repository/download/bt11/30231:id/worldguard-bukkit-7.0.20-SNAPSHOT-dist.jar?branch=version/7.0.x&guest=1"
            )
            url("https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-5.5.87.jar")
        }
    }
}

paper {
    name = "Taerra"
    main = "net.aerulion.taerra.bootstrap.Taerra"
    loader = "net.aerulion.taerra.bootstrap.TaerraLoader"
    version = project.version.toString()
    author = "aerulion"
    description = "A live isometric and top-down web map of your worlds."
    website = "https://taerra.dev"
    apiVersion = libs.versions.paper.get().substringBefore(".build")
    foliaSupported = false
    serverDependencies {
        register("WorldEdit") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("WorldGuard") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("Chunky") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("ChunkyBorder") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("LuckPerms") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("Towny") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("GriefPrevention") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
        register("PlotSquared") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
            joinClasspath = true
        }
    }
}
