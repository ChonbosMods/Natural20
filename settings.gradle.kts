rootProject.name = "Natural20"

plugins {
    // See documentation on https://scaffoldit.dev
    id("dev.scaffoldit") version "0.2.+"
}

hytale {
    usePatchline("release")
    // Pinned to match ScaffoldIt devtools 0.2.14 (targets 2026.02.18-f3b8fff95).
    // Server 2026.02.19 causes version mismatch that breaks all entity collision/interaction.
    // Unpin when ScaffoldIt 0.3.x ships with flexible version checking (issue #11).
    useVersion("2026.02.18-f3b8fff95")

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