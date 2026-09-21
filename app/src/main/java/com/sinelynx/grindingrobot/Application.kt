package com.sinelynx.grindingrobot

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.TopAppBarState
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.util.storage.MMKVUtils
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.feature.main.ota.ApkOtaManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.and

/**
 * 全局Application
 *
 * @author Dreamj
 */
@HiltAndroidApp
class GrindingRobotApplication : Application() {
    //注入全局状态管理器
    @Inject
    lateinit var appStateManager: AppState
    @Inject lateinit var apkOtaManager: ApkOtaManager
    override fun onCreate() {
        super.onCreate()
        initToast()
        initMMKV()
        // 广播也会启动应用进程，此处只重试事件，不确认新版本主页面已打开。
        apkOtaManager.onProcessStart()
        appStateManager.initialize()
    }


    /**
     * 初始化 Toast 框架
     *
     * @author Dreamj
     */
    private fun initToast() {
        // 检测当前是否为深色模式
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

        // 初始化Toast，传递深色模式参数
        ToastUtils.init(this, isDarkTheme)
    }

    /**
     * 初始化 MMKV 框架
     *
     * @author Dreamj
     */
    private fun initMMKV() {
        MMKVUtils.init(this)
    }

}



