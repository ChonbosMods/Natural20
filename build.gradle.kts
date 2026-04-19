/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

dependencies {
    // Any external dependency you also want to include
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // HytaleLogger refuses to initialize unless java.util.logging uses its custom
    // log manager. Set the property at JVM startup so tests that load plugin classes
    // with a `HytaleLogger.get(...)` static field don't fail on class init.
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
}

// Add --accept-early-plugins to the dev server launch args
// Required for the nat20-patches early plugin (bundles all Nat20 bytecode patches:
// player model scale, durability/fortified/indestructible/repair-penalty, block face stash,
// workbench exclusion)
tasks.matching { it.name == "runServer" }.configureEach {
    if (this is JavaExec) {
        args("--accept-early-plugins")
    }

    // Pre-seed AuthCredentialStore so player login persists across devServer runs
    doFirst {
        val configFile = file("devserver/config.json")
        if (configFile.exists()) {
            val text = configFile.readText()
            if (!text.contains("\"AuthCredentialStore\"")) {
                val patched = text.trimEnd().removeSuffix("}") +
                    ",\n  \"AuthCredentialStore\": {\n    \"Type\": \"Encrypted\",\n    \"Path\": \"auth.enc\"\n  }\n}"
                configFile.writeText(patched)
                logger.lifecycle("Injected AuthCredentialStore into devserver/config.json")
            }
        }
    }
}