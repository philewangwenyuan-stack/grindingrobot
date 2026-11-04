# GrindingRobot Agent Guide

这是一份给代码 Agent 使用的项目交接与修改指南。修改前先阅读本文，再根据任务范围阅读对应模块和 `README.md`、`docs/` 下的专项文档。

## 项目定位

GrindingRobot 是研磨机器人 Android 上位机。Android 端主要通过 TCP + SLLink 与嵌入式端通信，嵌入式端再驱动实际设备。典型数据链路为：

```text
Compose 页面 / ViewModel
        -> Repository 或 TcpManager
        -> SlLinkManager / SLLink 帧协议
        -> 嵌入式端
        -> 响应解析与 core:model 状态流
        -> ViewModel / Compose UI
```

不要把 Android 页面当作独立业务处理；设备请求、响应、状态流和页面展示通常需要一起检查。

## 技术与构建基线

- Kotlin 2.2.21、AGP 8.13.1、Gradle Kotlin DSL、Version Catalog。
- Compile/Target SDK 36；版本目录中的默认 `minSdk` 为 24，但 `app` 显式提升为 26。
- JDK 17 是项目声明的构建要求；Android/Kotlin 常规编译目标由 `build-logic` 统一配置为 JVM 11。
- Jetpack Compose、Navigation Compose、Coroutines/StateFlow、Hilt/KSP、Room、MMKV、OkHttp/Retrofit。
- 应用有 `dev` 和 `prod` 两个环境 flavor；常用变体为 `DevDebug`、`ProdDebug`、`ProdRelease`。
- 应用 APK 按 `armeabi-v7a` 和 `arm64-v8a` 分 ABI 输出，不生成 universal APK。

常用命令（在项目根目录执行）：

```powershell
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:assembleProdDebug
.\gradlew.bat :app:assembleProdRelease
.\gradlew.bat :feature:map:compileDevDebugKotlin
.\gradlew.bat :feature:main:compileDevDebugKotlin
.\gradlew.bat :core:model:testDevDebugUnitTest
.\gradlew.bat :feature:map:testDevDebugUnitTest
.\gradlew.bat testDevDebugUnitTest
```

构建前在当前终端配置有效的 `JAVA_HOME`；此工作区可用 `D:/jdk-21.0.8`。应至少运行受影响模块的编译和单元测试；涉及入口、依赖、构建逻辑或全局状态时再运行完整构建/测试。

## 模块职责

### 应用与导航

- `app`：`Application`、`MainActivity`、`AppNavHost`、最终 APK 配置。
- `navigation`：路由数据类、导航事件、结果回传和 `AppNavigator`。
- `build-logic`：统一 Android、Compose、Hilt、feature 模块的 convention plugin。修改模块构建行为优先改这里，不要在各模块重复堆配置。

### Feature

- `feature:connect`：设备连接页面和连接流程。
- `feature:main`：主框架、首页、任务记录/回放、统计、OTA。
  遥控目标与松手零速见 `feature:common/control/ManualDriveController.kt`、`viewmodel/RemoteSettingsViewModel.kt`，回归为 `ManualDriveControllerTest.kt`；传输层独立定时发送及停车回归见 `core:tcp` 的 `PairedTcpServiceTest.kt`，松手零速不能等任务STOP回执。
- `feature:map`：地图目录、编辑步骤、区域/禁区、路径规划、开始研磨。
- `feature:device`：设备状态、任务地图及设备侧业务。
  设备累计统计、任务记录和作业统计统一调用 `TcpManager.loadTaskExecutionHistory()`；`TaskHistoryQuery.kt` 串行等待、处理断线和超时，退出页面取消查询以释放共享分片接收权。回归见 `TaskHistoryQueryTest.kt`。
- `feature:common`：跨功能公共 UI、弹窗、状态栏和资源。
- `feature:remote`：相对独立的远程控制预留模块，入口和完整度需要结合设备联调确认。

### Core

- `core:model`：请求/响应模型、业务模型、地图/任务模型和跨页面状态流。
- `core:data`：Repository、全局 `AppState` 和数据层编排。
- `core:tcp`：TCP 连接、请求发送、响应解析、任务数据分片组装。
  双通道入口是 `PairedTcpService.kt`，通过其 `Callback` 向 `TcpManager` 交付完整帧；`ChannelRouting.kt` 分流，`ChannelRuntime.kt` 提供时钟/主线程健康/Wi-Fi事件。数据8002、控制8003，先获取连接组令牌再绑定控制端。
  亮屏恢复、单平板占用、独立100ms遥控发送、ACK/主线程健康超时见 `PairedTcpServiceTest.kt`；改连接逻辑必须验证旧令牌、旧socket收尾和旧速度不能复活，数据重连不得清理控制确认。
  遥控续发与ACK等待分离，保留500ms确认保护，更新成功回执可结束旧速度等待；任务底图请求/失败/取消由 `MapSnapshotLoader.kt` 统一管理，回归 `MapSnapshotLoaderTest.kt`；开始研磨等待旧请求释放后再切图，并按导入响应 `map_id` 关联。
  任务重定位启动等待由 TCP `MapModeAwaiter.kt` 按帧 ACK 关联，接收失败并处理断线；地图共享解码由 map UI `SerializedMapDecoder.kt` 串行执行，取消排队旧帧并复用同一帧，`MapWorkflowBitmap.kt` 在后台准备绘制缓存。回归 `MapModeAwaiterTest.kt`、`SerializedMapDecoderTest.kt`。
- `core:sllink`：SLLink 帧、CRC、消息构建和生成的消息代码。
- `core:network`：HTTP/Retrofit、OTA 服务及网络数据源。
- `core:database`：Room 数据库和本地数据源。
- `core:datastore`：配置、用户信息等持久化。
- `core:designsystem`、`core:ui`：主题、图标和通用 Compose UI。
- `core:common`、`core:util`、`core:result`：基础类、工具、统一结果处理。
- `core:bluetooth`、`core:udp`、`core:websocket`：辅助通信能力。

## UI 风格与字体（用户确认规范）

- 新增或修改确认、警告、结果反馈弹窗时，沿用 `feature/main/src/main/java/com/sinelynx/grindingrobot/feature/main/ui/component/ConfirmOverlayDialog.kt` 的风格。其他模块使用所在模块的同款公共组件；缺少能力时扩展公共组件，避免每个页面另写一套。不得直接使用随系统深色主题变黑的默认 Material3 `AlertDialog`。
- 弹窗以已有“确定执行紧急制动？”弹窗为样式基准：居中白色面板、48.dp 大圆角、暗色半透明遮罩；上方居中放置淡红圆形图标区，下面是居中标题和说明，底部固定胶囊按钮。平板标准面板参考 480×400.dp，图标底圆 80.dp，内容横向内边距 40.dp。小窗口需约束在可见范围内，不能裁切底部操作区。
- 字体统一使用项目既有系统默认中文无衬线字体 `FontFamily.Default`，不自行引入字体或混用衬线/装饰字体。弹窗标题 24.sp、Bold、行高 30.sp、深色 `#202937`；说明文字 20.sp、Normal、行高 28.sp、灰色 `#9DA3AF`；按钮文字 18.sp、Bold、行高 24.sp。字号用 sp，布局尺寸用 dp，禁止照抄截图像素当作 dp/sp。
- 标题、正文和按钮文字均居中。正文允许换行，长说明和板端结果必须在正文区域滚动，不能截断关键原因、挤出按钮或靠缩小字体塞下全部文字。
- 双操作时左侧灰色“取消”、右侧红色主操作，参考按钮 180×64.dp、间隔 24.dp、圆角 1000.dp；单操作结果弹窗只保留居中的主按钮。危险操作红色 `#D82B2A`、白字；取消按钮底色 `#E5E7EB`、灰字。禁用按钮需要视觉区分且不可点击。
- 弹窗背景和文字颜色显式指定，保证系统浅色/深色主题下仍符合上述白底风格。页面其他字体沿用已有相邻组件，不因新增弹窗顺带修改全局主题或字号。
- 地图模块的“重新扫描”、移除图纸、失效图纸确认等弹窗统一复用 `feature/map/src/main/java/com/sinelynx/grindingrobot/feature/map/ui/DrawingConfirmDialog.kt`（外壳为 `feature:common/CommonTipsDialog`）。新增同类弹窗先复用或扩展该组件，禁止页面内另写默认 `AlertDialog`；“重新扫描”也必须白底，不能因系统主题变黑。
- 修改弹窗后检查白底、48.dp 圆角、默认中文字体、标题/正文/按钮字号与字重、居中和长正文滚动。标题 24.sp Bold、正文 20.sp Normal、按钮 18.sp Bold，行高分别 30/28/24.sp；不得只改背景色而沿用默认 Material 字体排版。提交前搜索修改页面的 `AlertDialog` 使用并确认已复用公共样式。

## 重要修改规则

1. 修改设备协议前，先确认 Android 与嵌入式端的消息 ID、组件 ID、字段顺序、字节序、长度和 CRC 约定。
2. `core/sllink/src/message_gen/` 是协议生成代码，不要直接手改。协议源位于 `core/sllink/proto/sl_link.proto`；使用 protoc 25.1 和 `core/sllink/scripts/generate-proto.ps1` 重新生成，并同步验证解析测试。
3. `SlFrameParser` 支持分片和粘包；改帧格式、CRC 或解析状态机时，必须覆盖半帧、多帧、错误 CRC、错误尾字节和恢复解析。
4. `SlMessageBuilder` 使用小端序，序列号为 16 位循环值。新增消息构建函数时保持现有命名和 `Raw` API 风格。
5. 全局设备状态集中在 `core:data`/`AppState` 的 StateFlow/SharedFlow 中。新增状态时同时检查更新、清理、断线重置和页面收集逻辑。
6. 地图、轨迹和机器人图标包含坐标转换、旋转、缩放和手势联动。相关修改前先阅读：
   - `docs/地图旋转需求实现与排障说明.md`
   - `docs/机器人图标等比例缩放与手势缩放联动重构设计文档.md`
   修改后优先运行 `feature:map`、`feature:main` 下的几何/状态测试，并进行真机操作验证。
7. Compose 页面应保持 UI 与业务状态分离；网络、设备协议、数据库操作放在 ViewModel/Repository/数据层，不要在 Composable 中直接管理长生命周期连接。
8. 跨模块引用资源时使用资源所属模块的 `R`，必要时加别名，避免误用当前模块资源。
9. 使用类型安全项目访问器和版本目录；新增依赖优先修改 `gradle/libs.versions.toml`，不要随意在模块脚本中散落版本号。
10. 公共模块、状态模型、协议和导航的改动需要检查所有下游依赖，不要只编译当前页面模块。

## OTA 注意事项

OTA 主要位于 `feature/main/.../ota/ApkOtaManager.kt`。当前代码中 `installId` 仍是固定的联调测试 ID，正式交付前必须改为每台设备独立生成并持久化的标识，并保证检查更新、刷新下载地址和事件上报使用同一值。

OTA 修改后至少检查：

- `dev` 使用 `test` 渠道，非 debug 使用 `production` 渠道。
- 下载文件大小、SHA-256、包名、版本号和签名校验仍然生效。
- 安装事件本地队列在网络失败后可以补报。
- 安装结果在启动和回到前台时都能正确处理。

## 配置与安全

- 默认设备 TCP 地址为 `192.168.1.3`；修改连接配置时同时检查 MMKV 持久化、断线清理和连接页面回显。
- convention plugin 中的 `BASE_URL` 当前是硬编码服务地址；变更环境时同时检查 `dev`/`prod` flavor。
- `app/build.gradle.kts` 保留了带固定密码的签名配置模板。不要提交真实密钥、密码或生产签名；正式构建应迁移到本地未跟踪配置或安全 CI Secret，并保持 release 配置可审计。
- 不要把 `local.properties`、keystore、APK、`build/` 产物或调试日志作为业务代码提交。

## 推荐排查顺序

### 设备通信异常

1. 确认设备 IP/端口和 TCP 连接状态。
2. 确认 ViewModel 是否发出请求、请求参数是否正确。
3. 检查 `core:tcp` 是否完成分片组装并交给 SLLink 解析。
4. 检查消息 ID、组件 ID、序列号、ACK、CRC 和 protobuf 字段。
5. 确认响应是否写入正确的 `core:model`/`AppState` Stream。
6. 确认页面收集的是正确的 StateFlow/SharedFlow，并处理了 loading、失败、断线和空数据。

### 地图/轨迹显示异常

1. 先判断是原始坐标、坐标转换、旋转中心、缩放比例还是 Compose 手势层的问题。
2. 对照地图专项文档和现有几何测试。
3. 验证缩放、旋转、拖拽、重建和切换地图后的状态是否一致。
4. 最后做真机验证，因为设备坐标、触摸事件和性能行为无法仅靠 JVM 测试覆盖。

## Git 文件范围

- 根目录 `agent.md` 是团队开发说明，保留在 Git 中。
- `.planning/`、`artifacts/` 仅用于本地计划、诊断日志、性能数据、预览和交付包，由根目录 `.gitignore` 整体忽略，不强制加入 Git。
- 需要随源码共享的设计、接口和验证结论应整理到 `docs/` 或对应模块 README，不依赖本地临时目录。
- 已跟踪的临时目录使用 `git rm --cached` 移出索引，保留本地文件；忽略规则不会清理已有提交历史。

## 交付前检查

- 只修改任务范围内的文件，保留用户已有改动。
- 运行受影响模块编译；有对应测试时运行单元测试。
- 若修改导航、构建、协议、公共状态或依赖，运行更大范围的构建/测试。
- 检查是否引入明文密钥、固定设备标识、生产地址或不应提交的构建产物。
- 在最终说明中列出修改内容、验证命令和因环境限制未执行的验证。

区域工艺保存与确认：`core:tcp/MapProcessSettings.kt`、`SlLinkManager.mapProcessReplies`；`MapScreenStep3ViewModel` 按map_id+region_id保存并确认区域工艺，MapRegionPoint查询按区域恢复草稿，启动/进入区域时由板端恢复；`MapProcessSettingsTest`。历史回放 `TaskReplayViewModel` 只读轨迹/底图，不调用规划接口；未归档规划路径明确提示。

区域工艺读回DTO：`core:model/state/MapPreviewStream.kt: WorkRegionProcessSettingsPayload`，`core:tcp/SlLinkManager.kt: parseMapRegionPoints`；只绑定当前返回的region_id，不从其他区补齐。

图纸地基导入：见 `docs/drawing_obstacles.md`。入口 `DrawingImportScreen` / `DrawingImportViewModel`、`core:dxf`、`core:tcp/DrawingGateway`。独立向量层只合入全局规划内存副本，不得写普通地图禁区或导航PGM；检查确认不发路径规划请求。
