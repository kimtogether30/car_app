plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    useLibrary("android.car", false)
    namespace = "com.example.vemoring"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.vemoring"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("verification") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".verification"
            matchingFallbacks.add("debug")
            signingConfig = getByName("debug").signingConfig
        }
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    // Explicit opt-in only: physical radio fixtures must never enter the real vehicle garage/stream.
    testBuildType = if(providers.gradleProperty("vehicleVerification").orNull == "true") "verification" else "debug"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets.getByName("main").kotlin.directories.add(rootProject.file("companion/src/main/kotlin").path)
    sourceSets.getByName("androidTest").kotlin.directories.add(rootProject.file("companion/src/androidTest/kotlin").path)
    sourceSets.getByName("verification").kotlin.directories.add(file("src/debug/java").path)
}

androidComponents {
    beforeVariants(selector().withBuildType("debug")) { it.hostTests["UnitTest"]?.enable = true }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:navi-data:3.0.4")
    implementation("ai.pleos.playground:fused-location-data:0.0.3")
    implementation("ai.pleos.playground:vehicle-data:1.1.11")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("verificationImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
