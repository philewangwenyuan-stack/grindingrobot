plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.model"
}

kotlin {
    sourceSets.all {
        languageSettings {
            optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

dependencies {
    // kotlin序列化
    implementation(libs.kotlinx.serialization.json)
}