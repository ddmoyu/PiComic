plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}
val targetAbi = providers.gradleProperty("targetAbi").orNull
val releaseAbiSplits = providers.gradleProperty("releaseAbiSplits").orNull == "true"
require(!releaseAbiSplits || targetAbi == null) { "releaseAbiSplits and targetAbi cannot be combined" }
val localReleaseSigning = providers.gradleProperty("localReleaseSigning").orNull == "true"
val releaseOwner = providers.gradleProperty("releaseOwner").orElse("ddmoyu").get()
val releaseRepo = providers.gradleProperty("releaseRepo").orElse("PiComic").get()
val taggedVersion = providers.gradleProperty("releaseVersionName").orNull
val taggedCode = taggedVersion?.let { version ->
    require(version.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) { "Release version must be X.Y.Z" }
    val parts = version.split('.').map { it.toLongOrNull() ?: error("Version component is too large") }
    require(parts[0] <= 2099 && parts[1] <= 999 && parts[2] <= 999) { "Version component is out of range" }
    (parts[0] * 1_000_000 + parts[1] * 1_000 + parts[2]).also { require(it in 1..2_099_999_999) }.toInt()
}
val releaseKeyFile = providers.environmentVariable("PICOMIC_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("PICOMIC_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("PICOMIC_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("PICOMIC_KEY_PASSWORD").orNull
if (releaseKeyFile != null) {
    require(!localReleaseSigning) { "Production and development signing cannot be combined" }
    require(listOf(releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrEmpty() }) { "Release signing configuration is incomplete" }
}
require((releaseOwner.isEmpty() && releaseRepo.isEmpty()) ||
    (releaseOwner.matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) && releaseRepo.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}"))))
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
        versionCode = taggedCode ?: 8
        versionName = taggedVersion ?: "0.3.0-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASE_OWNER", "\"$releaseOwner\"")
        buildConfigField("String", "RELEASE_REPO", "\"$releaseRepo\"")
        targetAbi?.let { ndk.abiFilters += it }
    }
    buildFeatures { compose = true; buildConfig = true }
    splits.abi {
        isEnable = releaseAbiSplits
        reset()
        include("arm64-v8a", "armeabi-v7a", "x86_64")
        isUniversalApk = false
    }
    if (releaseKeyFile != null) signingConfigs.create("production") {
        storeFile = file(releaseKeyFile)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Local comparison builds can update the existing development installation.
            // Leave ordinary Release builds unsigned until a production key is configured.
            if (localReleaseSigning) signingConfig = signingConfigs.getByName("debug")
            if (releaseKeyFile != null) signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    coreLibraryDesugaring(libs.desugar.nio)
    implementation(libs.jsoup)
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
    implementation(libs.coil.network)
    implementation(libs.coil.gif)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.webkit)
    implementation(libs.zoomimage.coil)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.mockwebserver)
}
