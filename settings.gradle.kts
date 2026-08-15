pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "msp-app"
include(":app")
include(":core:appgate")
include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:network")
include(":core:printing")
include(":core:settings")
include(":core:telemetry")
include(":core:upload")
include(":core:testing")
include(":build-tools:detekt-rules")
include(":feature:collectionReport")
include(":feature:configuracion")