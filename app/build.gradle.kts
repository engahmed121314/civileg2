import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    jacoco
}

android {
    namespace = "com.civileg.app"
    compileSdk = 36

    // Load local properties for external IDs (local.properties is NOT committed —
    // see local.properties.template for every supported key)
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.civileg.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.8.0"

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
            arg("ksp.incremental", "false")
        }

        // Developer info for Play Store
        resValue("string", "developer_name", "Eng. Ahmed Magdy")
        resValue("string", "developer_email", "eng.ahmedmagdy121314@gmail.com")
        resValue("string", "developer_phone", "+201012628353")
        resValue("string", "developer_copyright", "Copyright \u00a9 2024-2025 Eng. Ahmed Magdy. All rights reserved.")
        resValue("string", "support_email", "eng.ahmedmagdy121314@gmail.com")

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

    // ── Distribution flavors ─────────────────────────────────────────────
    // `play`  — the Play Store build: AdMob (banner/interstitial/native), UMP consent,
    //           Play Billing. Everything ad-related lives in src/play.
    // `free`  — the same app with no ads and no billing: no play-services-ads, no UMP,
    //           no billing client, and therefore no AD_ID / BILLING permissions and no
    //           AdMob meta-data in the merged manifest. src/free holds no-op stand-ins.
    //
    // Variant names change: `./gradlew :app:assembleFreeDebug` / `:app:assemblePlayDebug`
    // (the old `assembleDebug` no longer exists). tools/emulator scripts default to
    // `free` so screenshots never show ads or a consent dialog.
    flavorDimensions += "distribution"

    productFlavors {
        create("free") {
            dimension = "distribution"
        }

        create("play") {
            dimension = "distribution"

            val admobAppId = localProperties.getProperty("admob.app.id")
                ?: "ca-app-pub-3940256099942544~3347511713"
            manifestPlaceholders["admobAppId"] = admobAppId

            // AdMob ad-unit IDs: defaults are Google's well-known TEST IDs (serve test
            // ads only, earn no revenue). For production set in local.properties:
            //   admob.banner.id=ca-app-pub-XXXXX/YYYYY   (same for interstitial/native/rewarded)
            fun admobId(key: String, testId: String): String =
                localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: testId
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"${admobId("admob.banner.id", "ca-app-pub-3940256099942544/6300978111")}\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"${admobId("admob.interstitial.id", "ca-app-pub-3940256099942544/1033173712")}\"")
            buildConfigField("String", "NATIVE_AD_UNIT_ID", "\"${admobId("admob.native.id", "ca-app-pub-3940256099942544/2247696110")}\"")
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"${admobId("admob.rewarded.id", "ca-app-pub-3940256099942544/5224354917")}\"")
        }
    }

    buildFeatures {
        // No XML layouts use generated binding classes any more: the splash screen
        // uses setContentView + findViewById. Keep this off to shorten builds.
        viewBinding = false
        dataBinding = false
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
    implementation(libs.firebase.crashlytics)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

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

    // ── Play-only: ads + billing (see productFlavors above) ──────────────
    // Play Billing
    "playImplementation"(libs.play.billing)
    "playImplementation"(libs.play.billing.ktx)

    // Tools
    implementation(libs.gson)
    implementation(libs.mpandroidchart)
    implementation(libs.itext)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Play Integrity API
    implementation(libs.play.integrity)

    // Google AdMob — Non-intrusive ads (banner + interstitial + native). Play build only;
    // this is what drags in the AD_ID permission and the play-services-ads-lite runtime.
    "playImplementation"(libs.play.services.ads)
    // User Messaging Platform (consent for EU/EEA users — required by Google Play)
    "playImplementation"(libs.user.messaging.platform)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    // Compose UI tests on the JVM (Robolectric) for the legibility invariants (§12.7 #3, #4)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Security: force patched Guava pulled transitively by play-services-ads-lite.
    // Resolved 31.1-android is affected by CVE-2023-2976 and CVE-2020-8908.
    constraints {
        implementation("com.google.guava:guava:33.4.0-android") {
            because("CVE-2023-2976 / CVE-2020-8908")
        }
    }
}

configure<JacocoPluginExtension> {
    toolVersion = "0.8.12"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testFreeDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*", "**/*_MembersInjector.*",
        "**/Dagger*Component*.*", "**/*_Factory*.*"
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/freeDebug").matching {
        exclude(fileFilter)
    }
    val mainSrc = "${projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(layout.buildDirectory.get()).include("outputs/unit_test_code_coverage/freeDebugUnitTest/testFreeDebugUnitTest.exec", "jacoco/testFreeDebugUnitTest.exec"))
}
