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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.civileg.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.6.0"

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

        // Load local properties for external IDs (local.properties is NOT committed —
        // see local.properties.template for every supported key)
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        val admobAppId = localProperties.getProperty("admob.app.id") ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["admobAppId"] = admobAppId

        // ── P4-3 production configuration (§9.37) ──────────────────────────
        // AdMob ad-unit IDs: defaults are Google's well-known TEST IDs (serve test
        // ads only, earn no revenue). For production set in local.properties:
        //   admob.banner.id=ca-app-pub-XXXXX/YYYYY   (same for interstitial/native/rewarded)
        fun admobId(key: String, testId: String): String =
            localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: testId
        buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${admobId("admob.banner.id", "ca-app-pub-3940256099942544/6300978111")}\"")
        buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"${admobId("admob.interstitial.id", "ca-app-pub-3940256099942544/1033173712")}\"")
        buildConfigField("String", "NATIVE_AD_UNIT_ID", "\"${admobId("admob.native.id", "ca-app-pub-3940256099942544/2247696110")}\"")
        buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"${admobId("admob.rewarded.id", "ca-app-pub-3940256099942544/5224354917")}\"")

        // Release signer fingerprint (SHA-256 of the release keystore certificate).
        // Obtain via:  keytool -list -v -keystore your-release-key.jks -alias your-alias
        // Set in local.properties:  signing.fingerprint.sha256=AB:CD:EF:...:12:34
        // Empty (default) => signature verification is skipped until configured.
        buildConfigField(
            "String",
            "EXPECTED_SIGNER_FINGERPRINT",
            "\"${localProperties.getProperty("signing.fingerprint.sha256")?.trim() ?: ""}\""
        )
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
        buildConfig = true
        resValues = true
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
            if (!project.hasProperty("noDebugSuffix")) {
                applicationIdSuffix = ".debug"
                versionNameSuffix = "-debug"
            }
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

    // Prevent packaging of unnecessary files
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    // Unit test configuration (Robolectric needs Android resources)
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
        // Suppress pre-existing resource issues
        disable += "MissingDefaultResource"
        disable += "UnusedResources"
        disable += "IconDuplicatesConfig"
        disable += "VectorPath"
        disable += "HardcodedText"
        disable += "RtlHardcoded"
        // Backup rules exclude path lint is overly strict
        disable += "FullBackupContent"
    }
}

// Suppress Hilt/KSP warnings
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}

dependencies {
    implementation(project(":core:calculations"))
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

    // Play Billing
    implementation(libs.play.billing)
    implementation(libs.play.billing.ktx)

    // Tools
    implementation(libs.gson)
    implementation(libs.mpandroidchart)
    implementation(libs.itext)
    implementation(libs.glide)
    ksp(libs.glide.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Play Integrity API
    implementation(libs.play.integrity)

    // Google AdMob — Non-intrusive ads (banner + interstitial + native)
    implementation(libs.play.services.ads)
    // User Messaging Platform (consent for EU/EEA users — required by Google Play)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Security: force patched Guava pulled transitively by play-services-ads-lite.
    // Resolved 31.1-android is affected by CVE-2023-2976 and CVE-2020-8908.
    constraints {
        implementation("com.google.guava:guava:33.4.0-android") {
            because("CVE-2023-2976 / CVE-2020-8908")
        }
    }
}
