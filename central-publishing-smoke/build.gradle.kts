plugins {
    kotlin("jvm") version "2.3.20"
}
val toolkitVersion = providers.gradleProperty("toolkitVersion").get()
dependencies {
    implementation("org.evoleq.toolkit:toolkit-actions:" + toolkitVersion)
    implementation("org.evoleq.toolkit:toolkit-api:" + toolkitVersion)
    implementation("org.evoleq.toolkit:toolkit-ktorx:" + toolkitVersion)
    implementation("org.evoleq.toolkit:toolkit-evolution-runtime:" + toolkitVersion)
    implementation("org.evoleq.toolkit:toolkit-context:" + toolkitVersion)
    implementation("org.evoleq.toolkit:toolkit-serialization-ksp:" + toolkitVersion)
}
