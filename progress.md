# Progress

## 2026-09-18

- 已读取规划技能并完成历史上下文检查。
- 已核对地图列表按钮、Step1 生命周期、TcpManager、SlLinkManager 和生成协议模型。
- 已增加 `MapModeStream`、`0x0512` 帧构建、`0x0513` 响应解析、TcpManager 请求封装。
- 已将“+”按钮改为等待模式启动成功后，导航到带 `LIVE_MAP` 的 Step1；Step1 再启动 `0x0304` 轮询。
- 已增加 `MapModeProtocolTest`，验证 `0x0512`、`dstId=0x10`、`compId=0x08` 和 protobuf payload。
- 已完成静态引用、括号平衡和入口链路检查。
- 已配置 Temurin JDK 17 到 `C:\Users\phil\AppData\Local\Programs\EclipseAdoptium\extract\jdk-17.0.20.1+1`，并写入用户级 `JAVA_HOME` 与 PATH。
- APK 打包命令：`.\gradlew.bat :app:assembleDevDebug`、`.\gradlew.bat :app:assembleProdDebug`、`.\gradlew.bat :app:assembleProdRelease`；输出在 `app/build/outputs/apk/`。
- 已安装 Android command-line tools、platform-tools、Android API 36 和 Build Tools 36.0.0 到 `C:\Users\phil\AppData\Local\Android\Sdk`，并写入用户级 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 与 PATH。
- 已修正 `local.properties` 的 `sdk.dir`。
- `:app:assembleDevDebug` 构建成功，生成 arm64-v8a 和 armeabi-v7a 两个调试 APK；两个 APK 均通过 APK v2 签名校验。
- `:core:tcp:testDevDebugUnitTest --tests com.sinelynx.grindingrobot.core.tcp.MapModeProtocolTest` 通过。

## 2026-09-18 重定位页面改版

- 已查看两张参考图：目标页面是地图左、设置卡片右，需将原速度/摇杆重定位弹窗替换为初始位姿设置流程。
- 已确认当前 `StartGrindingSessionUiState.mapPreview` 可以提供重定位页面使用的地图数据；当前 Android 生成协议模型还未包含初始位姿字段，外部 SDK 版本已包含。
- 已将重定位弹窗改为地图 + 设置初始位置工作区：支持地图点选、拖动/双指缩放、加减缩放、上下左右微调和方向旋转微调。
- 已将重定位最终按钮改为携带 x/y/heading_deg 调用 TcpManager.requestRadarRelocalization，并发送 0x052A 及可选协方差。
- 已同步 SlLink.java 初始位姿模型，并保留工程原有 TaskConfig/TaskConfigResponse.task_name 兼容字段。
- 新增 RadarRelocalizationProtocolTest，与 MapModeProtocolTest 一起通过。
- :feature:map:compileDevDebugKotlin 和 :app:assembleDevDebug 通过。
- 最终 APK 已通过 APK Signature Scheme v2 校验：
  - app-dev-arm64-v8a-debug.apk
  - app-dev-armeabi-v7a-debug.apk

## 2026-09-18 重定位方向修正

- 已读取用户提供的 ROS 启动/运行日志；确认地图返回 `frame_id=map`，板端日志写明 raw map output without rotation。
- 已将重定位地图世界坐标到 Bitmap/屏幕的正向投影统一到其他页面共用的 `worldPointToBitmapOffset` 与 `taskBitmapPointToFittedPoint`。
- 已修正箭头角度为 `90 - headingDeg + displayRotation`，并改为小尺寸、细线宽、绿色且无外圈的箭头。
- 新增 `RelocalizationCoordinateTransformTest`，重定位方向单测通过。
- `:feature:map:testDevDebugUnitTest --tests com.sinelynx.grindingrobot.feature.map.ui.RelocalizationCoordinateTransformTest :feature:map:compileDevDebugKotlin` 通过。
- `:app:assembleDevDebug` 通过；arm64-v8a 与 armeabi-v7a APK 均为 `versionName=0.1.1`，APK v2 签名校验通过。

## 2026-09-19 机器人设置与 RPP 参数评估

- 已确认 Android 机器人设置页面目前只读写 `MapSettings.vehicle_width/vehicle_length`。
- 已确认 SL-Link 当前设置协议没有 footprint 顶点、base-to-laser TF 或 RPP 参数消息。
- 已确认板端 RPP 支持 dynamic_reconfigure，scheduler 目前只封装了目标速度的动态修改。
- 已确认 footprint 来自 costmap YAML，base-to-laser TF 来自启动文件参数；两者需要板端新增运行时配置服务/协议才能由 APP 可靠修改。
- 当前未修改业务代码，待用户确认后按端到端方案实施；本轮结论为“可加，但不是单独增加两个 Compose 页面即可完成”。

## 2026-09-19 Android 接入开始

- 已确认板端新 proto 和外部 Android 生成文件位置。
- 已确认当前 Android 删除逻辑需使用 `local_deleted` 作为成功条件，并显示远端后台重试状态。
- 已发现外部生成 `SlLink.java` 不含工程现有 `task_name` 兼容字段；同步协议时需保留该兼容能力。
- 2026-09-19：继续修复 Android 端板端队列放大问题：建图 `0x0512` 改为同步置位单飞；重定位状态 `0x052C` 等待响应/超时后再发；`0x052A` 与雷达地图同步禁止重复在途请求；`0x0522` 同地图导入增加单飞和成功去重。
- 2026-09-19：` :feature:map:compileDevDebugKotlin` 与 `:feature:main:compileDevDebugKotlin` 通过，仅保留既有弃用警告。
- 2026-09-19：` :core:tcp:testDevDebugUnitTest` 通过；MapMode、RadarRelocalization、TaskConfig，以及重定位状态/坐标相关测试通过。
- 2026-09-19：全量 `:feature:map:testDevDebugUnitTest` 为 118 项中 4 项既有 `TaskPathGeometryTest` 失败，与本次请求去重和设置页改动无关；本次相关定向测试通过。
- 2026-09-19：` :app:assembleDevDebug` 构建成功，产出 arm64-v8a 与 armeabi-v7a APK，版本名 `0.1.1`、versionCode `1`。

## 2026-09-19 设置页面视觉还原

- 用户反馈上一版 APP 只是功能可用，和参考图的布局、卡片层级、留白、颜色及信息密度不一致。
- 本轮目标：保留现有设置协议/读写交互，重做机器设置与 RPP 导航参数两页，使其匹配参考图的浅蓝背景、左侧导航、白色圆角主卡片、双栏参数卡、蓝色操作按钮和紧凑字段布局。
- 已重写 `RobotSettingsScreen.kt` 的机器设置页：增加参考图风格的机器人外形/坐标示意、Footprint 顶点卡、尺寸摘要、base_link→base_laser_link 字段和蓝色操作栏。
- 已重写 RPP 页：增加顶部提示标签、控制器状态条、临时调参/未保存状态、速度/跟踪/避障/对正四张参数卡，以及协议字段小字和自定义胶囊开关。
- 已保留原有 ViewModel 回调与协议字段，输入、临时应用、保存默认、恢复默认交互仍走原实现。
- `:feature:main:compileDevDebugKotlin` 和 `:app:assembleDevDebug` 均通过；APK 版本名保持 `0.1.1`。
- 当前 ADB 仅发现 `emulator-5554 offline`，没有可用的在线平板/模拟器，因此本轮无法抓取真机截图；已完成源码编译和 APK 元数据校验。
- 最终 APK SHA-256：arm64 `71E2465DFA402EB101DA06D48678D7333D839C1E723A6A376371AD94FE830AAA`；armeabi-v7a `856930B945E0D6CA6C0AEC20F9BC532C5FD836B93DB7111345552BB3D66E75F8`。

## 2026-09-20 取消建图停止 Super-LIO

- 未编译，按用户要求只修改代码。
- `TcpManager` 新增 `stopMappingMode()`，发送 `0x0512 MAP_MODE_MAPPING` 且 `enabled=false`；板端收到后负责停止/kill Super-LIO 建图节点。
- Step1 的取消操作停止地图快照轮询并发送停止模式请求；Step2/Step3/Step4 的取消入口也统一发送停止模式请求，避免只退出页面而留下板端建图进程。

## 2026-09-20 重定位与路径规划页面需求

- 已确认本轮目标：重定位编辑点不能被板端状态轮询覆盖；重定位地图视觉改为中心点+方向箭头；导航参数收敛为核心参数并统一视觉；新增路径规划参数页面。
- 当前处于代码核对阶段，尚未编译。

## 2026-09-24 外形坐标示意图

- 已定位机器人设置页 `RobotFootprintDiagram`；准备移除车身装饰并按配置坐标绘制 Footprint、base_link 和 base_laser_link。
- 已移除车身装饰；示意图按当前 Footprint 顶点绘制轮廓及编号，并实时按 baseLaserX/Y 绘制激光坐标。X 向上、Y 向左；两原点重合时使用同心标记和错行标签。
- `:feature:main:compileDevDebugKotlin` 通过。当前只完成源码改动与编译，未重新打包 APK。

## 2026-09-24 三页视觉统一落地

- 已确认三张视觉稿与后续反馈：机器设置只显示轴向前后左右距离，移除三维“空间距离”；导航参数移除“控制器”文字行；三页字体完整显示并保留现有读写回调。
## 2026-09-24 三页设置实现结果

- 机器设置、导航参数、路径规划共用固定底部操作栏（临时应用 / 保存为默认 / 恢复默认），参数区滚动，窄宽度下双栏堆叠。
- 导航参数移除硬编码“控制器：Regulated Pure Pursuit”及未经实时状态驱动的标签；实际 RPP 参数保留。
- 机器示意图按输入顶点实时重绘，并沿 X/Y 正交方向标注前后左右边界距离；激光显示 X/Y 平面偏移，不再出现空间斜距。
- `:feature:main:compileDevDebugKotlin` 与 `:app:assembleDevDebug` 通过。新 APK：`app-dev-arm64-v8a-debug.apk`、`app-dev-armeabi-v7a-debug.apk`。`adb devices` 无连接设备，未做真机视觉验收。
## 2026-09-24 导航与建图机器人图标同步 Footprint

- `AppState.RobotSettingsState` 增加米制 Footprint 点；设置读响应、写成功路径都同步到全局状态。
- 地图标记改用 Footprint 多边形按地图分辨率显示实际尺寸，以 `base_link`（pose）为平移/旋转中心；扣除地图原点朝向及页面旋转。无有效 Footprint 时保留旧图标回退。
- 已接入建图 Step1～Step4、导航地图预览、远程地图预览及轨迹回放；进入地图页且设置未加载时主动读取一次设置。
- `MapRobotMarkerTest` 定向投影测试通过；`:feature:map:compileDevDebugKotlin`、`:feature:main:compileDevDebugKotlin`、`:app:assembleDevDebug` 通过。新 arm64/v7a APK 均于 18:17:58 生成。
- 无连接 Android 设备，未做真机视觉验收。
