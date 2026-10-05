rootProject.name = "Quazio"

pluginManagement {
    includeBuild("build-logic")
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":core:model")
include(":core:db")
include(":core:accounts")
include(":core:lock")
include(":engine:quickinput")
include(":feature:finance")
include(":feature:calendar")
include(":core:ui")
include(":app:android")
include(":app:desktop")
