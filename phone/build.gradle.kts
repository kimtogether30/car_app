plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.vemoring.phone"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.example.vemoring.phone"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "com.example.vemoring.phone.VerificationRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    // Device tests install/uninstall only this disposable application ID.
    buildTypes {
        create("verification") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".verification"
            matchingFallbacks.add("debug")
            signingConfig = getByName("debug").signingConfig
        }
    }
    testBuildType = "verification"
    sourceSets.getByName("main").kotlin.directories.add(rootProject.file("companion/src/main/kotlin").path)
    sourceSets.getByName("test").kotlin.directories.add(rootProject.file("companion/src/test/kotlin").path)
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { it.hostTests["UnitTest"]?.enable = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("verificationImplementation", libs.androidx.compose.ui.test.manifest)
}
