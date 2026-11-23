// ============================================================================
// Build/plugin-classpath security constraints.
// The Gradle plugins on the build classpath (AGP, google-services, etc.) pull
// transitive libraries with open Dependabot alerts. Constraints here force the
// first patched version of each (see the GHSAs) without changing plugin
// behavior otherwise. Remove a constraint only when every plugin on the
// classpath already brings the patched version or newer.
// ============================================================================
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        constraints {
            classpath("org.bouncycastle:bcprov-jdk18on:1.85") {
                because("GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26, GHSA-574f-3g2m-x479, GHSA-p93r-85wp-75v3, GHSA-c3fc-8qff-9hwx")
            }
            classpath("org.bouncycastle:bcpkix-jdk18on:1.85") {
                because("GHSA-wg6q-6289-32hp")
            }
            classpath("org.bouncycastle:bcpg-jdk18on:1.85") {
                because("GHSA-cj8j-37rh-8475")
            }
            classpath("org.bitbucket.b_c:jose4j:0.9.6") {
                because("GHSA-3677-xxcr-wjqv")
            }
            classpath("org.jdom:jdom2:2.0.6.1") {
                because("GHSA-2363-cqg2-863c")
            }
            classpath("org.apache.httpcomponents:httpclient:4.5.14") {
                because("GHSA-7r82-7xv7-xcpj")
            }
            classpath("org.apache.commons:commons-lang3:3.18.0") {
                because("GHSA-j288-q9x7-2f5v")
            }
            classpath("com.google.guava:guava:33.8.0-jre") {
                because("GHSA-xxph-c9ww-hj94")
            }
            classpath("io.opentelemetry:opentelemetry-api:1.67.0") {
                because("GHSA-ccrj-gw8r-854m, GHSA-rcgg-9c38-7xpx")
            }
        }
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.google.firebase.crashlytics) apply false
}

// Lint and annotation processors resolve independently of buildscript and app runtimes.
// Keep these constraints scoped to the tool graphs that actually contain the advisories.
subprojects {
    val targetProject = this
    configurations.configureEach {
        val configurationName = name
        val patchedToolDependencies = when {
            configurationName == "androidLintTool" -> listOf(
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "com.google.guava:guava:33.8.0-jre",
                "org.apache.commons:commons-lang3:3.18.0",
                "org.apache.httpcomponents:httpclient:4.5.14"
            )
            (configurationName.startsWith("_agp_internal_") && configurationName.endsWith("_kspClasspath")) ||
                configurationName.startsWith("hiltAnnotationProcessor") ||
                (configurationName.startsWith("ksp") && configurationName.endsWith("KotlinProcessorClasspath")) ->
                listOf("com.google.guava:guava:33.8.0-jre")
            else -> emptyList()
        }
        patchedToolDependencies.forEach { coordinate ->
            targetProject.dependencies.constraints.add(configurationName, coordinate) {
                because("Patch verified Dependabot paths in lint and Hilt/KSP tool configurations")
            }
        }
    }
}
