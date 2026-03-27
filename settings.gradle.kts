rootProject.name = "Natural20"

plugins {
    // See documentation on https://scaffoldit.dev
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("release")
    // Update 4 (2026-03-26). Devtools 0.2.14 may emit version mismatch warnings.
    useVersion("2026.03.26-89796e57b")

    repositories {
    }

    dependencies {
    }

    manifest {
        Group = "chonbosmods"
        Name = "Natural20"
        Main = "com.chonbosmods.Natural20"
    }
}