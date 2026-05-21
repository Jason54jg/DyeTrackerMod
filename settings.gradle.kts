pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.4"
}

stonecutter {
    create(rootProject) {
        versions("1.21.10", "1.21.11")
        vcsVersion = "1.21.10"
    }
}

rootProject.name = "dye-tracker-mod"
