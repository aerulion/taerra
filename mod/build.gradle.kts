plugins {
    id("taerra.java-conventions")
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = "taerra-fabric"
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
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
