pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        maven { url = uri("https://www.jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://www.jitpack.io") }
    }
}

rootProject.name = "location"
include(":app")
include(":NewBlackbox:Bcore")
include(":NewBlackbox:black-reflection")
include(":NewBlackbox:compiler")
// Note: ":NewBlackbox:app" (BlackBox's standalone demo launcher) is intentionally
// not included — the main app only depends on :NewBlackbox:Bcore.
 
