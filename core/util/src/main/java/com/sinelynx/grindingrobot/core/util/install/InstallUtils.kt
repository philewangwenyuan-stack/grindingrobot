package com.sinelynx.grindingrobot.core.util.install

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import java.io.File

/**
 * 安装 APK 工具（OTA 更新完成后调起安装）
 *
 * 调用方需在 AndroidManifest 中配置 FileProvider，authority 为 "${applicationId}.fileProvider"
 */
object InstallUtils {

    private const val MIME_APK = "application/vnd.android.package-archive"
    private val URI_GRANT_READ_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val URI_GRANT_VIEW_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION

    private val KNOWN_INSTALLER_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.samsung.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.coloros.safecenter"
    )

    /**
     * 调起系统安装界面安装 APK
     *
     * @param context 上下文（建议使用 Activity 或 Application）
     * @param apkFile 已下载的 APK 文件
     * @return 是否成功发起安装 Intent
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.canRead()) return false
        return try {
            val authority = "${context.packageName}.fileProvider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(URI_GRANT_VIEW_FLAGS)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    clipData = ClipData.newUri(context.contentResolver, "apk", uri)
                }
            }
            val pm = context.packageManager ?: return false
            val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            var resolvedComponent: ComponentName? = null
            if (resolveInfo?.activityInfo != null) {
                val info = resolveInfo.activityInfo
                resolvedComponent = ComponentName(info.packageName, info.name)
            }
            val packagesToGrant = mutableSetOf<String>()
            resolvedComponent?.packageName?.let { packagesToGrant.add(it) }
            packagesToGrant.addAll(KNOWN_INSTALLER_PACKAGES)
            try {
                pm.getInstallerPackageName(context.packageName)?.let { packagesToGrant.add(it) }
            } catch (_: Exception) { }
            for (pkg in packagesToGrant) {
                try {
                    context.grantUriPermission(pkg, uri, URI_GRANT_VIEW_FLAGS)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (resolvedComponent != null) {
                intent.component = resolvedComponent
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    /**
     * 安装 APK（兼容 Android 7+ / 10+ / 各厂商 ROM）
     */
    fun installApkNew(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || !apkFile.canRead()) return false
        // Android 13+ 上，ACTION_INSTALL_PACKAGE 在部分 ROM 上对 grant 的兼容性较差；
        // 直接使用 ACTION_VIEW 的路径可以显著提升成功率。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return installApk(context, apkFile)
        }

        return try {
            val authority = "${context.packageName}.fileProvider"

            val uri = FileProvider.getUriForFile(
                context,
                authority,
                apkFile
            )

            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Android 13+ 对安装器进程的 Uri 访问需要显式 READ 授权。
                // 此处不要带 WRITE（部分 ROM/Provider 会导致 grant 失败，最终读权限缺失）。
                addFlags(URI_GRANT_READ_FLAGS)
                setDataAndType(uri, MIME_APK)

                // ⭐ 必须：解决部分 ROM 权限丢失
                // 与 installApk() 保持一致：更容易被系统按 intent grant 识别
                clipData = ClipData.newUri(context.contentResolver, "apk", uri)

                // ⭐ 提升兼容性
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }

            // ⭐ 强制授权（关键）
            runCatching {
                val pm = context.packageManager
                val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.resolveActivity(
                        intent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
                resolveInfo?.activityInfo?.let { ai ->
                    context.grantUriPermission(ai.packageName, uri, URI_GRANT_READ_FLAGS)
                    intent.component = ComponentName(ai.packageName, ai.name)
                }
            }.onFailure { it.printStackTrace() }

            grantToAllInstaller(context, uri)
            // Android 13+ 上我们最关心的是系统的 packageinstaller 进程
            // 再显式授权一次，确保 grantUriPermission 不依赖 resolveActivity 的结果。
            runCatching {
                context.grantUriPermission(
                    "com.android.packageinstaller",
                    uri,
                    URI_GRANT_READ_FLAGS
                )
            }

            context.startActivity(intent)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.d("错误日志 ${e.message}")
            false
        }
    }

    /**
     * 给所有可能的安装器授权 Uri（防止权限丢失）
     */
    private fun grantToAllInstaller(context: Context, uri: Uri) {
        val packages = listOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.miui.packageinstaller",
            "com.coloros.safecenter",
            "com.samsung.android.packageinstaller"
        )

        for (pkg in packages) {
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    URI_GRANT_READ_FLAGS
                )
            } catch (e: Exception) {
                // Android 13+ 各 ROM 在授权上可能存在差异，保留栈用于排查
                LogUtils.d("错误日志 $pkg $uri ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
