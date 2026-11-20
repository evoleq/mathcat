import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// Preserve existing JitPack and local-development coordinates.
// Central is explicit and uses a distinct, domain-verified group.
val isJitPack = providers.environmentVariable("JITPACK")
    .map { it.equals("true", ignoreCase = true) }.orElse(false).get()
val isCentral = providers.gradleProperty("centralPublishing")
    .orElse(providers.environmentVariable("CENTRAL_PUBLISHING"))
    .map { it.equals("true", ignoreCase = true) }.orElse(false).get()
check(!(isCentral && isJitPack)) { "Maven Central and JitPack publication modes are mutually exclusive" }

val requestedVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("RELEASE_VERSION"))
val artifactVersion = requestedVersion.orElse(
    if (isJitPack) providers.environmentVariable("VERSION")
    else providers.provider { "0.0.0-SNAPSHOT" }
)
if (isJitPack && !artifactVersion.isPresent) {
    error("JITPACK=true requires VERSION (Git tag) or releaseVersion")
}
if (isCentral) {
    check(requestedVersion.isPresent) { "Central publishing requires releaseVersion/RELEASE_VERSION" }
    check(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(artifactVersion.get())) {
        "Central requires a stable numeric SemVer (without v prefix or -SNAPSHOT)"
    }
}

allprojects {
    group = when {
        isCentral -> "org.evoleq.toolkit"
        isJitPack -> "com.github.evoleq.mathcat"
        else -> "org.evoleq"
    }
    version = artifactVersion.get()
}

// Configure only already-publishable modules. KSP fixture remains test-only.
// KMP publications are created lazily by Kotlin; configureEach covers all targets.
subprojects {
    if (name != "toolkit-serialization-ksp-fixture" && isCentral) {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            extensions.configure(JavaPluginExtension::class.java) {
                withSourcesJar()
            }
        }
        pluginManager.withPlugin("maven-publish") {
            val licenseName = providers.gradleProperty("centralLicenseName")
                .orElse(providers.environmentVariable("CENTRAL_LICENSE_NAME")).orNull
            val licenseUrl = providers.gradleProperty("centralLicenseUrl")
                .orElse(providers.environmentVariable("CENTRAL_LICENSE_URL")).orNull
            check(!licenseName.isNullOrBlank() && !licenseUrl.isNullOrBlank()) {
                "Choose and configure a real distribution license before Central publication"
            }
            check(licenseUrl.startsWith("https://")) { "Central license URL must use HTTPS" }

            val publishing = extensions.getByType(PublishingExtension::class.java)
            publishing.repositories.maven {
                name = "CentralStaging"
                url = uri(rootProject.layout.buildDirectory.dir("central-staging").get().asFile)
            }
            publishing.publications.withType(MavenPublication::class.java).configureEach {
                // Each KMP target has its own signing task. They MUST NOT sign
                // the same physical Javadoc JAR (Gradle 9 task output collision).
                val publicationId = name.replaceFirstChar { it.uppercaseChar() }
                val docsJar = tasks.register<Jar>("centralJavadoc" + publicationId + "Jar") {
                    archiveBaseName.set(project.name + "-" + publicationId)
                    archiveClassifier.set("javadoc")
                    // Placeholder API docs until a KDoc pipeline is introduced.
                    from(rootProject.file("ReadMe.md"))
                }
                artifact(docsJar)
                pom {
                    name.set("Evoleq ToolKit - " + project.name)
                    description.set("Composable software architecture library: " + project.name)
                    url.set("https://github.com/evoleq/toolkit")
                    licenses {
                        license {
                            name.set(licenseName)
                            url.set(licenseUrl)
                        }
                    }
                    developers {
                        developer {
                            id.set("evoleq")
                            name.set("Evoleq ToolKit contributors")
                            url.set("https://github.com/evoleq")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/evoleq/toolkit.git")
                        developerConnection.set("scm:git:ssh://git@github.com/evoleq/toolkit.git")
                        url.set("https://github.com/evoleq/toolkit")
                    }
                }
            }

            val signingKey = providers.environmentVariable("CENTRAL_SIGNING_KEY").orNull
            check(!signingKey.isNullOrBlank()) { "CENTRAL_SIGNING_KEY must be supplied for Central staging" }
            pluginManager.apply("signing")
            extensions.configure(SigningExtension::class.java) {
                useInMemoryPgpKeys(
                    signingKey,
                    providers.environmentVariable("CENTRAL_SIGNING_PASSWORD").orNull
                )
                sign(publishing.publications)
            }
        }
    }
}

tasks.register<Exec>("verifyReleaseCompatibility") {
    group = "verification"
    description = "Verify the module compatibility manifest against Gradle declarations."
    commandLine("python3", "scripts/release.py", "validate")
}

tasks.register<Exec>("planRelease") {
    group = "release"
    description = "Plan a SemVer source release without creating tags or publishing anything."
    dependsOn("verifyReleaseCompatibility")
    doNotTrackState("Git tags and commit history are not Gradle inputs")
    commandLine(
        "python3", "scripts/release.py", "plan", "--output",
        layout.buildDirectory.file("release/plan.json").get().asFile.absolutePath,
    )
}
