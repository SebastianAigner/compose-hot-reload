pluginManagement {
    plugins {
        id("org.jetbrains.compose-hot-reload") version "1.0.0-dev.28.4"
    }

    repositories {
        mavenLocal {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }

        maven(file("../..//build/repo"))
        maven("https://repo.sellmair.io")
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        kotlin("multiplatform") version "2.1.255-SNAPSHOT"
        kotlin("plugin.compose") version "2.1.255-SNAPSHOT"
        id("org.jetbrains.compose") version "1.7.1"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal {
            mavenContent {
                includeGroupByRegex("org.jetbrains.kotlin.*")
            }
        }

        maven(file("../..//build/repo"))
        maven("https://repo.sellmair.io")
        mavenCentral()
        google()
    }
}

include(":app")
include(":widgets")