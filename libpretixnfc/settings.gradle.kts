pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "2.2.21"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("nfcCoreLibs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "eu.pretix.libpretixnfc"
