import org.jetbrains.kotlin.gradle.utils.IMPLEMENTATION

plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.coolmall.hilt)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.tcp"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    
    // 引入 core 模块的其他组件
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.model)
    implementation(projects.core.util)
    implementation(projects.core.sllink)
}
