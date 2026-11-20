plugins {
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":mathcat-result"))
    api(project(":mathcat-context"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit4)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
