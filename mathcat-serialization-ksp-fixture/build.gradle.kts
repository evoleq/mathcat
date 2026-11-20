plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":mathcat-serialization"))
    implementation(project(":mathcat-ktorx"))
    implementation(project(":mathcat-middleware"))
    implementation(libs.kotlinx.serialization.json)
    ksp(project(":mathcat-serialization-ksp"))

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.junit4)
}

ksp {
    arg("mathcat.serializers.package", "org.evoleq.mathcat.ksp.fixture")
    arg("mathcat.serializers.name", "GeneratedFeatureSerializers")
}
