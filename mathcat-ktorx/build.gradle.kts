plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()
    js(IR) { nodejs() }
    wasmJs { nodejs() }
    android {
        namespace = "org.evoleq.mathcat.ktorx"
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
            api(project(":mathcat-api"))
            api("io.ktor:ktor-client-core:3.4.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.4.1")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
        jvmMain.dependencies {
            api(project(":mathcat-result"))
            api(project(":mathcat-context"))
            api(project(":mathcat-transform"))
            api("io.ktor:ktor-server-core:3.4.1")
        }
        jvmTest.dependencies {
            implementation("io.ktor:ktor-server-test-host:3.4.1")
        }
    }
}
