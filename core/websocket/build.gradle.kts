plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.websocket"
}

dependencies {
    implementation(libs.okhttp3)
    implementation(libs.okio)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // 引入 core 模块的其他组件
    implementation(projects.core.common)
    implementation(projects.core.model)
}