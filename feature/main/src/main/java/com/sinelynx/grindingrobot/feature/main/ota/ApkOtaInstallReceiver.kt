package com.sinelynx.grindingrobot.feature.main.ota

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 接收安装会话结果及本应用替换广播；两条路径共同补记安装成功。 */
@AndroidEntryPoint
class ApkOtaInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var manager: ApkOtaManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ApkOtaManager.ACTION_INSTALL_RESULT -> manager.handleInstallResult(intent)
            Intent.ACTION_MY_PACKAGE_REPLACED -> manager.onPackageReplaced()
        }
    }
}
