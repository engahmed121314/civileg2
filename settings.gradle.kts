pluginManagement {
    repositories {
        // Aliyun central mirror — has KSP, kotlin-stdlib, etc. (Maven Central mirror, no 429)
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Hilt plugin marker artifact غير موجود في Aliyun — نربطه يدوياً
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.google.dagger.hilt.android" -> useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
                "com.google.devtools.ksp" -> useModule("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${requested.version}")
            }
        }
    }
}

// foojay-resolver-convention disabled (offline container, JDK path provided explicitly via gradle.properties)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SiteEngineerPro"
include(":app")
include(":core:calculations")
