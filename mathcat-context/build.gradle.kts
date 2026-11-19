plugins {
    `maven-publish`
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
}
