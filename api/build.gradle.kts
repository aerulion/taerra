plugins {
    id("taerra.java-conventions")
    `java-library`
    `maven-publish`
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
        addBooleanOption("Xdoclint:all,-missing", true)
        addBooleanOption("quiet", true)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "taerra-api"
            from(components["java"])
            pom {
                name = "Taerra API"
                description = "API for adding markers and layers to the Taerra web map."
                url = "https://github.com/aerulion/taerra"
                licenses {
                    license {
                        name = "Taerra Source Code License"
                        url = "https://github.com/aerulion/taerra/blob/main/LICENSE.md"
                    }
                }
                developers {
                    developer {
                        id = "aerulion"
                        name = "aerulion"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/aerulion/taerra.git"
                    developerConnection = "scm:git:ssh://git@github.com/aerulion/taerra.git"
                    url = "https://github.com/aerulion/taerra"
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aerulion/taerra")
            credentials(PasswordCredentials::class)
        }
    }
}
