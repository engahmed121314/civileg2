plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.civileg.core.calculations"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/main/kotlin", "src/main/java"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.firebase.crashlytics)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    testImplementation(libs.junit)
}
