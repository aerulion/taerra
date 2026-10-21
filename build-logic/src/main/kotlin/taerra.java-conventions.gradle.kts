import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    id("com.diffplug.spotless")
}

val libs = versionCatalogs.named("libs")

group = "net.aerulion"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.glaremasters.me/repository/towny/") {
        mavenContent { includeGroup("com.palmergames.bukkit.towny") }
    }
    mavenCentral()
}

java {
    toolchain.languageVersion =
        JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
}

spotless {
    java {
        googleJavaFormat(libs.findLibrary("google-java-format").get().get().version)
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = libs.findVersion("java").get().requiredVersion.toInt()
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation", "-parameters"))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = Charsets.UTF_8.name()
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "2G"
    jvmArgs("-Dcom.mojang.eula.agree=true", "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// The repository is private, so the jars are where users find the license.
tasks.named<Jar>("jar") { from(rootDir.resolve("LICENSE.md")) }

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
