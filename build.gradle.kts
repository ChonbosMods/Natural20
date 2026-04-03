/**
 * NOTE: This is entirely optional and basics can be done in `settings.gradle.kts`
 */

repositories {
    // Any external repositories besides: MavenLocal, MavenCentral, HytaleMaven, and CurseMaven
}

dependencies {
    // Any external dependency you also want to include
}

// Add --accept-early-plugins to the dev server launch args
// Required for the player-model-fix early plugin (bytecode patch for scale=-1.0f crash)
tasks.matching { it.name == "runServer" }.configureEach {
    if (this is JavaExec) {
        args("--accept-early-plugins")
    }
}