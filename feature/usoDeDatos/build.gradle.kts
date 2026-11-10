plugins {
    id("msp.android.library")
    id("msp.android.compose")
    id("msp.hilt")
    id("msp.detekt")
    id("msp.kover")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.example.msp_app.feature.usodedatos"
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:datos"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.ui.test.junit4)
}
