package com.sinelynx.grindingrobot.core.data.state

import com.google.gson.Gson
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import com.sinelynx.grindingrobot.core.util.storage.MMKVUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeviceEventLevel { NORMAL, WARNING, ERROR }

data class DeviceEventRecord(
    val occurredAtMillis: Long,
    val title: String,
    val level: DeviceEventLevel
)

/** Application-wide history; collection never depends on the message popup being visible. */
object DeviceEventHistory {
    private const val KEY = "device_event_history_v1"
    private const val LIMIT = 500
    private val gson = Gson()
    private val records by lazy {
        val saved = runCatching {
            gson.fromJson(MMKVUtils.getString(KEY, "[]"), Array<DeviceEventRecord>::class.java)
                ?.toList().orEmpty().take(LIMIT)
        }.getOrElse {
            LogUtils.w("读取设备消息历史失败: ${it.message}")
            emptyList()
        }
        MutableStateFlow(saved)
    }
    val events by lazy { records.asStateFlow() }

    @Synchronized
    fun clear(): Boolean = runCatching {
        MMKVUtils.putString(KEY, "[]")
        records.value = emptyList()
        true
    }.getOrElse {
        LogUtils.w("清空设备消息历史失败: ${it.message}")
        false
    }

    @Synchronized
    fun append(level: DeviceEventLevel, title: String) {
        val next = (listOf(DeviceEventRecord(System.currentTimeMillis(), title, level)) + records.value).take(LIMIT)
        records.value = next
        runCatching { MMKVUtils.putString(KEY, gson.toJson(next)) }
            .onFailure { LogUtils.w("保存设备消息历史失败: ${it.message}") }
    }
}
