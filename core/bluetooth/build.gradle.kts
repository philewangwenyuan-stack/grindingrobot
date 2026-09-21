plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.bluetooth"
}

dependencies {
    implementation(projects.core.util)
    implementation(projects.core.model)

    // Kotlin 协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Hilt 依赖注入
    implementation(libs.hilt.android)
    kspAndroidTest(libs.hilt.compiler)


    // Androidx Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
