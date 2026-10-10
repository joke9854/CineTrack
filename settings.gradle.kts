pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // PrismalAGSL (liquid glass) is published only on JitPack.
        maven("https://jitpack.io") { content { includeGroupByRegex("com[.]github[.]styropyr0.*") } }
    }
}

rootProject.name = "CineTrack"
include(":app")
include(":baselineprofile")
