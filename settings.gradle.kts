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

// Modules below are declared as they are implemented, following the
// roadmap order (see docs/ROADMAP.md, section 31): raw -> calibration ->
// image -> astrometry -> astronomy -> detection -> catalog -> fits ->
// pipeline -> ui.
