package com.sinelynx.grindingrobot.feature.main.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sinelynx.grindingrobot.core.data.state.AppState
import com.sinelynx.grindingrobot.core.model.state.SystemCacheClearPayload
import com.sinelynx.grindingrobot.core.model.state.SystemCacheClearStream
import com.sinelynx.grindingrobot.core.tcp.TcpManager
import com.sinelynx.grindingrobot.core.util.toast.ToastUtils
import com.sinelynx.grindingrobot.core.model.response.ota.ApkOtaRelease
import com.sinelynx.grindingrobot.feature.main.ota.ApkOtaManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sl_link.SlLink
import java.util.Locale

data class FootprintPointUi(
    val x: String,
    val y: String
)

data class RppSettingsUiState(
    val desiredLinearVel: String = "0.06",
    val maxLinearVel: String = "0.15",
    val maxAngularVel: String = "0.25",
    val maxLinearAccel: String = "0.40",
    val maxAngularAccel: String = "0.40",
    val lookaheadDist: String = "0.80",
    val minLookaheadDist: String = "0.30",
    val maxLookaheadDist: String = "1.20",
    val curvatureLookaheadDist: String = "0.45",
    val regulatedLinearScalingMinRadius: String = "0.01",
    val regulatedLinearScalingMinSpeed: String = "0.01",
    val collisionFrontClearanceM: String = "0.25",
    val collisionSideClearanceM: String = "0.10",
    val maxAllowedTimeToCollision: String = "5.0",
    val collisionConfirmScans: String = "15",
    val collisionClearConfirmScans: String = "3",
    val rotateToHeadingAngularVel: String = "0.20",
    val rotateToHeadingMinAngle: String = "0.45",
    val goalDistTol: String = "0.12",
    val angleTol: String = "0.05",
    val lookaheadTime: String = "1.50",
    val approachVelocityScalingDist: String = "0.60",
    val minApproachLinearVelocity: String = "0.05",
    val collisionStopDistanceM: String = "0.50",
    val collisionScanHalfAngleRad: String = "0.35",
    val collisionScanTimeoutS: String = "0.50",
    val collisionMinValidPoints: String = "3",
    val maxRobotPoseSearchDist: String = "10.0",
    val transStoppedVel: String = "0.02",
    val thetaStoppedVel: String = "0.10",
    val collisionSideTurningMinAngularVel: String = "0.005",
    val rotateToHeadingExitAngle: String = "0.05",
    val rotateToHeadingStableCycles: String = "5",
    val rotateToHeadingCornerDistance: String = "0.08",
    val useCollisionDetection: Boolean = true,
    val useFootprintExpansionCollisionDetection: Boolean = true,
    val useFixedDistanceCollisionDetection: Boolean = true,
    val useRotateToHeading: Boolean = true,
    val allowReversing: Boolean = false,
    val useFixedCurvatureLookahead: Boolean = true,
    val useRegulatedLinearVelocityScaling: Boolean = true,
    val useCostRegulatedLinearVelocityScaling: Boolean = true,
    val useVelocityScaledLookaheadDist: Boolean = false,
    val useApproachVelocityScaling: Boolean = true,
    val useCommandVelocityForAccelLimit: Boolean = true,
    val useCommandAngularVelocityForAccelLimit: Boolean = true,
    val collisionSideOnlyWhenTurning: Boolean = true,
    val useCornerAwareRotateToHeading: Boolean = true
)

data class PathPlanningSettingsUiState(
    val inflationRadius: String = "0.60",
    val endpointMargin: String = "2.00",
    val outputPointSpacing: String = "0.30",
    val alignedObstacleInflation: String = "0.70",
    val alignedObstacleMaxExtent: String = "3.00",
    val obstacleCornerAngleDeg: String = "45.0",
    val obstacleAvoidanceDistance: String = "0.00"
)

enum class BaseLaserField { X, Y, Z, ROLL, PITCH, YAW }

enum class RppTextField {
    DESIRED_LINEAR_VEL, MAX_LINEAR_VEL, MAX_ANGULAR_VEL, MAX_LINEAR_ACCEL, MAX_ANGULAR_ACCEL,
    LOOKAHEAD_DIST, MIN_LOOKAHEAD_DIST, MAX_LOOKAHEAD_DIST, CURVATURE_LOOKAHEAD_DIST,
    SCALING_MIN_RADIUS, SCALING_MIN_SPEED, FRONT_CLEARANCE, SIDE_CLEARANCE,
    MAX_TIME_TO_COLLISION, COLLISION_CONFIRM_SCANS, CLEAR_CONFIRM_SCANS,
    ROTATE_ANGULAR_VEL, ROTATE_MIN_ANGLE, GOAL_DIST_TOL, ANGLE_TOL, LOOKAHEAD_TIME,
    APPROACH_SCALING_DIST, MIN_APPROACH_SPEED, COLLISION_STOP_DISTANCE,
    COLLISION_HALF_ANGLE, COLLISION_SCAN_TIMEOUT, COLLISION_MIN_POINTS,
    MAX_POSE_SEARCH_DIST, TRANS_STOPPED_VEL, THETA_STOPPED_VEL,
    SIDE_TURNING_MIN_ANGULAR_VEL, ROTATE_EXIT_ANGLE, ROTATE_STABLE_CYCLES,
    ROTATE_CORNER_DISTANCE
}

enum class RppToggleField {
    COLLISION_DETECTION, FOOTPRINT_COLLISION, FIXED_DISTANCE_COLLISION, ROTATE_TO_HEADING,
    ALLOW_REVERSING, FIXED_CURVATURE_LOOKAHEAD, REGULATED_SCALING, COST_SCALING,
    VELOCITY_SCALED_LOOKAHEAD, APPROACH_SCALING, COMMAND_VELOCITY_ACCEL,
    COMMAND_ANGULAR_ACCEL, SIDE_ONLY_WHEN_TURNING, CORNER_AWARE_ROTATE
}

enum class PathPlanningTextField {
    INFLATION_RADIUS,
    ENDPOINT_MARGIN,
    OUTPUT_POINT_SPACING,
    ALIGNED_OBSTACLE_INFLATION,
    ALIGNED_OBSTACLE_MAX_EXTENT,
    OBSTACLE_CORNER_ANGLE_DEG,
    OBSTACLE_AVOIDANCE_DISTANCE
}

/** 设置页状态：升级检查结果与下载/安装反馈同机器人参数、缓存清理状态一起驱动页面。 */
data class RobotSettingsUiState(
    val robotWidth: Double = 1.0,
    val robotLength: Double = 1.0,
    val footprint: List<FootprintPointUi> = defaultFootprint(),
    val baseLaserX: String = "0.00",
    val baseLaserY: String = "0.00",
    val baseLaserZ: String = "0.13",
    val baseLaserRoll: String = "0.00",
    val baseLaserPitch: String = "0.00",
    val baseLaserYaw: String = "0.00",
    val rpp: RppSettingsUiState = RppSettingsUiState(),
    val pathPlanning: PathPlanningSettingsUiState = PathPlanningSettingsUiState(),
    val geometryRequiresRestart: Boolean = false,
    val isDownloading: Boolean = false,
    val isAwaitingInstall: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val showUpdateFailedDialog: Boolean = false,
    val updateError: String? = null,
    // 保留当前选中的服务端版本，用户点击更新或失败重试时不使用旧的硬编码下载地址。
    val availableRelease: ApkOtaRelease? = null,
    val updateRequired: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val showClearCacheConfirmDialog: Boolean = false,
    val isClearingCache: Boolean = false
)

private fun defaultFootprint() = listOf(
    FootprintPointUi("-0.60", "0.47"),
    FootprintPointUi("1.00", "0.47"),
    FootprintPointUi("1.00", "-0.47"),
    FootprintPointUi("-0.60", "-0.47")
)

private fun formatRppFloat(value: Float): String =
    String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

private fun SlLink.RppSettings.toUiState(): RppSettingsUiState = RppSettingsUiState(
    desiredLinearVel = formatRppFloat(desiredLinearVel),
    maxLinearVel = formatRppFloat(maxLinearVel),
    maxAngularVel = formatRppFloat(maxAngularVel),
    maxLinearAccel = formatRppFloat(maxLinearAccel),
    maxAngularAccel = formatRppFloat(maxAngularAccel),
    lookaheadDist = formatRppFloat(lookaheadDist),
    minLookaheadDist = formatRppFloat(minLookaheadDist),
    maxLookaheadDist = formatRppFloat(maxLookaheadDist),
    curvatureLookaheadDist = formatRppFloat(curvatureLookaheadDist),
    regulatedLinearScalingMinRadius = formatRppFloat(regulatedLinearScalingMinRadius),
    regulatedLinearScalingMinSpeed = formatRppFloat(regulatedLinearScalingMinSpeed),
    collisionFrontClearanceM = formatRppFloat(collisionFrontClearanceM),
    collisionSideClearanceM = formatRppFloat(collisionSideClearanceM),
    maxAllowedTimeToCollision = formatRppFloat(maxAllowedTimeToCollisionUpToCarrot),
    collisionConfirmScans = collisionConfirmScans.toString(),
    collisionClearConfirmScans = collisionClearConfirmScans.toString(),
    rotateToHeadingAngularVel = formatRppFloat(rotateToHeadingAngularVel),
    rotateToHeadingMinAngle = formatRppFloat(rotateToHeadingMinAngle),
    goalDistTol = formatRppFloat(goalDistTol),
    angleTol = formatRppFloat(angleTol),
    lookaheadTime = formatRppFloat(lookaheadTime),
    approachVelocityScalingDist = formatRppFloat(approachVelocityScalingDist),
    minApproachLinearVelocity = formatRppFloat(minApproachLinearVelocity),
    collisionStopDistanceM = formatRppFloat(collisionStopDistanceM),
    collisionScanHalfAngleRad = formatRppFloat(collisionScanHalfAngleRad),
    collisionScanTimeoutS = formatRppFloat(collisionScanTimeoutS),
    collisionMinValidPoints = collisionMinValidPoints.toString(),
    maxRobotPoseSearchDist = formatRppFloat(maxRobotPoseSearchDist),
    transStoppedVel = formatRppFloat(transStoppedVel),
    thetaStoppedVel = formatRppFloat(thetaStoppedVel),
    collisionSideTurningMinAngularVel = formatRppFloat(collisionSideTurningMinAngularVel),
    rotateToHeadingExitAngle = formatRppFloat(rotateToHeadingExitAngle),
    rotateToHeadingStableCycles = rotateToHeadingStableCycles.toString(),
    rotateToHeadingCornerDistance = formatRppFloat(rotateToHeadingCornerDistance),
    useCollisionDetection = useCollisionDetection,
    useFootprintExpansionCollisionDetection = useFootprintExpansionCollisionDetection,
    useFixedDistanceCollisionDetection = useFixedDistanceCollisionDetection,
    useRotateToHeading = useRotateToHeading,
    allowReversing = allowReversing,
    useFixedCurvatureLookahead = useFixedCurvatureLookahead,
    useRegulatedLinearVelocityScaling = useRegulatedLinearVelocityScaling,
    useCostRegulatedLinearVelocityScaling = useCostRegulatedLinearVelocityScaling,
    useVelocityScaledLookaheadDist = useVelocityScaledLookaheadDist,
    useApproachVelocityScaling = useApproachVelocityScaling,
    useCommandVelocityForAccelLimit = useCommandVelocityForAccelLimit,
    useCommandAngularVelocityForAccelLimit = useCommandAngularVelocityForAccelLimit,
    collisionSideOnlyWhenTurning = collisionSideOnlyWhenTurning,
    useCornerAwareRotateToHeading = useCornerAwareRotateToHeading
)

private fun RppSettingsUiState.updateText(field: RppTextField, value: String): RppSettingsUiState = when (field) {
    RppTextField.DESIRED_LINEAR_VEL -> copy(desiredLinearVel = value)
    RppTextField.MAX_LINEAR_VEL -> copy(maxLinearVel = value)
    RppTextField.MAX_ANGULAR_VEL -> copy(maxAngularVel = value)
    RppTextField.MAX_LINEAR_ACCEL -> copy(maxLinearAccel = value)
    RppTextField.MAX_ANGULAR_ACCEL -> copy(maxAngularAccel = value)
    RppTextField.LOOKAHEAD_DIST -> copy(lookaheadDist = value)
    RppTextField.MIN_LOOKAHEAD_DIST -> copy(minLookaheadDist = value)
    RppTextField.MAX_LOOKAHEAD_DIST -> copy(maxLookaheadDist = value)
    RppTextField.CURVATURE_LOOKAHEAD_DIST -> copy(curvatureLookaheadDist = value)
    RppTextField.SCALING_MIN_RADIUS -> copy(regulatedLinearScalingMinRadius = value)
    RppTextField.SCALING_MIN_SPEED -> copy(regulatedLinearScalingMinSpeed = value)
    RppTextField.FRONT_CLEARANCE -> copy(collisionFrontClearanceM = value)
    RppTextField.SIDE_CLEARANCE -> copy(collisionSideClearanceM = value)
    RppTextField.MAX_TIME_TO_COLLISION -> copy(maxAllowedTimeToCollision = value)
    RppTextField.COLLISION_CONFIRM_SCANS -> copy(collisionConfirmScans = value)
    RppTextField.CLEAR_CONFIRM_SCANS -> copy(collisionClearConfirmScans = value)
    RppTextField.ROTATE_ANGULAR_VEL -> copy(rotateToHeadingAngularVel = value)
    RppTextField.ROTATE_MIN_ANGLE -> copy(rotateToHeadingMinAngle = value)
    RppTextField.GOAL_DIST_TOL -> copy(goalDistTol = value)
    RppTextField.ANGLE_TOL -> copy(angleTol = value)
    RppTextField.LOOKAHEAD_TIME -> copy(lookaheadTime = value)
    RppTextField.APPROACH_SCALING_DIST -> copy(approachVelocityScalingDist = value)
    RppTextField.MIN_APPROACH_SPEED -> copy(minApproachLinearVelocity = value)
    RppTextField.COLLISION_STOP_DISTANCE -> copy(collisionStopDistanceM = value)
    RppTextField.COLLISION_HALF_ANGLE -> copy(collisionScanHalfAngleRad = value)
    RppTextField.COLLISION_SCAN_TIMEOUT -> copy(collisionScanTimeoutS = value)
    RppTextField.COLLISION_MIN_POINTS -> copy(collisionMinValidPoints = value)
    RppTextField.MAX_POSE_SEARCH_DIST -> copy(maxRobotPoseSearchDist = value)
    RppTextField.TRANS_STOPPED_VEL -> copy(transStoppedVel = value)
    RppTextField.THETA_STOPPED_VEL -> copy(thetaStoppedVel = value)
    RppTextField.SIDE_TURNING_MIN_ANGULAR_VEL -> copy(collisionSideTurningMinAngularVel = value)
    RppTextField.ROTATE_EXIT_ANGLE -> copy(rotateToHeadingExitAngle = value)
    RppTextField.ROTATE_STABLE_CYCLES -> copy(rotateToHeadingStableCycles = value)
    RppTextField.ROTATE_CORNER_DISTANCE -> copy(rotateToHeadingCornerDistance = value)
}

private fun RppSettingsUiState.updateToggle(field: RppToggleField, value: Boolean): RppSettingsUiState = when (field) {
    RppToggleField.COLLISION_DETECTION -> copy(useCollisionDetection = value)
    RppToggleField.FOOTPRINT_COLLISION -> copy(useFootprintExpansionCollisionDetection = value)
    RppToggleField.FIXED_DISTANCE_COLLISION -> copy(useFixedDistanceCollisionDetection = value)
    RppToggleField.ROTATE_TO_HEADING -> copy(useRotateToHeading = value)
    RppToggleField.ALLOW_REVERSING -> copy(allowReversing = value)
    RppToggleField.FIXED_CURVATURE_LOOKAHEAD -> copy(useFixedCurvatureLookahead = value)
    RppToggleField.REGULATED_SCALING -> copy(useRegulatedLinearVelocityScaling = value)
    RppToggleField.COST_SCALING -> copy(useCostRegulatedLinearVelocityScaling = value)
    RppToggleField.VELOCITY_SCALED_LOOKAHEAD -> copy(useVelocityScaledLookaheadDist = value)
    RppToggleField.APPROACH_SCALING -> copy(useApproachVelocityScaling = value)
    RppToggleField.COMMAND_VELOCITY_ACCEL -> copy(useCommandVelocityForAccelLimit = value)
    RppToggleField.COMMAND_ANGULAR_ACCEL -> copy(useCommandAngularVelocityForAccelLimit = value)
    RppToggleField.SIDE_ONLY_WHEN_TURNING -> copy(collisionSideOnlyWhenTurning = value)
    RppToggleField.CORNER_AWARE_ROTATE -> copy(useCornerAwareRotateToHeading = value)
}

private fun SlLink.MapSettings.toPathPlanningUiState(): PathPlanningSettingsUiState =
    PathPlanningSettingsUiState(
        inflationRadius = formatRppFloat(inflationRadius.takeIf { it > 0f } ?: 0.6f),
        endpointMargin = formatRppFloat(if (hasEndpointMargin()) endpointMargin else 2.0f),
        outputPointSpacing = formatRppFloat(if (hasOutputPointSpacing()) outputPointSpacing else 0.3f),
        alignedObstacleInflation = formatRppFloat(
            if (hasAlignedObstacleInflation()) alignedObstacleInflation else 0.7f
        ),
        alignedObstacleMaxExtent = formatRppFloat(
            if (hasAlignedObstacleMaxExtent()) alignedObstacleMaxExtent else 3.0f
        ),
        obstacleCornerAngleDeg = formatRppFloat(
            if (hasObstacleCornerAngleDeg()) obstacleCornerAngleDeg else 45.0f
        ),
        obstacleAvoidanceDistance = formatRppFloat(
            if (hasObstacleAvoidanceDistance()) obstacleAvoidanceDistance else 0.0f
        )
    )

private fun PathPlanningSettingsUiState.updateText(
    field: PathPlanningTextField,
    value: String
): PathPlanningSettingsUiState = when (field) {
    PathPlanningTextField.INFLATION_RADIUS -> copy(inflationRadius = value)
    PathPlanningTextField.ENDPOINT_MARGIN -> copy(endpointMargin = value)
    PathPlanningTextField.OUTPUT_POINT_SPACING -> copy(outputPointSpacing = value)
    PathPlanningTextField.ALIGNED_OBSTACLE_INFLATION -> copy(alignedObstacleInflation = value)
    PathPlanningTextField.ALIGNED_OBSTACLE_MAX_EXTENT -> copy(alignedObstacleMaxExtent = value)
    PathPlanningTextField.OBSTACLE_CORNER_ANGLE_DEG -> copy(obstacleCornerAngleDeg = value)
    PathPlanningTextField.OBSTACLE_AVOIDANCE_DISTANCE -> copy(obstacleAvoidanceDistance = value)
}

internal enum class ClearCacheCategory {
    MemoryCache,
    TemporaryFiles,
    Logs
}

@HiltViewModel
class RobotSettingsViewModel @Inject constructor(
    private val tcpManager: TcpManager,
    private val appState: AppState,
    private val apkOtaManager: ApkOtaManager
) : ViewModel() {

    private val initialInstallFailure = apkOtaManager.consumeInstallFailure()
    private val _uiState = MutableStateFlow(RobotSettingsUiState(
        isAwaitingInstall = apkOtaManager.hasPendingInstall(),
        updateError = initialInstallFailure,
        showUpdateFailedDialog = initialInstallFailure != null
    ))
    val uiState: StateFlow<RobotSettingsUiState> = _uiState.asStateFlow()
    private val settingWriteRequests = MutableSharedFlow<RobotSettingsUiState>(
        extraBufferCapacity = 1
    )
    private var clearCacheTimeoutJob: Job? = null

    init {
        // 会话提交只是进入等待；系统终态才允许恢复操作或提示失败。
        viewModelScope.launch {
            apkOtaManager.installOutcomes.collect { outcome ->
                if (!outcome.success) apkOtaManager.consumeInstallFailure()
                _uiState.update {
                    if (outcome.success) it.copy(isAwaitingInstall = false)
                    else it.copy(isAwaitingInstall = false, showUpdateFailedDialog = true,
                        updateError = outcome.message ?: "系统安装失败")
                }
            }
        }
        viewModelScope.launch {
            appState.robotSettings
                .filterNotNull()
                .collect { settings ->
                    _uiState.update { current ->
                        current.copy(
                            robotWidth = settings.robotWidth,
                            robotLength = settings.robotLength
                        )
                    }
            }
        }
        viewModelScope.launch {
            appState.mapSettings
                .filterNotNull()
                .collect { applyMapSettings(it) }
        }
        viewModelScope.launch {
            appState.rppSettings
                .filterNotNull()
                .collect { settings ->
                    _uiState.update { it.copy(rpp = settings.toUiState()) }
                }
        }
        viewModelScope.launch {
            appState.settingsWriteResponses.collect { response ->
                if (response.isSuccess) {
                    val suffix = if (response.geometryRequiresRestart) "，部分几何参数将在板端重载后生效" else ""
                    ToastUtils.showReplacingSuccess("机器人设置成功$suffix")
                    val current = _uiState.value
                    appState.updateRobotSettings(
                        width = current.robotWidth,
                        length = current.robotLength
                    )
                } else {
                    ToastUtils.showReplacingError(response.message.ifBlank { "机器人设置失败" })
                }
            }
        }
        viewModelScope.launch {
            settingWriteRequests.collectLatest { settings ->
                delay(SETTING_WRITE_DEBOUNCE_MS)
                sendSettingWrite(settings.robotWidth.toFloat(), settings.robotLength.toFloat())
            }
        }
        viewModelScope.launch {
            SystemCacheClearStream.responses.collect(::applySystemCacheClearResponse)
        }
    }

    fun decreaseWidth() {
        _uiState.update { current ->
            current.copy(robotWidth = (current.robotWidth - STEP).coerceAtLeast(MIN_VALUE))
        }
        val current = _uiState.value
        scheduleSettingWrite(current)
    }

    fun increaseWidth() {
        _uiState.update { current ->
            current.copy(robotWidth = (current.robotWidth + STEP).coerceAtMost(MAX_VALUE))
        }
        val current = _uiState.value
        scheduleSettingWrite(current)
    }

    fun decreaseLength() {
        _uiState.update { current ->
            current.copy(robotLength = (current.robotLength - STEP).coerceAtLeast(MIN_VALUE))
        }
        val current = _uiState.value
        scheduleSettingWrite(current)
    }

    fun increaseLength() {
        _uiState.update { current ->
            current.copy(robotLength = (current.robotLength + STEP).coerceAtMost(MAX_VALUE))
        }
        val current = _uiState.value
        scheduleSettingWrite(current)
    }

    fun requestSettingRead() {
        tcpManager.requestSettingRead(readChassis = null, readMap = true, readRpp = true)
    }

    fun updateFootprintPoint(index: Int, x: String? = null, y: String? = null) {
        _uiState.update { state ->
            state.copy(footprint = state.footprint.mapIndexed { pointIndex, point ->
                if (pointIndex != index) point else point.copy(x = x ?: point.x, y = y ?: point.y)
            })
        }
    }

    fun updateBaseLaser(field: BaseLaserField, value: String) {
        _uiState.update {
            when (field) {
                BaseLaserField.X -> it.copy(baseLaserX = value)
                BaseLaserField.Y -> it.copy(baseLaserY = value)
                BaseLaserField.Z -> it.copy(baseLaserZ = value)
                BaseLaserField.ROLL -> it.copy(baseLaserRoll = value)
                BaseLaserField.PITCH -> it.copy(baseLaserPitch = value)
                BaseLaserField.YAW -> it.copy(baseLaserYaw = value)
            }
        }
    }

    fun updateRppText(field: RppTextField, value: String) {
        _uiState.update { state -> state.copy(rpp = state.rpp.updateText(field, value)) }
    }

    fun updateRppToggle(field: RppToggleField, value: Boolean) {
        _uiState.update { state -> state.copy(rpp = state.rpp.updateToggle(field, value)) }
    }

    fun updatePathPlanningText(field: PathPlanningTextField, value: String) {
        _uiState.update { state ->
            state.copy(pathPlanning = state.pathPlanning.updateText(field, value))
        }
    }

    fun applyGeometrySettings(saveDefault: Boolean = false) {
        val map = buildGeometrySettings() ?: return
        if (!tcpManager.requestSettingWrite(mapSettings = map)) {
            ToastUtils.showReplacingError("机器人外形设置发送失败")
        } else if (saveDefault) {
            ToastUtils.showReplacingSuccess("机器人外形已保存为默认")
        }
    }

    fun applyRppSettings(saveDefault: Boolean = false) {
        val rpp = buildRppSettings() ?: return
        if (!tcpManager.requestSettingWrite(
                rppSettings = rpp,
                applyRppTemporarily = true,
                saveRppDefault = saveDefault,
                rppFieldMask = RPP_FIELD_MASK
            )) {
            ToastUtils.showReplacingError("RPP 参数发送失败")
        } else {
            ToastUtils.showReplacingSuccess(if (saveDefault) "RPP 参数已保存为默认" else "RPP 参数已临时应用")
        }
    }

    fun applyPathPlanningSettings(saveDefault: Boolean = false) {
        val map = buildPathPlanningSettings() ?: return
        if (!tcpManager.requestSettingWrite(mapSettings = map)) {
            ToastUtils.showReplacingError("路径规划参数发送失败")
        } else {
            ToastUtils.showReplacingSuccess(
                if (saveDefault) "路径规划参数已保存为默认" else "路径规划参数已临时应用"
            )
        }
    }

    /** 当前协议没有独立的 restore-default 命令，先重新读取板端当前持久化配置。 */
    fun restoreSettingsFromBoard() {
        requestSettingRead()
        ToastUtils.showReplacingSuccess("已重新读取板端配置")
    }

    fun sendSettingWrite(width: Float, length: Float) {
        val settings = SlLink.MapSettings.newBuilder().setVehicleWidth(width).setVehicleLength(length).build()
        val sent = tcpManager.requestSettingWrite(mapSettings = settings)
        if (!sent) {
            ToastUtils.showReplacingError("机器人尺寸设置发送失败")
        }
    }

    private fun applyMapSettings(settings: SlLink.MapSettings) {
        val points = settings.footprintList.map { FootprintPointUi(formatRppFloat(it.x), formatRppFloat(it.y)) }
            .takeIf { it.size >= 3 } ?: defaultFootprint()
        _uiState.update {
            it.copy(
                robotWidth = settings.vehicleWidth.toDouble(),
                robotLength = settings.vehicleLength.toDouble(),
                footprint = points,
                baseLaserX = formatRppFloat(settings.baseLaserX),
                baseLaserY = formatRppFloat(settings.baseLaserY),
                baseLaserZ = formatRppFloat(settings.baseLaserZ),
                baseLaserRoll = formatRppFloat(settings.baseLaserRollDeg),
                baseLaserPitch = formatRppFloat(settings.baseLaserPitchDeg),
                baseLaserYaw = formatRppFloat(settings.baseLaserYawDeg),
                pathPlanning = settings.toPathPlanningUiState()
            )
        }
    }

    private fun buildPathPlanningSettings(): SlLink.MapSettings? {
        val state = _uiState.value.pathPlanning
        fun read(value: String, label: String, min: Float = 0f): Float? {
            val parsed = value.toFloatOrNull()
            if (parsed == null || !parsed.isFinite() || parsed < min) {
                ToastUtils.showReplacingError("$label 参数必须是大于等于 ${formatRppFloat(min)} 的数字")
                return null
            }
            return parsed
        }
        val inflation = read(state.inflationRadius, "障碍物膨胀距离") ?: return null
        val endpoint = read(state.endpointMargin, "起止点留边") ?: return null
        val spacing = read(state.outputPointSpacing, "路径补点间距") ?: return null
        val alignedInflation = read(state.alignedObstacleInflation, "柱体外接矩形膨胀") ?: return null
        val maxExtent = read(state.alignedObstacleMaxExtent, "柱体最大边长", min = 0.01f) ?: return null
        val cornerAngle = read(state.obstacleCornerAngleDeg, "绕柱角度", min = 0.1f) ?: return null
        if (cornerAngle >= 90f) {
            ToastUtils.showReplacingError("绕柱角度应小于 90 度")
            return null
        }
        val avoidance = read(state.obstacleAvoidanceDistance, "额外避让距离") ?: return null
        return SlLink.MapSettings.newBuilder()
            .setInflationRadius(inflation)
            .setEndpointMargin(endpoint)
            .setOutputPointSpacing(spacing)
            .setAlignedObstacleInflation(alignedInflation)
            .setAlignedObstacleMaxExtent(maxExtent)
            .setObstacleCornerAngleDeg(cornerAngle)
            .setObstacleAvoidanceDistance(avoidance)
            .build()
    }

    private fun buildGeometrySettings(): SlLink.MapSettings? {
        val state = _uiState.value
        val builder = SlLink.MapSettings.newBuilder()
            .setVehicleWidth(state.robotWidth.toFloat())
            .setVehicleLength(state.robotLength.toFloat())
        state.footprint.forEachIndexed { index, point ->
            val x = point.x.toFloatOrNull()
            val y = point.y.toFloatOrNull()
            if (x == null || y == null) {
                ToastUtils.showReplacingError("顶点${index + 1}坐标格式不正确")
                return null
            }
            builder.addFootprint(
                SlLink.PolygonPoint.newBuilder().setX(x).setY(y).build()
            )
        }
        val values = listOf(
            state.baseLaserX, state.baseLaserY, state.baseLaserZ,
            state.baseLaserRoll, state.baseLaserPitch, state.baseLaserYaw
        ).map { it.toFloatOrNull() }
        if (values.any { it == null }) {
            ToastUtils.showReplacingError("base_link 到 base_laser_link 参数格式不正确")
            return null
        }
        builder
            .setBaseLaserX(values[0]!!)
            .setBaseLaserY(values[1]!!)
            .setBaseLaserZ(values[2]!!)
            .setBaseLaserRollDeg(values[3]!!)
            .setBaseLaserPitchDeg(values[4]!!)
            .setBaseLaserYawDeg(values[5]!!)
        return builder.build()
    }

    private fun buildRppSettings(): SlLink.RppSettings? {
        val state = _uiState.value.rpp
        fun f(value: String, label: String): Float? = value.toFloatOrNull() ?: run {
            ToastUtils.showReplacingError("$label 参数格式不正确")
            null
        }
        fun i(value: String, label: String): Int? = value.toIntOrNull() ?: run {
            ToastUtils.showReplacingError("$label 参数格式不正确")
            null
        }
        val b = SlLink.RppSettings.newBuilder()
        fun setFloat(value: String, label: String, setter: (Float) -> Unit): Boolean {
            val parsed = f(value, label) ?: return false
            setter(parsed)
            return true
        }
        fun setInt(value: String, label: String, setter: (Int) -> Unit): Boolean {
            val parsed = i(value, label) ?: return false
            setter(parsed)
            return true
        }
        val ok = listOf(
            setFloat(state.desiredLinearVel, "目标跟踪速度", b::setDesiredLinearVel),
            setFloat(state.maxLinearVel, "最大线速度", b::setMaxLinearVel),
            setFloat(state.maxAngularVel, "最大角速度", b::setMaxAngularVel),
            setFloat(state.maxLinearAccel, "最大线加速度", b::setMaxLinearAccel),
            setFloat(state.maxAngularAccel, "最大角加速度", b::setMaxAngularAccel),
            setFloat(state.lookaheadDist, "前视距离", b::setLookaheadDist),
            setFloat(state.minLookaheadDist, "最小前视", b::setMinLookaheadDist),
            setFloat(state.maxLookaheadDist, "最大前视", b::setMaxLookaheadDist),
            setFloat(state.curvatureLookaheadDist, "曲率前视距离", b::setCurvatureLookaheadDist),
            setFloat(state.regulatedLinearScalingMinRadius, "曲率降速最小半径", b::setRegulatedLinearScalingMinRadius),
            setFloat(state.regulatedLinearScalingMinSpeed, "曲率最低速度", b::setRegulatedLinearScalingMinSpeed),
            setFloat(state.collisionFrontClearanceM, "前方避障距离", b::setCollisionFrontClearanceM),
            setFloat(state.collisionSideClearanceM, "侧向避障距离", b::setCollisionSideClearanceM),
            setFloat(state.maxAllowedTimeToCollision, "预测碰撞时间", b::setMaxAllowedTimeToCollisionUpToCarrot),
            setInt(state.collisionConfirmScans, "碰撞确认扫描数", b::setCollisionConfirmScans),
            setInt(state.collisionClearConfirmScans, "清除确认扫描数", b::setCollisionClearConfirmScans),
            setFloat(state.rotateToHeadingAngularVel, "对正角速度", b::setRotateToHeadingAngularVel),
            setFloat(state.rotateToHeadingMinAngle, "进入对正角度", b::setRotateToHeadingMinAngle),
            setFloat(state.goalDistTol, "目标距离容差", b::setGoalDistTol),
            setFloat(state.angleTol, "目标角度容差", b::setAngleTol),
            setFloat(state.lookaheadTime, "速度前视时间", b::setLookaheadTime),
            setFloat(state.approachVelocityScalingDist, "接近目标减速距离", b::setApproachVelocityScalingDist),
            setFloat(state.minApproachLinearVelocity, "接近目标最低速度", b::setMinApproachLinearVelocity),
            setFloat(state.collisionStopDistanceM, "碰撞停止距离", b::setCollisionStopDistanceM),
            setFloat(state.collisionScanHalfAngleRad, "碰撞扫描半角", b::setCollisionScanHalfAngleRad),
            setFloat(state.collisionScanTimeoutS, "激光数据超时", b::setCollisionScanTimeoutS),
            setInt(state.collisionMinValidPoints, "有效激光点数", b::setCollisionMinValidPoints),
            setFloat(state.maxRobotPoseSearchDist, "最大位姿搜索距离", b::setMaxRobotPoseSearchDist),
            setFloat(state.transStoppedVel, "停止线速度阈值", b::setTransStoppedVel),
            setFloat(state.thetaStoppedVel, "停止角速度阈值", b::setThetaStoppedVel),
            setFloat(state.collisionSideTurningMinAngularVel, "侧向避障转弯阈值", b::setCollisionSideTurningMinAngularVel),
            setFloat(state.rotateToHeadingExitAngle, "对正退出角度", b::setRotateToHeadingExitAngle),
            setInt(state.rotateToHeadingStableCycles, "对正稳定周期", b::setRotateToHeadingStableCycles),
            setFloat(state.rotateToHeadingCornerDistance, "转角对正距离", b::setRotateToHeadingCornerDistance)
        ).all { it }
        if (!ok) return null
        return b
            .setUseCollisionDetection(state.useCollisionDetection)
            .setUseFootprintExpansionCollisionDetection(state.useFootprintExpansionCollisionDetection)
            .setUseFixedDistanceCollisionDetection(state.useFixedDistanceCollisionDetection)
            .setUseRotateToHeading(state.useRotateToHeading)
            .setAllowReversing(state.allowReversing)
            .setUseFixedCurvatureLookahead(state.useFixedCurvatureLookahead)
            .setUseRegulatedLinearVelocityScaling(state.useRegulatedLinearVelocityScaling)
            .setUseCostRegulatedLinearVelocityScaling(state.useCostRegulatedLinearVelocityScaling)
            .setUseVelocityScaledLookaheadDist(state.useVelocityScaledLookaheadDist)
            .setUseApproachVelocityScaling(state.useApproachVelocityScaling)
            .setUseCommandVelocityForAccelLimit(state.useCommandVelocityForAccelLimit)
            .setUseCommandAngularVelocityForAccelLimit(state.useCommandAngularVelocityForAccelLimit)
            .setCollisionSideOnlyWhenTurning(state.collisionSideOnlyWhenTurning)
            .setUseCornerAwareRotateToHeading(state.useCornerAwareRotateToHeading)
            .build()
    }

    private fun scheduleSettingWrite(settings: RobotSettingsUiState) {
        settingWriteRequests.tryEmit(settings)
    }

    /**
     * 用户手动检查 OTA。防止检查和下载并发；有版本才打开详情弹窗，
     * 无更新或请求失败使用一次性 Toast，不在页面中保存过期提示。
     */
    fun checkUpdate() {
        if (_uiState.value.isCheckingUpdate || _uiState.value.isDownloading) return
        if (_uiState.value.isAwaitingInstall || apkOtaManager.hasPendingInstall()) {
            ToastUtils.showReplacingSuccess("安装正在进行，请等待系统处理")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, availableRelease = null, showUpdateDialog = false) }
            try {
                val result = apkOtaManager.check()
                val release = result.release?.takeIf { result.hasUpdate }
                _uiState.update {
                    it.copy(
                        availableRelease = release,
                        updateRequired = result.updateRequired,
                        showUpdateDialog = release != null
                    )
                }
                if (release == null) ToastUtils.showReplacingSuccess("当前已是最新版本")
            } catch (e: CancellationException) {
                // ViewModel 生命周期取消不能当作普通网络错误提示。
                throw e
            } catch (e: Exception) {
                ToastUtils.showReplacingError("检查更新失败，请检查网络后重试")
            } finally {
                _uiState.update { it.copy(isCheckingUpdate = false) }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    /**
     * 仅对当前检查返回的 release 发起下载。管理器负责续传、完整性校验及系统安装会话；
     * 此处负责进度展示与用户可重试的错误状态。
     */
    fun startDownload() {
        val release = _uiState.value.availableRelease ?: return
        if (_uiState.value.isDownloading || _uiState.value.isCheckingUpdate ||
            _uiState.value.isAwaitingInstall || apkOtaManager.hasPendingInstall()) return
        // 点击后立即占用下载状态，避免协程开始执行前的连续点击创建多个会话。
        _uiState.update {
            it.copy(isDownloading = true, showUpdateFailedDialog = false,
                showUpdateDialog = false, updateError = null)
        }
        viewModelScope.launch {
            try {
                apkOtaManager.downloadAndInstall(release)
                // 会话提交后继续等待系统确认及终态，不提前展示安装成功提示。
                _uiState.update { it.copy(isAwaitingInstall = apkOtaManager.hasPendingInstall()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isAwaitingInstall = false, showUpdateFailedDialog = true,
                    updateError = e.message ?: "更新失败，请重试") }
            } finally {
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
    }

    fun dismissUpdateFailedDialog() {
        _uiState.update { it.copy(showUpdateFailedDialog = false) }
    }

    fun showClearCacheDialog() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(showClearCacheConfirmDialog = true) }
    }

    fun dismissClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheConfirmDialog = false) }
    }

    internal fun confirmClearCache(categories: Set<ClearCacheCategory>) {
        if (categories.isEmpty() || _uiState.value.isClearingCache) return
        _uiState.update {
            it.copy(
                showClearCacheConfirmDialog = false,
                isClearingCache = true
            )
        }

        val sent = tcpManager.requestSystemCacheClear(
            clearMemoryCache = ClearCacheCategory.MemoryCache in categories,
            clearTemporaryFiles = ClearCacheCategory.TemporaryFiles in categories,
            clearLogs = ClearCacheCategory.Logs in categories
        )
        if (!sent) {
            finishCacheClearWithError("缓存清理请求发送失败")
            return
        }

        clearCacheTimeoutJob?.cancel()
        clearCacheTimeoutJob = viewModelScope.launch {
            delay(CACHE_CLEAR_TIMEOUT_MS)
            if (_uiState.value.isClearingCache) {
                finishCacheClearWithError("缓存清理请求超时")
            }
        }
    }

    private fun applySystemCacheClearResponse(payload: SystemCacheClearPayload) {
        if (!_uiState.value.isClearingCache) return
        clearCacheTimeoutJob?.cancel()
        clearCacheTimeoutJob = null
        _uiState.update { it.copy(isClearingCache = false) }

        if (!payload.isSuccess) {
            ToastUtils.showReplacingError(payload.message.ifBlank { "缓存清理失败" })
            return
        }

        val releasedBytes = payload.temporaryBytesReleased + payload.logBytesReleased
        val clearedFiles = payload.temporaryFilesCleared + payload.logFilesCleared
        val detail = buildList {
            if (clearedFiles > 0) add("清理 $clearedFiles 个文件")
            if (releasedBytes > 0) add("释放 ${formatBytes(releasedBytes)}")
        }.joinToString("，")
        val suffix = detail.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()
        if (payload.failedItems > 0) {
            ToastUtils.showReplacingError("缓存清理完成$suffix，${payload.failedItems} 项未清理")
        } else {
            val message = payload.message.ifBlank { "缓存清理成功" }
            ToastUtils.showReplacingSuccess("$message$suffix")
        }
    }

    private fun finishCacheClearWithError(message: String) {
        clearCacheTimeoutJob?.cancel()
        clearCacheTimeoutJob = null
        _uiState.update { it.copy(isClearingCache = false) }
        ToastUtils.showReplacingError(message)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= BYTES_PER_GIB -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / BYTES_PER_GIB)
        bytes >= BYTES_PER_MIB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / BYTES_PER_MIB)
        bytes >= BYTES_PER_KIB -> String.format(Locale.ROOT, "%.1f KB", bytes.toDouble() / BYTES_PER_KIB)
        else -> "$bytes B"
    }

    companion object {
        // 仅提交页面有控件的字段；31、33、34、43 是板端高级参数，暂不随页面默认值覆盖。
        private val RPP_FIELD_MASK: Long = (1..30)
            .plus(32)
            .plus(35..42)
            .plus(44..52)
            .fold(0L) { mask, field -> mask or (1L shl (field - 1)) }
        private const val STEP = 0.1
        private const val MIN_VALUE = 0.1
        private const val MAX_VALUE = 9.9
        private const val SETTING_WRITE_DEBOUNCE_MS = 800L
        private const val CACHE_CLEAR_TIMEOUT_MS = 10_000L
        private const val BYTES_PER_KIB = 1024L
        private const val BYTES_PER_MIB = BYTES_PER_KIB * 1024L
        private const val BYTES_PER_GIB = BYTES_PER_MIB * 1024L
    }
}

