plugins {
    alias(libs.plugins.coolmall.android.feature)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.feature.device"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(projects.core.data)
    implementation(projects.core.model)
    implementation(projects.feature.map)
    implementation(projects.feature.common)
    implementation(projects.core.sllink)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(projects.core.tcp)
}
