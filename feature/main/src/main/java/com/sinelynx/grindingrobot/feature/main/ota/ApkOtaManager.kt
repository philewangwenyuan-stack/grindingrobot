package com.sinelynx.grindingrobot.feature.main.ota

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaCheckRequest
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaCheckResponse
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaEvent
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaRelease
import com.sinelynx.grindingrobot.core.network.service.ApkOtaService
import com.sinelynx.grindingrobot.core.network.service.shouldAppendDownload
import com.sinelynx.grindingrobot.core.util.`package`.PackageUtils
import com.sinelynx.grindingrobot.core.util.storage.MMKVUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 安装会话跨进程重启的最小状态。两个事件 ID 单独保存：系统回调和新版本首次启动
 * 可能先后观察到同一次安装，仍须以相同 ID 上报，避免重复计数。
 */
@Serializable
private data class PendingApkInstall(
    val releaseId: String,
    val versionCode: Long,
    val installReported: Boolean = false,
    val successNotificationPosted: Boolean = false,
    val installSuccessEventId: String? = null,
    val launchConfirmedEventId: String? = null
)

/** 安装会话的终态；设置页只在收到终态后结束等待。 */
data class ApkInstallOutcome(val success: Boolean, val message: String? = null)

/** 协调 APK OTA 的检查、下载校验、系统安装和可恢复的事件上报。 */
@Singleton
class ApkOtaManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ApkOtaService
) {
    // 系统安装回调不依赖设置页生命周期，因此使用管理器级 IO 协程处理后续上报。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 网络发送串行化；MMKV 读改写另用进程内锁保护，避免并发回调覆盖待发事件。
    private val eventMutex = Mutex()
    private val storeLock = Any()
    private val _installOutcomes = MutableSharedFlow<ApkInstallOutcome>(extraBufferCapacity = 1)
    val installOutcomes = _installOutcomes.asSharedFlow()

    // 当前联调使用固定测试设备 ID，所有检查、下载地址刷新和事件均传同一值。
    // 多台设备正式部署前应改为每台设备独立且持久化的 ID，避免灰度分桶混淆。
    private fun installId(): String = "9bd805bf-7c43-4c66-ba7f-08aed61b1877"

    /** 待安装记录在安装成功后仍保留至主页面真正打开；只锁住更高的旧版本。 */
    fun hasPendingInstall(): Boolean = synchronized(storeLock) {
        MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING)?.versionCode?.let {
            it > PackageUtils.getCurrentVersionCode(context)
        } == true
    }

    /** 设置页重新建立时取出一次上次安装失败原因，避免广播时页面不在前台而丢提示。 */
    fun consumeInstallFailure(): String? = synchronized(storeLock) {
        MMKVUtils.getString(KEY_INSTALL_FAILURE).takeIf { it.isNotBlank() }?.also {
            MMKVUtils.remove(KEY_INSTALL_FAILURE)
        }
    }

    /** 构造服务端选版所需的完整设备上下文；调试属性决定 test/production 渠道。 */
    private fun deviceContext() = ApkOtaCheckRequest(
        packageName = OTA_PACKAGE_NAME,
        channel = if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "test" else "production",
        versionCode = PackageUtils.getCurrentVersionCode(context),
        installId = installId(),
        androidApiLevel = Build.VERSION.SDK_INT,
        supportedAbis = Build.SUPPORTED_ABIS.toList()
    )

    /** 手动检查更新时顺便重试历史事件；有更新但缺少 release 属于不可用响应。 */
    suspend fun check(): ApkOtaCheckResponse = withContext(Dispatchers.IO) {
        flushEvents()
        service.check("$OTA_BASE/api/ota/v1/check", deviceContext()).also { result ->
            if (result.hasUpdate && result.release == null) throw IllegalStateException("服务端未返回版本信息")
        }
    }

    /**
     * 下载到私有缓存，完成完整性与安装包身份校验后才交给系统安装器。
     * 检查当前实际包名和更高版本号是本地安装安全条件，不能用请求中固定的 OTA 包名代替。
     */
    suspend fun downloadAndInstall(release: ApkOtaRelease) = withContext(Dispatchers.IO) {
        check(!hasPendingInstall()) { "安装正在进行，请等待系统处理" }
        require(release.packageName == context.packageName && release.versionCode > PackageUtils.getCurrentVersionCode(context)) {
            "更新包与当前应用不匹配"
        }
        val part = partFile(release)
        report(release.releaseId, "download_started", true)
        val apk = try {
            download(release, part)
        } catch (e: Exception) {
            report(release.releaseId, "download_failed", false, e.message)
            throw e
        }
        report(release.releaseId, "download_completed", true)
        try {
            validate(release, apk)
        } catch (e: Exception) {
            apk.delete()
            report(release.releaseId, "verification_failed", false, e.message)
            throw e
        }
        report(release.releaseId, "verification_succeeded", true)

        // 下载可能耗时较久；安装前重新选版，避免安装已撤回或不再命中灰度的版本。
        val latest = service.check("$OTA_BASE/api/ota/v1/check", deviceContext())
        if (!latest.hasUpdate || latest.release?.releaseId != release.releaseId) {
            throw IllegalStateException("版本已变化或不可用，请重新检查更新")
        }
        // 刷新地址还会让服务端复核发布状态、渠道和设备资格。
        refresh(release)
        report(release.releaseId, "install_started", true)
        try {
            install(release, apk)
        } catch (e: Exception) {
            report(release.releaseId, "install_failed", false, e.message)
            throw e
        }
    }

    /**
     * 每次下载或续传前获取新的预签名 URL。releaseId、大小和摘要必须与最初确认的版本相同，
     * 防止续传时混入另一份文件；下载地址仅接受文档指定的 HTTPS APK 域名。
     */
    private suspend fun refresh(release: ApkOtaRelease) = service.refreshDownloadUrl(
        "$OTA_BASE/api/ota/v1/releases/${release.releaseId}/download-url", deviceContext()
    ).also {
        check(it.releaseId == release.releaseId && it.fileSize == release.fileSize &&
            it.fileSHA256.equals(release.fileSHA256, ignoreCase = true)) { "版本文件信息已变化，请重新检查更新" }
        check(it.fileUrl.startsWith("https://apk.4digitalcenter.cloud:8443/")) { "下载地址不可信" }
    }

    /**
     * 临时文件跨重试保留。206 时按 Content-Range 追加；200 时覆盖重下。
     * 已签发地址返回 403 时仅重新取一次地址，避免无限重试失去资格的版本。
     */
    private suspend fun download(release: ApkOtaRelease, part: File): File {
        if (part.exists() && part.length() > release.fileSize) part.delete()
        repeat(2) { attempt ->
            val url = refresh(release).fileUrl
            val offset = part.takeIf { it.exists() }?.length() ?: 0L
            // 上次下载可能已写完但未校验；仍由后续 validate() 核对摘要及 APK 元数据。
            if (offset == release.fileSize) return part
            val response = service.download(url, if (offset > 0L) "bytes=$offset-" else null)
            if (response.code() == 403 && attempt == 0) {
                response.errorBody()?.close()
                return@repeat
            }
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                response.errorBody()?.close()
                throw IllegalStateException("APK 下载失败：HTTP ${response.code()}")
            }
            body.use { source ->
                val append = shouldAppendDownload(response.code(), offset, response.headers()["Content-Range"])
                FileOutputStream(part, append).use { output ->
                    source.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = if (append) offset else 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            // 在写入前拒绝超长响应，避免异常服务器持续占用设备存储。
                            check(downloaded <= release.fileSize) { "APK 文件大小超出预期" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            check(part.length() == release.fileSize) { "APK 下载不完整，可重试续传" }
            return part
        }
        throw IllegalStateException("下载地址已失效，请重新检查更新")
    }

    /** releaseId 进入本地文件名，须先限制字符；摘要格式和大小也在开始写文件前验证。 */
    private fun partFile(release: ApkOtaRelease): File {
        require(release.releaseId.matches(Regex("[A-Za-z0-9_-]+"))) { "版本标识无效" }
        require(release.fileSize > 0 && release.fileSHA256.matches(Regex("[a-fA-F0-9]{64}"))) { "版本校验信息无效" }
        return File(context.cacheDir, "ota-${release.releaseId}.apk.part")
    }

    /**
     * SHA-256 确认下载字节与服务端发布文件一致；包名、版本及签名再确认该文件
     * 确实可以覆盖当前应用，不能只凭文件扩展名或下载成功就发起安装。
     */
    private fun validate(release: ApkOtaRelease, apk: File) {
        check(apk.length() == release.fileSize) { "APK 文件大小不匹配" }
        val sha = MessageDigest.getInstance("SHA-256")
        apk.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha.update(buffer, 0, count)
            }
        }
        val actualHash = sha.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(actualHash.equals(release.fileSHA256, ignoreCase = true)) { "APK SHA-256 校验失败" }

        val pm = context.packageManager
        // Android 9 起读取 SigningInfo；旧系统沿用 signatures，确保 minSdk 26 也可核验。
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("无法读取 APK 包信息")
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(context.packageName, flags)
        check(archive.packageName == context.packageName && archive.packageName == release.packageName) { "APK 包名不匹配" }
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        check(archiveVersion == release.versionCode && archiveVersion > PackageUtils.getCurrentVersionCode(context)) { "APK 版本号不匹配" }
        @Suppress("DEPRECATION")
        val newSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        @Suppress("DEPRECATION")
        val oldSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        // 用证书集合比较，拒绝签名不同的同包名 APK；缺少签名信息时按失败处理。
        check(!newSigners.isNullOrEmpty() && !oldSigners.isNullOrEmpty() &&
            newSigners.map { it.toCharsString() }.toSet() == oldSigners.map { it.toCharsString() }.toSet()) {
            "APK 签名与当前应用不一致"
        }
    }

    /**
     * 通过 PackageInstaller 会话提交已校验 APK。提交前保存待安装版本，
     * 这样安装导致进程退出后，系统回调或下次启动仍能关联到原 release。
     */
    private fun install(release: ApkOtaRelease, apk: File) {
        check(!hasPendingInstall()) { "安装正在进行，请等待系统处理" }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12 起可明确要求系统返回确认 Intent；旧系统由安装器决定。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                synchronized(storeLock) {
                    MMKVUtils.remove(KEY_INSTALL_FAILURE)
                    MMKVUtils.putObject(KEY_PENDING, PendingApkInstall(release.releaseId, release.versionCode))
                }
                val intent = Intent(context, ApkOtaInstallReceiver::class.java).apply {
                    action = ACTION_INSTALL_RESULT
                    putExtra(EXTRA_RELEASE_ID, release.releaseId)
                }
                // 系统安装器需填入状态和用户确认 Intent，因此回调 PendingIntent 必须可变。
                val sender = PendingIntent.getBroadcast(
                    context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                ).intentSender
                session.commit(sender)
            }
        } catch (e: Exception) {
            // 提交前失败则放弃会话并清理待安装记录；调用方负责上报 install_failed。
            runCatching { installer.abandonSession(sessionId) }
            synchronized(storeLock) { MMKVUtils.remove(KEY_PENDING) }
            throw e
        }
    }

    /**
     * 处理系统安装器的异步状态。打开用户确认界面不等于安装成功，
     * 只有 STATUS_SUCCESS 才记录 install_succeeded。
     */
    fun handleInstallResult(intent: Intent) {
        val releaseId = intent.getStringExtra(EXTRA_RELEASE_ID) ?: return
        val pending = synchronized(storeLock) { MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING) } ?: return
        if (pending.releaseId != releaseId) return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val approval = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (approval == null) {
                    failInstall(releaseId, "未获取到安装确认界面")
                } else {
                    // 某些系统会无异常地拒绝后台打开页面，先保留可由用户触发的确认入口。
                    val notified = postNotification(CONFIRM_NOTIFICATION_ID, "请确认 APP 更新",
                        "若安装页未打开，点击此处继续", approval)
                    runCatching { context.startActivity(approval.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure {
                            if (!notified) {
                                failInstall(releaseId, it.message ?: "无法打开安装确认界面")
                            }
                        }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> recordInstallSuccess(releaseId)
            else -> failInstall(releaseId, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "系统安装失败")
        }
    }

    /** 安装失败先持久化事件，再清理待安装状态并通知仍在前台的设置页。 */
    private fun failInstall(releaseId: String, message: String) {
        enqueueEvent(releaseId, "install_failed", false, message)
        synchronized(storeLock) {
            MMKVUtils.remove(KEY_PENDING)
            MMKVUtils.putString(KEY_INSTALL_FAILURE, message)
        }
        notificationManager.cancel(CONFIRM_NOTIFICATION_ID)
        _installOutcomes.tryEmit(ApkInstallOutcome(false, message))
        scope.launch { flushEvents() }
    }

    /**
     * Application 可能只是为安装结果广播启动进程，不能在这里确认主页面已打开。
     * 进程启动只核实安装结果并补发历史事件，不确认主页面已打开。
     */
    fun onProcessStart() {
        onPackageReplaced()
        scope.launch { flushEvents() }
    }

    /** 系统成功回调可能在进程更替时丢失，包替换广播按实际安装版本补记。 */
    fun onPackageReplaced() {
        val pending = synchronized(storeLock) { MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING) } ?: return
        if (PackageUtils.getCurrentVersionCode(context) >= pending.versionCode) recordInstallSuccess(pending.releaseId)
    }

    /** 安装成功先持久化事件，再发布通知；两种系统信号共用同一待安装记录。 */
    private fun recordInstallSuccess(releaseId: String) {
        synchronized(storeLock) {
            val pending = MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING) ?: return
            if (pending.releaseId != releaseId || PackageUtils.getCurrentVersionCode(context) < pending.versionCode) return
            if (!pending.installReported) {
                enqueueEvent(releaseId, "install_succeeded", true, stableId = true)
                MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING)?.let {
                    MMKVUtils.putObject(KEY_PENDING, it.copy(installReported = true))
                }
            }
            val current = MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING) ?: return
            if (!current.successNotificationPosted) {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (launch != null && postNotification(SUCCESS_NOTIFICATION_ID, "APP 更新成功", "点击打开新版本", launch)) {
                    MMKVUtils.putObject(KEY_PENDING, current.copy(successNotificationPosted = true))
                }
            }
        }
        notificationManager.cancel(CONFIRM_NOTIFICATION_ID)
        _installOutcomes.tryEmit(ApkInstallOutcome(true))
        scope.launch { flushEvents() }
    }

    /** 主页面进入前台才是 launch_confirmed；接收广播启动的进程不会调用这里。 */
    fun onForegroundLaunch() {
        synchronized(storeLock) {
            val pending = MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING) ?: return
            if (PackageUtils.getCurrentVersionCode(context) < pending.versionCode) return
            if (!pending.installReported) {
                enqueueEvent(pending.releaseId, "install_succeeded", true, stableId = true)
            }
            enqueueEvent(pending.releaseId, "launch_confirmed", true, stableId = true)
            MMKVUtils.remove(KEY_PENDING)
        }
        notificationManager.cancel(SUCCESS_NOTIFICATION_ID)
        scope.launch { flushEvents() }
    }

    private val notificationManager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** 通知权限未授予时不抛异常；设置页已提前提示用户需手动打开应用。 */
    private fun postNotification(id: Int, title: String, message: String, activity: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching {
            notificationManager.createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "应用升级", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val action = PendingIntent.getActivity(
                context, id, activity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(action)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            notificationManager.notify(id, notification)
        }.isSuccess
    }

    /** 事件必须先入本地队列，再尝试联网；服务端暂时不可用不阻断下载或安装。 */
    private suspend fun report(releaseId: String, type: String, success: Boolean, message: String? = null, stableId: Boolean = false) {
        enqueueEvent(releaseId, type, success, message, stableId)
        flushEvents()
    }

    /** stableId 用于可能被系统回调和首次启动同时观察到的事件，防止重复入队。 */
    private fun enqueueEvent(releaseId: String, type: String, success: Boolean, message: String? = null, stableId: Boolean = false) {
        val detail = if (message == null) JsonObject(emptyMap()) else JsonObject(mapOf("message" to JsonPrimitive(message)))
        val event = ApkOtaEvent(
            eventId = if (stableId) stableEventId(releaseId, type) else nextEventId(), releaseId = releaseId,
            packageName = OTA_PACKAGE_NAME, installId = installId(),
            eventType = type, success = success, detail = detail,
            clientTime = OffsetDateTime.now().toString()
        )
        synchronized(storeLock) {
            val queue = MMKVUtils.getObject<List<ApkOtaEvent>>(KEY_EVENTS).orEmpty()
            if (queue.none { it.eventId == event.eventId }) MMKVUtils.putObject(KEY_EVENTS, queue + event)
        }
    }

    /**
     * 安装成功和首次启动的 ID 随待安装记录持久化。即使事件已发出但进程随即退出，
     * 再次观察到同一事件时仍使用原 ID，让服务端的幂等处理生效。
     */
    private fun stableEventId(releaseId: String, type: String): String = synchronized(storeLock) {
        val pending = MMKVUtils.getObject<PendingApkInstall>(KEY_PENDING)
        if (pending == null || pending.releaseId != releaseId) return@synchronized nextEventId()
        val existing = when (type) {
            "install_succeeded" -> pending.installSuccessEventId
            "launch_confirmed" -> pending.launchConfirmedEventId
            else -> null
        }
        if (existing != null) return@synchronized existing
        val id = nextEventId()
        val updated = when (type) {
            "install_succeeded" -> pending.copy(installSuccessEventId = id)
            "launch_confirmed" -> pending.copy(launchConfirmedEventId = id)
            else -> pending
        }
        MMKVUtils.putObject(KEY_PENDING, updated)
        id
    }

    /**
     * 生成 evt_yyyyMMdd_HHmmss 格式的本地时间 ID。同一秒内连续上报时推进逻辑秒值，
     * 并保存最后使用的秒数，避免当前设备在进程重启后重用 ID。
     */
    private fun nextEventId(): String = synchronized(storeLock) {
        val now = System.currentTimeMillis() / 1000
        val second = maxOf(now, MMKVUtils.getLong(KEY_LAST_EVENT_SECOND) + 1)
        MMKVUtils.putLong(KEY_LAST_EVENT_SECOND, second)
        EVENT_ID_FORMAT.format(Instant.ofEpochSecond(second))
    }

    /**
     * 按入队顺序逐条上报。仅收到 accepted 或 duplicate 才删除；失败时停在队首，
     * 下次检查更新或应用启动继续用原 eventId 重试。
     */
    private suspend fun flushEvents() = eventMutex.withLock {
        while (true) {
            val event = synchronized(storeLock) { MMKVUtils.getObject<List<ApkOtaEvent>>(KEY_EVENTS).orEmpty().firstOrNull() } ?: break
            val accepted = try {
                // 旧队列可能留有先前的包名；本次请求统一使用当前约定的 OTA 包名。
                val response = service.reportEvent("$OTA_BASE/api/ota/v1/events", event.copy(packageName = OTA_PACKAGE_NAME))
                response.isSuccessful && (response.body()?.accepted == true || response.body()?.duplicate == true)
            } catch (_: Exception) { false }
            if (!accepted) break
            synchronized(storeLock) {
                val queue = MMKVUtils.getObject<List<ApkOtaEvent>>(KEY_EVENTS).orEmpty()
                MMKVUtils.putObject(KEY_EVENTS, queue.filterNot { it.eventId == event.eventId })
            }
        }
    }

    companion object {
        private const val OTA_BASE = "https://ota.4digitalcenter.cloud:8443"
        private const val OTA_PACKAGE_NAME = "com.sinelynx.grindingrobot"
        private const val KEY_INSTALL_ID = "apk_ota_install_id"
        private const val KEY_PENDING = "apk_ota_pending_install"
        private const val KEY_INSTALL_FAILURE = "apk_ota_last_install_failure"
        private const val KEY_EVENTS = "apk_ota_pending_events"
        private const val KEY_LAST_EVENT_SECOND = "apk_ota_last_event_second"
        private val EVENT_ID_FORMAT = DateTimeFormatter.ofPattern("'evt_'yyyyMMdd_HHmmss", Locale.ROOT)
            .withZone(ZoneId.systemDefault())
        const val ACTION_INSTALL_RESULT = "com.sinelynx.grindingrobot.APK_OTA_INSTALL_RESULT"
        const val EXTRA_RELEASE_ID = "releaseId"
        private const val NOTIFICATION_CHANNEL_ID = "apk_ota"
        private const val CONFIRM_NOTIFICATION_ID = 4001
        private const val SUCCESS_NOTIFICATION_ID = 4002
    }
}
