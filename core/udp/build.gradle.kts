plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.udp"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(projects.core.util)
}
