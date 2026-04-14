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

tasks.jar {
    archiveBaseName.set("nat20-patches")
    archiveVersion.set("1.0.0")

    // Bundle ASM into the JAR (fat jar) since early plugins use an isolated classloader
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
