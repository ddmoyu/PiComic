plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}
val targetAbi = providers.gradleProperty("targetAbi").orNull
val localReleaseSigning = providers.gradleProperty("localReleaseSigning").orNull == "true"
require(targetAbi == null || targetAbi in setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
    "Unsupported targetAbi: $targetAbi"
}
android {
    namespace = "io.github.ddmoyu.picomic"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.ddmoyu.picomic"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.1-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetAbi?.let { ndk.abiFilters += it }
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local comparison builds can update the existing development installation.
            // Leave ordinary Release builds unsigned until a production key is configured.
            if (localReleaseSigning) signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.zoomimage.coil)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.junit)
}
