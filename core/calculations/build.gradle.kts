plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Google Services plugin 4.6+ hard-fails when google-services.json is
// missing. This library module has no Firebase project file (the app
// module owns ./app/google-services.json), so apply the plugin only
// when a config file is actually present.
if (file("google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    file("src/main/google-services.json").exists()
) {
    apply(plugin = "com.google.gms.google-services")
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    testImplementation(libs.junit)
}
