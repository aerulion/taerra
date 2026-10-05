plugins {
    id("taerra.java-conventions")
    `java-library`
    `maven-publish`
    signing
}

base {
    archivesName = "taerra-api"
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.jetbrains.annotations)
}

java {
    withSourcesJar()
    withJavadocJar()
}

configurations.consumable("pluginBundle") {
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("taerra-plugin-bundle"))
    }
    outgoing {
        artifact(tasks.compileJava.flatMap { it.destinationDirectory })
        artifact(tasks.processResources.map { it.destinationDir })
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        links(
            "https://jd.papermc.io/paper/${libs.versions.paper.get().substringBefore(".build")}/",
            "https://jd.papermc.io/adventure/${libs.versions.adventure.get()}/",
        )
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("quiet", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "taerra-api"
            from(components["java"])
            pom {
                name = "TAERRA API"
                description = "API for adding markers and layers to the TAERRA web map."
                url = "https://taerra.dev"
                licenses {
                    license {
                        name = "TAERRA License"
                        url = "https://assets.corpium.net/taerra/license.txt"
                    }
                }
                developers {
                    developer {
                        id = "aerulion"
                        name = "aerulion"
                    }
                }
                scm { url = "https://taerra.dev" }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aerulion/taerra")
            credentials(PasswordCredentials::class)
        }
        maven {
            name = "Central"
            url = uri(layout.buildDirectory.dir("central"))
        }
    }
}

val signingKey = providers.gradleProperty("signingKey").orNull

if (signingKey != null) {
    signing {
        useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
        sign(publishing.publications["maven"])
    }
}
