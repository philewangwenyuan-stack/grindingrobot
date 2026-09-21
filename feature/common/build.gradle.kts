plugins {
    alias(libs.plugins.coolmall.android.feature)
}

android {
    namespace = "com.sinelynx.grindingrobot.feature.common"
}
dependencies {
    implementation(projects.core.database)

    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.2.1")
}
