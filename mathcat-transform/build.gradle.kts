plugins {
    `maven-publish`
    kotlin("jvm")
}

dependencies {
    api(project(":mathcat-result"))
    api(project(":mathcat-context"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
