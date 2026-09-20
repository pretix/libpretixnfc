import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

// The Kotlin plugin is requested without a version so that this module can be included as a
// subproject of a host build, which then supplies the plugin. The version used when building
// standalone is pinned in settings.gradle.kts.
plugins {
    jacoco
    id("org.jetbrains.kotlin.multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    val xcframework = XCFramework("LibPretixNfc")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "LibPretixNfc"
            isStatic = true
            binaryOption("bundleId", "eu.pretix.libpretixnfc")
            xcframework.add(this)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(nfcCoreLibs.bcpkix.jdk15to18)
            implementation(nfcCoreLibs.bcprov.jdk15to18)
        }
        commonTest.dependencies {
            // Tracks the Kotlin version of whichever build compiles this module.
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(nfcCoreLibs.junit)
        }
    }
}

jacoco {
    toolVersion = "0.8.7"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("jvmTest")
    executionData.setFrom(layout.buildDirectory.file("jacoco/jvmTest.exec"))
    classDirectories.setFrom(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.named("check") {
    dependsOn("jacocoTestReport")
}
