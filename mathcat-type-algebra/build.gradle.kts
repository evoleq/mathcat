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
        namespace = "org.evoleq.mathcat.types"
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
