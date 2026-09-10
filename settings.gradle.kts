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
    }
}

rootProject.name = "astra"

include(":app")
include(":core")
include(":camera")
include(":raw")
include(":calibration")

// Modules below are declared as they are implemented, following the
// roadmap order (see docs/ROADMAP.md, section 31): image -> astrometry ->
// astronomy -> detection -> catalog -> fits -> pipeline -> ui.
