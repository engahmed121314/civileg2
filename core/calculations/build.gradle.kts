plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.civileg.core.calculations"
    compileSdk = 35

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
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.20")
}
