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

// ScaffoldIt's plugin still pins devtools 0.2.14, whose manifest requires a date-string
// Universe version (>=2026.02.18) and so refuses to load under U5's SemVer (0.5.3). Force
// devtools 0.2.16, which declares its dependency as Hytale:Universe >= 0.5.2.
configurations.configureEach {
    resolutionStrategy.force("dev.scaffoldit:devtools:0.2.16")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // HytaleLogger refuses to initialize unless java.util.logging uses its custom
    // log manager. Set the property at JVM startup so tests that load plugin classes
    // with a `HytaleLogger.get(...)` static field don't fail on class init.
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
}

// CurseForge listing expects Natural20-<version>.jar so the user-facing artifact name
// matches the project name. Version comes from gradle.properties.
tasks.named<Jar>("jar").configure {
    archiveFileName.set("Natural20-${project.version}.jar")
}

// ── CurseForge bundle ─────────────────────────────────────────────────────────
// Single attached file per release: Natural20-Bundle-<version>.zip. Layout:
//   README.md                                  (generated install excerpt)
//   mods/Natural20-<version>.jar
//   earlyplugins/Natural20-Patches-<version>.jar
// Folders are intentionally lowercase: EarlyPluginLoader hardcodes
// Path.of("earlyplugins") for its auto-scan default; capitalizing breaks Linux
// self-hosted servers. Linux singleplayer users do a manual jar copy instead;
// see docs/plans/2026-04-26-curseforge-one-listing-bundle-design.md.

// tools/nat20-patches/ is a separate root project, not a Gradle subproject of this
// build (own settings.gradle.kts). Invoke its jar task via Exec so bundleZip is a
// one-command produce-everything entrypoint.
val buildPatchesJar = tasks.register<Exec>("buildPatchesJar") {
    description = "Builds the Natural20-Patches early plugin jar (separate root project)."
    group = "build"
    workingDir = file("tools/nat20-patches")
    commandLine("../../gradlew", "jar")
    // Tell Gradle what we expect to land on disk so up-to-date checking has a hook.
    outputs.file(layout.projectDirectory
        .dir("tools/nat20-patches/build/libs")
        .file("Natural20-Patches-${project.version}.jar"))
}

val bundleReadme = tasks.register("bundleReadme") {
    description = "Generates the install README bundled at the zip root."
    group = "distribution"
    val outFile = layout.buildDirectory.file("bundle/README.md")
    outputs.file(outFile)
    val versionStr = project.version.toString()
    doLast {
        val target = outFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText("""
            # Natural 20 v$versionStr — install

            This bundle contains both halves of Natural 20. **Self-hosted servers**:
            extract this zip at your server root. Two folders will populate:

              mods/Natural20-$versionStr.jar
              earlyplugins/Natural20-Patches-$versionStr.jar

            Then add `--accept-early-plugins` to your server start command and restart.

            **Singleplayer**: extract somewhere temporary and manually copy
            `mods/Natural20-$versionStr.jar` into your launcher's `UserData/Mods/`
            folder, and `earlyplugins/Natural20-Patches-$versionStr.jar` into
            `UserData/EarlyPlugins/`. The case mismatch with the launcher's
            capitalized folder names is intentional: the Hytale engine requires
            lowercase `earlyplugins/` for self-hosted servers, so the bundle
            uses lowercase throughout. Singleplayer users copy two files instead.

            Full install guide: https://nat20mod.com/wiki/getting-started/installation/
        """.trimIndent())
    }
}

tasks.register<Zip>("bundleZip") {
    description = "Assembles the published CurseForge artifact bundling main + patches jars."
    group = "distribution"
    dependsOn("jar", buildPatchesJar, bundleReadme, "processResources")

    archiveFileName.set("Natural20-Bundle-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    from(tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
        into("mods")
    }
    from(layout.projectDirectory
        .dir("tools/nat20-patches/build/libs")
        .file("Natural20-Patches-${project.version}.jar")) {
        into("earlyplugins")
    }
    from(bundleReadme.map { it.outputs.files.singleFile })
    // CurseForge requires manifest.json at zip root (their reviewer reads metadata
    // without unpacking jars). Sourced from processResources so Version stays in
    // sync with gradle.properties via ScaffoldIt's generateManifest. The loose copy
    // post-extract is harmless: Hytale's plugin loader only reads manifest.json
    // from inside jars in mods/, never the server root.
    from(layout.buildDirectory.file("resources/main/manifest.json"))
}
// ──────────────────────────────────────────────────────────────────────────────

// Add --accept-early-plugins to the dev server launch args
// Required for the Natural20-Patches early plugin (bundles all Nat20 bytecode patches:
// player model scale, durability/fortified/indestructible/repair-penalty, block face stash,
// workbench exclusion). Source lives in tools/nat20-patches/ (gradle module name retained).
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