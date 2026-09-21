plugins {
    alias(libs.plugins.coolmall.android.application.compose)
    alias(libs.plugins.coolmall.hilt)
}

android {
    defaultConfig {
        minSdk = 26

        // 仅包括中文和英文必要的语言资源
        androidResources {
            localeFilters += listOf("zh", "en")
        }
    }

    // ABI 分包配置 - 一次性打包多个架构版本
    splits {
        abi {
            // 启用 ABI 分包
            isEnable = true
            // 重置默认列表
            reset()
            // 包含的架构：32位和64位 ARM
            include("armeabi-v7a", "arm64-v8a")
            // 是否生成通用 APK（包含所有架构）
            // 设置为 true 会额外生成一个包含所有架构的 APK
            isUniversalApk = false
        }
    }

    signingConfigs {
        // 通用签名配置
        // 实际使用时请替换为自己的签名文件
        create("common") {
            // 哪个签名文件
            storeFile = file("joker_open_key.keystore")
            // 密钥别名
            keyAlias = "joker_open_key"
            // 密钥密码
            keyPassword = "123456"
            // 签名文件密码
            storePassword = "123456"

            // 启用所有签名方案以确保最大兼容性
            enableV1Signing = true  // JAR 签名 (Android 1.0+)
            enableV2Signing = true  // APK 签名 v2 (Android 7.0+)
            enableV3Signing = true  // APK 签名 v3 (Android 9.0+)
            enableV4Signing = true  // APK 签名 v4 (Android 11.0+)
        }
    }

    // 构建类型配置
    buildTypes {
        debug {
            // debug 模式下也使用正式签名配置 - 方便调试支付以及三方登录等功能
//            signingConfig = signingConfigs["common"] // 没有签名请注释掉这行使用默认签名
            // debug 模式下包名后缀
            //applicationIdSuffix = ".debug"
        }

        release {
//            signingConfig = signingConfigs["common"] // 没有签名请注释掉这行使用默认签名
            // 是否启用代码压缩
//            isMinifyEnabled = false
//            // 资源压缩
//            isShrinkResources = true
//            // 配置ProGuard规则文件
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
        }
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.navigation)
    implementation(projects.core.designsystem)
    implementation(projects.core.network)
    
    implementation(libs.androidx.appcompat)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(projects.feature.common)
    implementation(projects.feature.connect)
    implementation(projects.feature.main)
    implementation(projects.feature.map)
}
