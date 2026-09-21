plugins {
    alias(libs.plugins.coolmall.android.feature)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.feature.map"
}

dependencies {
    implementation(projects.core.tcp)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.feature.common)
    implementation(projects.core.sllink)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}