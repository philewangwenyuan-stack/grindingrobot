plugins {
    alias(libs.plugins.coolmall.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sinelynx.grindingrobot.core.sllink"

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    // Source directories
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/frame",       // 帧处理（手写）
                "src/message_gen", // 消息（protobuf 生成）
                "examples"
            )
        }
    }
}

dependencies {
    // Protobuf runtime (code is pre-generated)
    // protobuf-java needed for DescriptorProtos (used by custom options: unit, scale)
    api("com.google.protobuf:protobuf-kotlin:3.24.0")
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation(projects.core.util)

}
