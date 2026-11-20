plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

kotlin {
    jvm()

    js(IR) {
        nodejs()
    }

    wasmJs {
        nodejs()
    }

    android {
        namespace = "evoleq.toolkit.core"
        compileSdk = 36
        minSdk = 24

        // Android unit tests for the same portable commonTest suite.
        withHostTestBuilder {}.configure {}
    }

    linuxX64()
    mingwX64()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":toolkit-type-algebra"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
