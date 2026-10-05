import java.io.IOException
import java.net.Socket
import java.time.Duration

plugins {
    id("taerra.java-conventions")
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = "taerra-fabric"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    archiveVersion = project.extra["jarVersion"] as String
}

repositories { maven("https://maven.terraformersmc.com/releases/") }

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    compileOnly(libs.modmenu)
    localRuntime(libs.modmenu)
}

tasks.processResources {
    val version = project.version.toString()
    val minecraft = libs.versions.minecraft.get()
    inputs.property("version", version)
    inputs.property("minecraft", minecraft)
    filesMatching("fabric.mod.json") {
        expand("version" to version, "minecraft" to minecraft)
    }
}

if (gradle.startParameter.taskNames.any { it.removePrefix(":") == "runDev" }) {
    tasks.named<JavaExec>("runClient") {
        args("--quickPlayMultiplayer", "localhost:25565", "--username", "TaerraDev")
        doFirst {
            val deadline = System.nanoTime() + Duration.ofMinutes(5).toNanos()
            while (System.nanoTime() < deadline) {
                try {
                    Socket("localhost", 25565).close()
                    return@doFirst
                } catch (_: IOException) {
                    Thread.sleep(1000)
                }
            }
        }
    }
}
