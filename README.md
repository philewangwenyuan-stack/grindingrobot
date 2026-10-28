# GrindingRobot

## 双通道控制与亮屏恢复

当前生产链路为 `TcpManager → PairedTcpService → 数据8002/控制8003 → SL-LinkA`，两通道使用同一设备 IP。
新安装默认 `192.168.1.3`，保留已保存的地址。先在数据端 OPEN 获取令牌，再在控制端 BIND；
已有平板在线时拒绝第二台。两端必须同步升级协议 `0x0403/0x0404`，无单端口运动控制回退。

遥控、直接设备控制、任务暂停/停止和软件急停/解除走 8003；地图、配置、状态、历史和任务启动/继续走 8002。
`ChannelRouting` 按命令内容区分同一个 TaskCommand 消息。每个 socket 独立解析与恢复，
命令确认按通道隔离，数据重连不撤销仍健康的控制端。

`ManualDriveController` 只发布目标变化/零速，100ms 周期发送由后台 transport 持有，最多一条未确认速度。
主线程健康标记超过300ms、后台/黑屏/退出遥控时撤销目标；写入或遥控ACK超过500ms关闭控制端。
重连不恢复速度，必须重新操作；平板失联不暂停已运行的自动任务，仍由板端执行安全保护。

亮屏与 Wi-Fi 恢复立即探测，300ms 无响应重建对应通道，跳过固定5秒等待；失败退避0.5/1/2/3秒。
有效令牌恢复旧连接组；令牌过期重新准入，另一平板已占用时停止抢连。
Wi-Fi 已恢复、设备空闲时，亮屏后1～3秒恢复控制就绪是待实机验证目标，不包含 Wi-Fi 关联时间或地图下载时间。
连接页分别显示数据和控制状态及设备占用；`ControlChannel` 日志采样记录恢复耗时、遥控RTT和撤销原因。

地图尺寸在后台解码并随冻结快照缓存；共享 `rememberWorkflowBitmap` 在后台解码显示图片。
本机 TCP 回归为 `:core:tcp:testDevDebugUnitTest` 中的 `PairedTcpServiceTest`，不连接真实设备。

## 项目概述

GrindingRobot 是研磨机器人 Android 上位机。系统整体由三部分组成：

- Android 上位机：提供设备连接、状态展示、地图管理、路径规划、研磨任务操作、任务记录与应用升级等交互能力。
- 嵌入式端：运行在工控机侧，接收 Android 端指令，处理雷达、定位、任务和设备协议。
- 研磨机实体设备：执行移动、研磨等实际动作。

Android 端不直接控制研磨机硬件，而是主要通过 TCP + SLLink 协议与嵌入式端交互，再由嵌入式端驱动实体设备。交接时应优先理清“页面/ViewModel → TcpManager → SlLinkManager → 嵌入式端 → Stream 状态 → UI”的主链路。

## 当前业务范围

目前代码主要覆盖以下业务：

1. 设备连接与全局状态
   - 默认 TCP 地址为 `192.168.1.3`。
   - 成功连接过的地址会通过 MMKV 保存。
   - 连接断开时会清理设备状态，避免界面继续展示失效数据。
2. 地图与作业区域
   - 地图列表、新建、编辑、删除。
   - 作业区域、禁区、扫描方向和路径规划。
   - 地图编辑采用分步骤页面，相关路由位于 `MapRoutes`。
3. 研磨任务
   - 下发任务、接收任务状态、展示规划路径和设备轨迹。
   - 包含任务记录、任务回放及作业统计相关实现。
4. 设备信息与控制
   - 展示设备运行、定位及任务状态。
   - `feature:device` 已承载设备状态和任务地图等逻辑。
   - `feature:remote` 模块已保留，但当前依赖和实现相对独立，接手后应结合实际设备联调确认其入口及完整度。
5. 应用 OTA
   - 支持检查版本、刷新下载地址、断点续传、文件大小与 SHA-256 校验、包名/版本/签名校验、调用系统安装器以及升级事件补报。
   - 应用启动和回到前台时会处理安装结果及待上报事件。

## 关键业务链路

### 设备通信

设备相关请求通常由功能模块的 ViewModel 发起，经 `core:tcp` 中的 `TcpManager` 发送；`SlLinkManager` 负责协议响应解析和分发；解析后的地图、路径、任务及设备状态写入 `core:model` 中的 Stream，再由 ViewModel/Compose 页面订阅。

排查设备交互问题时建议按以下顺序检查：

1. TCP 是否已连接、目标 IP 是否正确。
2. 请求是否从 ViewModel 正常发出。
3. SLLink 消息类型、字段及生成协议是否与嵌入式端一致。
4. 响应是否进入对应 Stream。
5. 页面收集的 StateFlow/事件是否与业务状态匹配。

`core:sllink/src/message_gen` 下为协议生成代码，不应直接手工修改。协议发生变化时，需要先与嵌入式相关人员确认并重新生成。
定位状态由 `DeviceStatusReport.localization_ready`、`localization_pause_latched` 和
`localization_status_reason` 上报。`DeviceEventTracker` 将定位暂停与恢复边沿写入现有消息中心；
旧板端缺少可选字段时保持未知，重连后若仍处于定位暂停则展示当前告警。

### 地图与任务

地图功能主要集中在 `feature:map`，包含地图目录、编辑步骤、区域/禁区编辑、路径规划和开始研磨等页面。任务执行过程中的路径、轨迹、地图显示缓存和调度状态主要由 `core:model` 的状态流承载，部分历史数据通过 Room 保存。

地图显示涉及坐标转换、旋转、缩放和手势联动。修改相关逻辑前，可先阅读 `docs/` 下的地图旋转及机器人图标缩放说明，并运行对应几何与状态测试。

### Super-LIO 地图定位

地图目录中的 `map_revision` 是保存地图资产的 SHA-256 标识，与地图区域编辑版本 `map_version` 不同。开始重定位时，APP 先等待所选地图导入和快照完成，再请求定位模式，并核对设备返回的 `map_id/map_revision`。初始位姿请求必须携带同一组身份；收到 `accepted` 只表示请求已受理，APP 继续查询状态，只有地图身份匹配且生命周期为 `READY`、质量帧数达标时才显示定位成功。保存地图的反馈会分别显示地图保存、建图节点停止和定位模式启动结果。

### OTA 升级

OTA 核心实现位于：

`feature/main/src/main/java/com/sinelynx/grindingrobot/feature/main/ota/ApkOtaManager.kt`

> **重要：当前 OTA 的 `installId` 是联调用的固定测试 ID：`9bd805bf-7c43-4c66-ba7f-08aed61b1877`。正式部署前必须替换。**

后续应将其改为“每台设备独立、首次生成后持久化、卸载前保持稳定”的设备安装标识。升级检查、下载地址刷新和事件上报必须使用同一个 `installId`，否则可能导致测试授权、灰度分桶和升级事件归属错误。代码中虽然保留了 `KEY_INSTALL_ID`，但当前 `installId()` 仍直接返回上述固定值。

OTA 还依赖以下约定：

- 调试包使用 `test` 渠道，非调试包使用 `production` 渠道。
- OTA 服务地址和允许下载的域名在 `ApkOtaManager` 中有固定校验。
- 下载完成后必须通过大小、摘要、包名、版本号和签名校验才会提交安装。
- 安装事件先写入本地队列，网络失败时会在后续启动或检查更新时继续补报。

## 项目结构

项目使用 Kotlin 多模块架构，主要模块职责如下：

| 模块 | 职责 |
| --- | --- |
| `app` | 应用入口、Application、MainActivity、顶层导航和 APK 构建 |
| `navigation` | Compose Navigation 路由、导航事件和页面跳转 |
| `feature:main` | 主框架、首页、设置、任务记录/回放、统计和 OTA |
| `feature:common` | 功能层共享组件、状态栏、弹窗和公共资源 |
| `feature:map` | 地图管理、区域编辑、路径规划和研磨任务 |
| `feature:connect` | 设备连接页面与连接流程 |
| `feature:device` | 设备状态、任务地图及设备相关业务 |
| `feature:remote` | 远程控制预留/独立功能模块，需结合设备联调确认完成度 |
| `core:tcp` | TCP 连接、请求发送、响应解析与任务数据分片组装 |
| `core:sllink` | SLLink 帧与协议生成代码 |
| `core:model` | 请求/响应模型、业务模型和跨页面状态流 |
| `core:data` | Repository、全局 AppState 和数据层编排 |
| `core:network` | HTTP 接口、OTA 服务及网络数据源 |
| `core:database` | Room 数据库及本地数据源 |
| `core:datastore` | 配置和用户信息等持久化能力 |
| `core:designsystem`、`core:ui` | 主题、图标和通用 Compose UI |
| `core:util`、`core:common`、`core:result` | 工具、基础类和统一结果处理 |
| `core:bluetooth`、`core:udp`、`core:websocket` | 辅助通信能力 |
| `build-logic` | Android/Kotlin/Hilt 等约定插件和统一构建逻辑 |

## 技术栈

- Kotlin、Coroutines、StateFlow
- Jetpack Compose、Navigation Compose
- Hilt + KSP
- Room、MMKV
- TCP、SLLink、OkHttp、Retrofit
- Gradle Kotlin DSL、Version Catalogs、类型安全项目访问器
- SceneView / Filament（3D 展示相关）

## 开发与构建

推荐环境：

- Android Studio Panda 2（2025.3.2）
- JDK 17 或更高版本
- Android Gradle Plugin 8.13.1
- Kotlin 2.2.21
- Compile SDK 36、Target SDK 36、Min SDK 26

使用 Android Studio 打开项目根目录并等待 Gradle 同步完成。运行时选择 `app` 配置，并连接 Android 设备或启动模拟器。

完整构建：

```powershell
.\gradlew.bat :app:assembleDevDebug
```

构建产物位于 `app/build/outputs/apk/`。项目按 `armeabi-v7a` 和 `arm64-v8a` 分别生成 APK，不生成通用 APK。

开发时可优先编译受影响模块：

```powershell
.\gradlew.bat :feature:map:compileDevDebugKotlin
.\gradlew.bat :feature:common:compileDevDebugKotlin
```

涉及应用入口、导航、依赖关系或全局构建配置时，应执行完整应用构建。

## 交接与维护注意事项

1. 首次接手建议先跑通设备连接、地图加载、路径规划、任务下发和状态回传这一条完整链路。
2. Android 端协议高度依赖嵌入式端实现，接口或字段调整需要双方同步确认。
3. 不要直接修改 SLLink 生成代码；应从协议源重新生成。
4. 跨模块使用资源时，应引用资源所属模块的 `R`，例如：

   ```kotlin
   import com.sinelynx.grindingrobot.feature.common.R as CommonR
   ```

5. 地图和轨迹逻辑包含较多坐标、旋转和缩放计算，修改后应优先运行相关单元测试，并进行真机地图操作验证。
6. OTA 正式交付前必须完成独立 `installId` 的生成与持久化改造，并分别验证 `test`、`production` 渠道。
7. 项目仍保留部分模板痕迹、备用通信模块和未完全统一的注释。判断功能是否可用时，应以应用入口、导航接入、模块依赖和实际设备联调结果为准。
8. 修改公共 UI、状态流或通信协议时，注意评估所有依赖模块，避免只验证单个页面。

## 相关文档

`docs/` 目录当前包含地图旋转、机器人图标缩放及手势联动等专项设计与排障说明。涉及相应功能时，应先阅读这些文档，再结合测试和真机行为确认。

## 许可证

暂无。

## 遥控松手响应

摇杆持续指令周期为100ms。`ManualControlSession` 将待发送移动目标与任务停止等待分开：
松手立即发送零速并清空待处理目标，任务STOP回执晚到也不能恢复旧移动。
自动任务接管仍需确认；仅打开自动模式提示但未接管时不发送遥控运动。
板端也需同步最新指令队列、执行代号和0.5秒失联兜底配置，参见Grinder根README 7.3。
平板代码修改后需重新构建并安装APK；CPU/USB灯效不能代替实车停车反馈验收。


## 现场控制与状态修正（2026-10-25）

TCP输出统一串行写；手动发送只保留最新待发速度，停车优先，移动在写入前检查500ms有效期和连接会话。
不等待上一条手动ACK、不重试旧移动。页面退出/后台/断连继续调用既有遥控停止逻辑。
同输入在途规划合并；输入改变取消旧等待，响应使用已有request_id隔离，不展示虚构百分比。
状态栏/消息中心电量明确为平板电量，首次有效Android广播前显示未知；不代表底盘电池。
里程计置信度与导航就绪分开，过期状态不显示为正常。RPP通过既有任务消息提供侧障/前障距离、扫描过期和TF失败原因。
本轮不增加Protobuf字段、不安装APK。模块验证使用DevDebug flavor：
`gradlew :core:tcp:testDevDebugUnitTest :feature:common:testDevDebugUnitTest :feature:device:testDevDebugUnitTest :feature:map:testDevDebugUnitTest`。
