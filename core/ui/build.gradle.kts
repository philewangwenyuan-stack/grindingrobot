plugins {
    alias(libs.plugins.coolmall.android.library.compose)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.ui"
}
dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.util)

    // 图片加载框架
    implementation(libs.coil.compose)

    // lottie 动画
    // https://airbnb.io/lottie/#/android-compose
    implementation(libs.lottie.compose)
    implementation(libs.androidx.compose.foundation)
}