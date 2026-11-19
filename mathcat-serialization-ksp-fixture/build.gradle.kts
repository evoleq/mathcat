plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":mathcat-serialization"))
    implementation(project(":mathcat-ktorx"))
    implementation(project(":mathcat-middleware"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    ksp(project(":mathcat-serialization-ksp"))

    testImplementation(kotlin("test-junit"))
    testImplementation("io.ktor:ktor-client-mock:3.4.1")
    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("mathcat.serializers.package", "org.evoleq.mathcat.ksp.fixture")
    arg("mathcat.serializers.name", "GeneratedFeatureSerializers")
}
