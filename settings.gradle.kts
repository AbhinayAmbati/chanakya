// Root Gradle settings: declares plugin/dependency repositories and the module graph.
pluginManagement {
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
    // Modules must not declare their own repositories; everything resolves from here.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Chanakya"

// ---- Application ---------------------------------------------------------
include(":app")

// ---- Core modules --------------------------------------------------------
include(":core:designsystem")
include(":core:data")
include(":core:vpn")
