plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.sinelynx.grindingrobot.feature.main"
}

dependencies {
    // lottie 动画
    // https://airbnb.io/lottie/#/android-compose
    implementation(libs.lottie.compose)

    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.datastore)

    implementation(projects.core.bluetooth)

    implementation(projects.core.tcp)
    
    // 公共组件模块
    implementation(projects.feature.common)
    implementation(projects.feature.device)
    implementation(projects.feature.map)
    implementation(projects.core.sllink)
//    implementation(projects.feature.login)

    implementation(libs.androidx.compose.runtime)

    implementation("com.google.code.gson:gson:2.10.1")


    // SceneView: Compose-friendly 3D viewer built on Filament for loading GLB/GLTF
    implementation("io.github.sceneview:sceneview:2.0.3")

    // Filament core/render utils/GLTF importer/material compiler for custom pipelines
    implementation("com.google.android.filament:filament-android:1.66.1")
    implementation("com.google.android.filament:filament-utils-android:1.66.1")
    implementation("com.google.android.filament:gltfio-android:1.66.1")
    implementation("com.google.android.filament:filamat-android:1.66.1")
}
