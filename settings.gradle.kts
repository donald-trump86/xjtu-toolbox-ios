pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            content { includeGroupByRegex("androidx.*") }
        }
        mavenCentral()
    }
}

rootProject.name = "XJTUToolBox"

include(":iosShared")
include(":shared:model")
include(":shared:navigation")
include(":shared:designsystem")
include(":shared:campus")
include(":feature:account")
include(":feature:home")
include(":feature:schedule")
include(":feature:score")
include(":feature:settings")
