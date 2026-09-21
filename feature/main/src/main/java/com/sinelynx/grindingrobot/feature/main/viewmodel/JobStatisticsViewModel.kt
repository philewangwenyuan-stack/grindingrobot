package com.sinelynx.grindingrobot.feature.main.viewmodel

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.common.base.viewmodel.BaseViewModel
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.TaskSchedulerStream
import com.sinelynx.grindingrobot.core.model.state.MapCatalogStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// 与 feature.map 的 MapRequestConfig 保持一致。
private const val MAP_TCP_REQUEST_TIMEOUT_MS = 180_000L

data class MapSelectItem(
    val mapId: String,
    val mapName: String,
    val isSelected: Boolean = false
)

// 柱状图单条数据点
data class ChartDataPoint(
    val label: String,    // 横轴标签，例如 "2026-01"
    val value: Float      // 纵轴数值（面积 m² 或 时长 小时）
)

// 单个任务维度的统计结果
data class TaskStatisticsItem(
    val taskName: String,
    val taskCount: Int,                           // 任务次数
    val totalArea: Float,                         // 作业面积 (m²)
    val totalDurationHours: Float,                // 作业时长 (小时)
    val mapNames: String,                         // 作业地图名称
    val utilizationRate: Float,                   // 稼动率 (%)
    val areaChartData: List<ChartDataPoint>,      // 效率趋势（面积）
    val durationChartData: List<ChartDataPoint>,   // 时长分布
    val invalidDurationCount: Int = 0
)

// 弹窗展示的完整统计结果
data class JobStatisticsResult(
    val dateRange: String,                        // "2026-05-18 ~ 2026-06-18"
    val mapNames: String,                         // "地图1、地图2"
    val tasks: List<TaskStatisticsItem>,          // 按任务分组的统计数据
    val selectedTaskIndex: Int = 0,               // 当前选中 Tab 索引
    // 汇总指标（全部任务聚合）
    val totalTaskCount: Int = 0,
    val totalArea: Float = 0f,
    val totalDurationHours: Float = 0f,
    val totalMapNames: String = "",
    val utilizationRate: Float = 0f
)

data class JobStatisticsUiState(
    val maps: List<MapSelectItem> = emptyList(),
    val selectedMapIds: Set<String> = emptySet(),
    val startDate: Long? = null,
    val endDate: Long? = null,
    val showDatePickerStart: Boolean = false,
    val showDatePickerEnd: Boolean = false,
    val isLoading: Boolean = false,
    val isMapCatalogLoading: Boolean = false,
    val hasQueried: Boolean = false,
    val totalTasks: Int = 0,
    val totalArea: Float = 0f,
    val totalDurationMinutes: Long = 0L,
    val averageEfficiency: Float = 0f,
    // 弹窗相关状态
    val showResultDialog: Boolean = false,
    val statisticsResult: JobStatisticsResult? = null
)

@HiltViewModel
class JobStatisticsViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    navigator: AppNavigator,
    appState: AppState
) : BaseViewModel(navigator, appState) {

    private val _uiState = MutableStateFlow(JobStatisticsUiState())
    val uiState: StateFlow<JobStatisticsUiState> = _uiState.asStateFlow()
    private var mapCatalogTimeoutJob: Job? = null

    private var historyTimeoutJob: Job? = null
    private var pendingQuery: JobStatisticsQuery? = null

    init {
        val today = LocalDate.now()
        _uiState.update {
            it.copy(
                startDate = datePickerMillis(today.withDayOfMonth(1)),
                endDate = datePickerMillis(today)
            )
        }
        observeMapCatalog()
        viewModelScope.launch {
            TaskSchedulerStream.taskExecutionHistory.collect { payload ->
                val query = pendingQuery ?: return@collect
                payload ?: return@collect
                if (!query.accepts(payload)) return@collect
                if (!payload.isSuccess) {
                    finishHistoryQuery(payload.message.ifBlank { "统计查询失败" })
                    return@collect
                }
                val result = calculateJobStatistics(query, payload.records)
                finishHistoryQuery()
                _uiState.update {
                    it.copy(
                        hasQueried = true,
                        totalTasks = result.totalTaskCount,
                        totalArea = result.totalArea,
                        totalDurationMinutes = (result.totalDurationHours * 60).toLong(),
                        averageEfficiency = if (result.totalDurationHours > 0)
                            result.totalArea / result.totalDurationHours else 0f,
                        showResultDialog = true,
                        statisticsResult = result
                    )
                }
            }
        }
    }

    private fun observeMapCatalog() {
        viewModelScope.launch {
            MapCatalogStream.payload.collect { payload ->
                payload ?: return@collect
                mapCatalogTimeoutJob?.cancel()
                if (!payload.isSuccess) {
                    val wasLoading = _uiState.value.isMapCatalogLoading
                    _uiState.update { it.copy(isMapCatalogLoading = false) }
                    if (wasLoading) ToastUtils.show("获取地图列表失败")
                    return@collect
                }
                val incomingItems = payload.items.map { item ->
                    MapSelectItem(
                        mapId = item.mapId,
                        mapName = item.name.ifBlank { "暂无名称" }
                    )
                }
                _uiState.update { state ->
                    val selected = state.selectedMapIds.intersect(incomingItems.map { it.mapId }.toSet())
                    state.copy(
                        maps = incomingItems.map { it.copy(isSelected = it.mapId in selected) },
                        selectedMapIds = selected,
                        isMapCatalogLoading = false
                    )
                }
            }
        }
    }

    fun requestMapCatalogOnEnter() {
        mapCatalogTimeoutJob?.cancel()
        MapCatalogStream.reset()
        _uiState.update { it.copy(isMapCatalogLoading = true) }
        if (!tcpManager.requestMapCatalog()) {
            _uiState.update { it.copy(isMapCatalogLoading = false) }
            ToastUtils.show("获取地图列表失败")
            return
        }
        mapCatalogTimeoutJob = viewModelScope.launch {
            delay(MAP_TCP_REQUEST_TIMEOUT_MS)
            if (_uiState.value.isMapCatalogLoading) {
                _uiState.update { it.copy(isMapCatalogLoading = false) }
                ToastUtils.show("获取地图列表失败")
            }
        }
    }

    fun toggleMapSelection(mapId: String) {
        _uiState.update { state ->
            if (state.maps.none { it.mapId == mapId }) return@update state
            val updatedSelected = state.selectedMapIds.toMutableSet()
            if (updatedSelected.contains(mapId)) {
                updatedSelected.remove(mapId)
            } else {
                updatedSelected.add(mapId)
            }
            val updatedMaps = state.maps.map {
                it.copy(isSelected = updatedSelected.contains(it.mapId))
            }
            state.copy(maps = updatedMaps, selectedMapIds = updatedSelected)
        }
    }

    fun clearMapSelection() {
        _uiState.update { state ->
            val updatedMaps = state.maps.map { it.copy(isSelected = false) }
            state.copy(maps = updatedMaps, selectedMapIds = emptySet())
        }
    }

    fun selectAllMaps() {
        _uiState.update { state ->
            val allIds = state.maps.map { it.mapId }.toSet()
            val updatedMaps = state.maps.map { it.copy(isSelected = true) }
            state.copy(maps = updatedMaps, selectedMapIds = allIds)
        }
    }

    fun setStartDate(timestamp: Long?) {
        _uiState.update { it.copy(startDate = timestamp, showDatePickerStart = false) }
    }

    fun setEndDate(timestamp: Long?) {
        _uiState.update { it.copy(endDate = timestamp, showDatePickerEnd = false) }
    }

    fun showDatePickerStart(show: Boolean) {
        _uiState.update { it.copy(showDatePickerStart = show) }
    }

    fun showDatePickerEnd(show: Boolean) {
        _uiState.update { it.copy(showDatePickerEnd = show) }
    }

    fun onQuery() {
        val state = _uiState.value

        // 校验
        if (state.selectedMapIds.isEmpty()) {
            ToastUtils.showError("请选择地图")
            return
        }
        if (state.startDate == null || state.endDate == null) {
            ToastUtils.showError("请选择有效的起止时间")
            return
        }
        if (state.startDate > state.endDate) {
            ToastUtils.showError("开始时间不能晚于结束时间")
            return
        }

        if (state.isLoading) return
        if (!TaskHistoryRequestGate.acquire(this)) {
            ToastUtils.showError("任务历史正在查询，请稍后重试")
            return
        }
        val query = JobStatisticsQuery(
            datePickerDate(state.startDate),
            datePickerDate(state.endDate),
            ZoneId.systemDefault(),
            state.maps.filter { it.mapId in state.selectedMapIds }.associate { it.mapId to it.mapName }
        )
        pendingQuery = query
        _uiState.update { it.copy(isLoading = true, showResultDialog = false, statisticsResult = null) }
        TaskSchedulerStream.resetTaskExecutionHistory()
        if (!tcpManager.requestTaskExecutionHistory(startTime = query.startSeconds, endTime = query.endSeconds)) {
            finishHistoryQuery("统计请求发送失败")
            return
        }
        historyTimeoutJob = viewModelScope.launch {
            delay(10_000L)
            if (pendingQuery != null) finishHistoryQuery("统计查询超时，请重试")
        }
    }

    private fun finishHistoryQuery(error: String? = null) {
        historyTimeoutJob?.cancel()
        pendingQuery = null
        TaskHistoryRequestGate.release(this)
        _uiState.update { it.copy(isLoading = false) }
        error?.let { ToastUtils.showError(it) }
    }

    fun cancelHistoryQuery() = finishHistoryQuery()

    override fun onCleared() {
        TaskHistoryRequestGate.release(this)
        super.onCleared()
    }

    fun selectTask(index: Int) {
        _uiState.update { state ->
            val result = state.statisticsResult ?: return@update state
            state.copy(statisticsResult = result.copy(selectedTaskIndex = index))
        }
    }

    fun dismissResultDialog() {
        _uiState.update { it.copy(showResultDialog = false) }
    }

    fun exportData(context: Context) {
        val result = _uiState.value.statisticsResult ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val csv = jobStatisticsCsv(result)
                    val fileName = "作业统计_${result.dateRange.replace(" ~ ", "_").replace("-", "")}.csv"
                    saveCsvToDownloads(context, fileName, csv)
                }
                ToastUtils.showSuccess("导出成功，文件已保存至下载目录")
            }.onFailure {
                ToastUtils.showError("导出失败：${it.message}")
            }
        }
    }

    private fun saveCsvToDownloads(context: Context, fileName: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建文件")
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // 写入 UTF-8 BOM 以确保 Excel 正确识别编码
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
        } else {
            // Android 9 以下使用直接文件写入
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
        }
    }

}
