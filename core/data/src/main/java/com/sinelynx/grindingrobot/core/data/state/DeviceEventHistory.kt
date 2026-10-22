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
    val level: DeviceEventLevel,
    val eventId: Long = 0L
)

internal fun hasUnreadDeviceEvents(events: List<DeviceEventRecord>, readThrough: Long): Boolean =
    events.any { it.eventId > readThrough }

internal fun nextDeviceEventId(now: Long, latestId: Long, readThrough: Long): Long =
    maxOf(now, latestId + 1L, readThrough + 1L)

/** Application-wide history; collection never depends on the message popup being visible. */
object DeviceEventHistory {
    private const val KEY = "device_event_history_v1"
    private const val READ_KEY = "device_event_read_through_v1"
    private const val BATTERY_KEY = "device_event_battery_tier_v1"
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
        // Upgrade old records without IDs deterministically, including equal timestamps.
        val legacyBase = saved.maxOfOrNull { it.occurredAtMillis } ?: 0L
        MutableStateFlow(saved.mapIndexed { index, event ->
            if (event.eventId > 0L) event else event.copy(eventId = legacyBase + saved.size - index)
        })
    }
    val events by lazy { records.asStateFlow() }
    private var readThrough = 0L
    private val unreadState by lazy {
        readThrough = runCatching { MMKVUtils.getLong(READ_KEY, 0L) }.getOrDefault(0L)
        MutableStateFlow(hasUnreadDeviceEvents(records.value, readThrough))
    }
    val hasUnread by lazy { unreadState.asStateFlow() }
    private var batteryTier: Int? = null

    @Synchronized
    fun markReadThrough(eventId: Long) {
        val unread = unreadState // Initialize the persisted cursor before comparing.
        if (eventId > readThrough) {
            readThrough = eventId
            runCatching { MMKVUtils.putLong(READ_KEY, readThrough) }
                .onFailure { LogUtils.w("保存消息已读位置失败: ${it.message}") }
        }
        unread.value = hasUnreadDeviceEvents(records.value, readThrough)
    }

    /** A battery warning is a new event only when entering a new low-battery tier. */
    @Synchronized
    fun reportBatteryLevel(percent: Int) {
        if (percent !in 0..100) return
        val tier = when { percent < 10 -> 2; percent < 20 -> 1; else -> 0 }
        val previous = batteryTier ?: runCatching { MMKVUtils.getLong(BATTERY_KEY, -1L).toInt() }.getOrDefault(-1)
        if (previous == tier) return
        batteryTier = tier
        runCatching { MMKVUtils.putLong(BATTERY_KEY, tier.toLong()) }
        when (tier) {
            1 -> append(DeviceEventLevel.WARNING, "设备电量过低($percent%)")
            2 -> append(DeviceEventLevel.ERROR, "设备电量严重不足($percent%)")
        }
    }

    @Synchronized
    fun clear(): Boolean = runCatching {
        MMKVUtils.putString(KEY, "[]")
        records.value = emptyList()
        unreadState.value = false
        true
    }.getOrElse {
        LogUtils.w("清空设备消息历史失败: ${it.message}")
        false
    }

    @Synchronized
    fun append(level: DeviceEventLevel, title: String) {
        val unread = unreadState
        val now = System.currentTimeMillis()
        val id = nextDeviceEventId(now, records.value.maxOfOrNull { it.eventId } ?: 0L, readThrough)
        val next = (listOf(DeviceEventRecord(now, title, level, id)) + records.value).take(LIMIT)
        records.value = next
        unread.value = hasUnreadDeviceEvents(next, readThrough)
        runCatching { MMKVUtils.putString(KEY, gson.toJson(next)) }
            .onFailure { LogUtils.w("保存设备消息历史失败: ${it.message}") }
    }
}
