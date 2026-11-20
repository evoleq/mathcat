plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":toolkit-serialization"))
    implementation(project(":toolkit-ktorx"))
    implementation(project(":toolkit-middleware"))
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":toolkit-serialization-ksp"))

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit4)
}

ksp {
    arg("toolkit.serializers.package", "evoleq.toolkit.ksp.fixture")
    arg("toolkit.serializers.name", "GeneratedFeatureSerializers")
}
