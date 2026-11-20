plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    `maven-publish`
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { nodejs() }
    android {
        namespace = "org.evoleq.mathcat.routing"
        compileSdk = 36
        minSdk = 24
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
            api(project(":mathcat-type-algebra"))
            api(project(":mathcat-evolution"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
