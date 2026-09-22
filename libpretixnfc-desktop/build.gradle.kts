// The Kotlin plugin is requested without a version so that this module can be included as a
// subproject of a host build, which then supplies the plugin.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)

    compilerOptions {
        // javax.smartcardio is not part of the default module set
        freeCompilerArgs.add("-Xadd-modules=java.smartcardio")
    }

    jvm()

    sourceSets {
        jvmMain.dependencies {
            api(project(":libpretixnfc"))
        }
        jvmTest.dependencies {
            implementation(nfcCoreLibs.junit)
        }
    }
}
