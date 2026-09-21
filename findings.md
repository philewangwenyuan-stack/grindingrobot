# Findings

- 地图列表按钮位于 `feature/map/.../MapHomeScreen.kt:314`，通过 `onAddMap` 回调触发。
- 当前 `feature/main/.../MainHomeNavigation.kt:27` 直接导航到 `MapRoutes.Step1()`，没有发送建图模式请求。
- 当前 `feature/map/.../MapScreenStep1.kt:55-57` 在进入 Step1 的 `DisposableEffect` 中先清理 LIVE_MAP 缓存并直接调用 `startMapSnapshotLoop(mapId)`。
- `MapViewModel.startMapSnapshotLoop()` 通过 `TcpManager.requestMapSnapshot(mapId)` 周期发送 `0x0304`。
- `TcpManager` 当前已有地图快照、地图编辑、地图保存、地图目录请求，但没有 MapMode 请求。
- `SlLinkManager` 当前没有 `MapMode` 请求方法、`0x0512/0x0513` 常量或响应处理。
- `core/sllink/src/message_gen/sl_link/SlLink.java` 已包含 `MapModeType.MAP_MODE_MAPPING`、`MapModeRequest` 和 `MapModeResponse`。
- `SlMessageBuilder.kt` 当前没有 `buildMapModeRequestRaw()`。
- 地图协议的目标设备 ID 默认是 `0x10`、组件 ID 是 `0x08`。

## 2026-09-18 重定位页面改版

- 参考图 1 是当前重定位弹窗：左侧机器人示意图，右侧速度/转速/摇杆；入口由 `StartGrindingScreen` 传入 `RelocalizationDialog`。
- 参考图 2 的目标布局是左侧地图、右侧白色工作区卡片；本次将右侧卡片改成“设置初始位置”与微调控制，保留关闭、同步和最终重定位入口。
- `StartGrindingSessionUiState.mapPreview` 已包含地图位图和坐标元数据（分辨率、原点、heading、显示旋转），可直接用于重定位页面；地图支持现有 `MapPreviewUiState` 坐标转换链。
- 当前 APP 的 `SlLink.java` 仍是旧版 `RadarRelocalizationRequest`（无 `initial_pose_available`/`initial_pose` 字段），外部已适配 SDK 的同名生成文件包含这 3 个字段，需要同步到 Android 模块后才能构造 `0x052A` 初始位姿请求。
- 当前 `TcpManager.requestRadarRelocalization()` 发送空 protobuf；需要扩展为接收 `x/y/heading_deg`，通过 `RadarRelocalizationRequest` 设置初始位姿，再发送现有 `0x052A` 帧。

## 2026-09-18 重定位方向排查

- 用户粘贴的 ROS 日志显示 `MapRequest raw map output without rotation`，`frame_id=map`；当前运行期间地图尺寸从 `1470x960`（保存地图）和 `1448x938`（LIVE_MAP）返回，说明图片本身是原始 map 网格，不应把自定义展示角当成世界坐标的 heading。
- 日志中的地图对齐参数为 `alignment_yaw_deg=0.000`、`rotation_alignment_delta_deg=0.180`，初始位姿请求已收到 `frame_id=map` 对应的 `x/y/heading_deg`。
- 重定位页面当前自己复制了世界→Bitmap 和 Bitmap→屏幕的公式；其他页面统一使用 `worldPointToBitmapOffset` + `taskBitmapPointToFittedPoint`。坐标公式本身基本相同，但独立实现容易导致旋转/适配中心漂移，应改为共用链路。
- 当前 `RelocalizationArrow` 以“屏幕向上”为箭头基准，却使用 `-(headingDeg - mapRotationDeg)`；按工程约定 `heading=0` 是 map +X、`heading=90` 是 map +Y，正确屏幕旋转应为 `90 - headingDeg + mapRotationDeg`。
- 当前箭头绘制有白色圆底和蓝色圆环，需改为绿色、较小、较细的单体箭头。

## 2026-09-19 机器人设置与 RPP 参数评估

- Android 已有 `feature/main/.../RobotSettingsScreen.kt` 和 `RobotSettingsViewModel.kt`，现状只有机器人宽度/长度两个数值，并通过 `SettingsReadRequest/SettingsWriteRequest` 的 `MapSettings.vehicle_width/vehicle_length` 读写；截图中的四点 footprint 和 `base_link -> base_laser_link` 尚未建模。
- 当前 Android/板端 SL-Link `SettingsRead/Write*` 仅包含 `ChassisSettings` 与 `MapSettings`，其中 `MapSettings` 只有宽度、长度、路径间距、转弯半径、重叠率、膨胀半径和区域多边形；没有 base-to-laser TF 或 RPP 字段。
- 板端 RPP 控制器已经注册 `dynamic_reconfigure::Server<RegulatedPurePursuitConfig>`，配置文件覆盖了目标速度、前视距离、曲率前视、加速度、碰撞检测、footprint 碰撞、到达容差、允许倒车等大量参数；因此 RPP 的“临时应用”具备可实现的运行时基础。
- 当前 `grinder_scheduler` 只通过 `navigation_speed_reconfigure_namespace=/move_base/RegulatedPurePursuitController` 动态修改 `desired_linear_vel`，没有通用参数读取/写入接口，也没有把参数持久化到默认 YAML 的 SL-Link 命令。
- `base_link -> base_laser_link` 当前由启动文件中的 `static_transform_publisher` 参数生成，footprint 当前写在 costmap YAML；修改它们通常需要 scheduler/导航节点提供运行时服务，或保存配置后重启相关节点，不能直接套用已有 MapSettings 写入。
- 结论：两个功能都能加，但必须按“协议/板端接口 + Android 页面”一起做。仅改 Android UI 会出现输入框能改、机器人运行参数不变的问题；RPP 可优先做动态临时应用，footprint/TF 需要先确定运行时更新和持久化策略。

## 2026-09-19 板端协议已更新后的 Android 接入核对

- 板端 canonical proto 已新增：`MapSettings.footprint`（重复 `PolygonPoint`）、`base_laser_x/y/z/roll_deg/pitch_deg/yaw_deg`；`RppSettings` 字段 1-52；`SettingsReadRequest.read_rpp`；`SettingsWriteRequest.rpp/apply_rpp_temporarily/save_rpp_default/rpp_field_mask`；写响应的 `rpp_applied/rpp_saved/geometry_requires_restart`。
- 板端删除响应 `MapDeleteResponse` 已新增 `local_deleted`、`remote_deleted`、`remote_delete_pending`。Android 当前 `MapDeletePayload` 没有这些字段，`MapHomeViewModel` 仍要求 `isSuccess` 才从列表删除，需要改为 `localDeleted` 即视为本地完成。
- 外部 Android 生成文件为 `E:/CODE/C++/Grinder/third_party/sl_linka/sdk/android/src/message_gen/sl_link/SlLink.java`，包含新字段；工程内 `core/sllink/.../SlLink.java` 仍是旧版本，但包含工程专用的 `TaskConfig/TaskConfigResponse.task_name` 兼容字段。同步时需要保留任务名称兼容，避免现有任务配置测试/功能回退。
- 工程已有 `RobotSettingsScreen`/`RobotSettingsViewModel`，适合扩展为左侧“机器设置/导航参数”两个页面；设置读写结果已经通过 `AppState.settingsReadResponses/settingsWriteResponses` 流转，但目前只保留底盘结果和宽高状态。

## 2026-09-19 Android 请求洪峰排查

- `MapHomeViewModel.startMappingMode()` 原先在协程内部才设置 `isStartingMapping`，快速双击可能启动多个 `0x0512`；已改为调用入口同步置位并保留单一活动任务。
- 重定位状态轮询原先每秒无条件发送 `0x052C`，响应慢时会在板端有序队列中积压；已改为单飞请求，响应返回或 3 秒超时后才允许下一条。
- `0x052A` 重定位和雷达地图同步增加在途响应保护；同一张地图的 `0x0522` 导入增加单飞/成功去重。
- 定向协议与重定位测试通过；全量地图测试仍有 4 个 `TaskPathGeometryTest` 失败，失败点不触及本次改动路径，应单独处理。

## 2026-09-19 设置页视觉还原测量

- 参考图整体为浅蓝灰渐变背景，内容区为左侧约 290px 宽的白色半透明圆角侧栏，右侧约 1360px 宽的白色大圆角面板；两者之间有约 16px 间距。
- 侧栏分为“软件设置/硬件设置”两组，分隔线为浅蓝虚线；选中项使用亮蓝填充、白色粗体文字和约 12px 圆角，未选中项为深蓝文字。
- 机器设置页是“标题 + 左侧机器人/坐标示意 + 右侧参数卡”的结构，右侧分为 footprint 多边形与 base_link→base_laser_link 两个区块，字段是浅灰边框的紧凑白色输入框，按钮是亮蓝实心按钮。
- RPP 页顶部有标题、蓝色提示标签、控制器运行状态和右上角操作按钮；主体是两列四张圆角卡片。卡片标题带蓝色/橙色强调色，参数行紧凑，英文协议字段以小号灰蓝文字显示，开关为蓝色胶囊样式。
- 当前实现的问题是把整个右侧做成 `F6F8FB` 普通表单，并大量使用默认 `OutlinedTextField`/Material `Switch`，缺少参考图的卡片层次、紧凑行高、顶部状态条及两栏密度；本轮需要保留回调但重写 composable 结构与控件样式。

## 2026-09-20 重定位与路径规划页面需求

- 重定位页面当前已经有地图与绿色无外圈箭头，但箭头位置会随状态轮询/板端返回的位姿刷新；需要把“编辑中的初始位姿”与“板端实时位姿”分离，用户再次重定位时以本地编辑点为准，直到提交或取消。
- 当前重定位地图采用 `RelocalizationMapPanel` + `RelocalizationArrow`，需要将可视化主体收敛为一个中心点和一个小绿色方向箭头，去除额外圆圈/机器人外形装饰。
- `RobotSettingsScreen.kt` 已有机器人外形页与 RPP 页，但当前 RPP 页面展示了较多控制器参数；本次导航参数应仅保留核心影响参数，并保持与机器人外形页相同的浅蓝背景、左侧菜单、白色圆角主卡片和蓝色操作按钮风格。
- 路径规划的七个参数（inflation_radius、endpoint_margin、output_point_spacing、aligned_obstacle_inflation、aligned_obstacle_max_extent、obstacle_corner_angle_deg、obstacle_avoidance_distance）目前尚未在 Android UI 的独立页面中出现，需要确认板端协议是否已有对应字段；若没有，必须先扩展协议/读写链路，不能只做输入框。
- 工程当前没有 `sl_link.proto` 源文件，Android 使用 `core/sllink/src/message_gen/sl_link/SlLink.java` 生成物；生成模型中可见 `MapSettings.inflation_radius`，未发现其余六个路径规划字段，不能直接假设已有板端可写接口。
- `RelocalizationDialog` 通过 `StartGrindingScreen` 传入 `MapHomeViewModel` 的实时 `robotPose`；页面内 `pose` 的 remember key 目前包含实时位姿字段，是位置被回调覆盖的根因。
