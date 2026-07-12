import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

android {
    namespace = "com.civileg.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.civileg.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Hilt KSP settings
        ksp {
            arg("hilt.projectLevelApp", "true")
            arg("dagger.hilt.internal.useAggregatingRootProcessor", "true")
            arg("dagger.fastInit", "enabled")
            arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
        }

        // Developer info for Play Store
        resValue("string", "developer_name", "Eng. Ahmed Magdy")
        resValue("string", "developer_email", "eng.ahmedmagdy121314@gmail.com")
        resValue("string", "developer_phone", "+201012628353")
        resValue("string", "developer_copyright", "Copyright \u00a9 2024-2025 Eng. Ahmed Magdy. All rights reserved.")
        resValue("string", "support_email", "eng.ahmedmagdy121314@gmail.com")
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
        buildConfig = true
    }

    // Signing configuration — uses env vars for CI, or keystore.properties file locally
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val hasKeystore = keystorePropertiesFile.exists()

    signingConfigs {
        if (hasKeystore) {
            val keystoreProperties = Properties()
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))

            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = file(keystoreProperties["storeFile"] as String? ?: "")
                storePassword = keystoreProperties["storePassword"] as String?
            }
        } else {
            // Fallback: use environment variables (for CI/CD)
            val envAlias = System.getenv("KEY_ALIAS") ?: ""
            val envKeyPass = System.getenv("KEY_PASSWORD") ?: ""
            val envStoreFile = System.getenv("KEYSTORE_FILE") ?: ""
            val envStorePass = System.getenv("KEYSTORE_PASSWORD") ?: ""

            if (envAlias.isNotEmpty() && envStoreFile.isNotEmpty()) {
                create("release") {
                    keyAlias = envAlias
                    keyPassword = envKeyPass
                    storeFile = file(envStoreFile)
                    storePassword = envStorePass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release signing config if available
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
            // Optimize APK size
            isDebuggable = false
            isJniDebuggable = false
            isProfileable = false
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Prevent packaging of unnecessary files
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    // Lint configuration for Play Store compliance
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        checkDependencies = true
        // Play Store required checks
        disable += "TypographyFractions"
        disable += "TypographyQuotes"
        // Allow missing translation for now
        disable += "MissingTranslation"
    }
}

// Suppress Hilt/KSP warnings
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.recyclerview)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Tools
    implementation(libs.gson)
    implementation(libs.mpandroidchart)
    implementation(libs.itext)
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Play Integrity API
    implementation("com.google.android.play:integrity:1.4.0")

    // Google AdMob — Non-intrusive ads (banner + interstitial + native)
    implementation("com.google.android.gms:play-services-ads:24.1.0")
    // User Messaging Platform (consent for EU/EEA users — required by Google Play)
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}