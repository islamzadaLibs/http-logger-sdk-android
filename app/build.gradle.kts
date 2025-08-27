plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
}

android {
    namespace = "com.islamzada.networklogger.sdk"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx.v1170)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase
    implementation(libs.firebase.firestore.ktx)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Retrofit
    implementation(libs.retrofit)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.islamzadaLibs"
            artifactId = "http-logger-sdk-android"
            version = "1.0.1"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}