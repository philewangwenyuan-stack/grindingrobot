package com.sinelynx.grindingrobot

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import com.sinelynx.grindingrobot.core.designsystem.theme.AppTheme
import com.sinelynx.grindingrobot.navigation.AppNavHost
import com.sinelynx.grindingrobot.navigation.AppNavigator
import com.sinelynx.grindingrobot.feature.main.ota.ApkOtaManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 应用的主Activity
 *
 * @author Dreamj
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appNavigator: AppNavigator
    @Inject lateinit var apkOtaManager: ApkOtaManager

    override fun onResume() {
        super.onResume()
        // 安装结果广播只创建进程；主页面进入前台后才确认新版本已被打开。
        apkOtaManager.onForegroundLaunch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            AppTheme {
                RequestLocationPermissionOnStart()
                AppNavHost(navigator = appNavigator)
            }
        }
    }
}

@Composable
private fun RequestLocationPermissionOnStart() {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions)
    }
}

