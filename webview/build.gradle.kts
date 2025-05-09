plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.yubicolabs.funke_explorer"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.yubicolabs.funke_explorer"
        minSdk = 33
        targetSdk = 35
        versionCode = (property("wallet.versionCode") as String).toInt()
        versionName = property("wallet.versionName") as String

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("all") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = project.rootProject.file("funke.keystore")
        }
    }

    buildTypes {
        all {
            buildConfigField("Boolean", "SHOW_URL_ROW", "false")
            buildConfigField("String", "BASE_URL", "\"https://demo.wwwallet.org\"")
            buildConfigField("Boolean", "VISUALIZE_INJECTION", "false")
            buildConfigField("Boolean", "SHOW_URL_ROW", "false")

            signingConfig = signingConfigs.getByName("all")
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            buildConfigField("Boolean", "VISUALIZE_INJECTION", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }


    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
            excludes += "COPYING"
        }

        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.playservices)
    implementation(libs.coroutines)
    implementation(libs.webkit)
    implementation(libs.ausweis)
    implementation(libs.yubikit.android)
    implementation(libs.yubikit.fido)
    implementation(libs.logback)

    debugImplementation(libs.softauth)

    testImplementation(libs.junit)
    testImplementation(libs.test.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
