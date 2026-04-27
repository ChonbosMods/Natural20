plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.hytale.com/release") }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.03.26-89796e57b")
    implementation("org.ow2.asm:asm:9.7.1")
}

// Shared version with the main Natural20 jar. This is a separate root project (its own
// settings.gradle.kts), so we read the parent repo's gradle.properties directly rather
// than rely on multi-project version inheritance.
val sharedVersion = file("../../gradle.properties").readLines()
    .firstOrNull { it.startsWith("version=") }
    ?.substringAfter("=")
    ?.trim()
    ?: error("version= line missing from ../../gradle.properties")

version = sharedVersion

tasks.jar {
    archiveBaseName.set("Natural20-Patches")
    archiveVersion.set(sharedVersion)

    // Bundle ASM into the JAR (fat jar) since early plugins use an isolated classloader
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Belt-and-suspenders: early plugins are discovered via META-INF/services, not
    // manifest.json. If something on the runtime classpath ever shipped one, exclude it
    // so the patches jar stays cleanly identifiable.
    exclude("manifest.json")
}
