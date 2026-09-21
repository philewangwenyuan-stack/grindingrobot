plugins {
    alias(libs.plugins.coolmall.android.feature)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.feature.remote"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}