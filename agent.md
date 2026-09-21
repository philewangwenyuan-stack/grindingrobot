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

本工作区当前无法直接运行 Gradle 验证，因为终端没有配置 `JAVA_HOME` 且 PATH 中没有 `java`。具备 JDK 后，应至少运行受影响模块的编译和单元测试；涉及入口、依赖、构建逻辑或全局状态时再运行完整构建/测试。

## 模块职责

### 应用与导航

- `app`：`Application`、`MainActivity`、`AppNavHost`、最终 APK 配置。
- `navigation`：路由数据类、导航事件、结果回传和 `AppNavigator`。
- `build-logic`：统一 Android、Compose、Hilt、feature 模块的 convention plugin。修改模块构建行为优先改这里，不要在各模块重复堆配置。

### Feature

- `feature:connect`：设备连接页面和连接流程。
- `feature:main`：主框架、首页、任务记录/回放、统计、OTA。
- `feature:map`：地图目录、编辑步骤、区域/禁区、路径规划、开始研磨。
- `feature:device`：设备状态、任务地图及设备侧业务。
- `feature:common`：跨功能公共 UI、弹窗、状态栏和资源。
- `feature:remote`：相对独立的远程控制预留模块，入口和完整度需要结合设备联调确认。

### Core

- `core:model`：请求/响应模型、业务模型、地图/任务模型和跨页面状态流。
- `core:data`：Repository、全局 `AppState` 和数据层编排。
- `core:tcp`：TCP 连接、请求发送、响应解析、任务数据分片组装。
- `core:sllink`：SLLink 帧、CRC、消息构建和生成的消息代码。
- `core:network`：HTTP/Retrofit、OTA 服务及网络数据源。
- `core:database`：Room 数据库和本地数据源。
- `core:datastore`：配置、用户信息等持久化。
- `core:designsystem`、`core:ui`：主题、图标和通用 Compose UI。
- `core:common`、`core:util`、`core:result`：基础类、工具、统一结果处理。
- `core:bluetooth`、`core:udp`、`core:websocket`：辅助通信能力。

## 重要修改规则

1. 修改设备协议前，先确认 Android 与嵌入式端的消息 ID、组件 ID、字段顺序、字节序、长度和 CRC 约定。
2. `core/sllink/src/message_gen/` 是协议生成代码，不要直接手改。应从协议源重新生成，并同步验证解析测试。
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

- 默认设备 TCP 地址在业务代码中存在 `192.168.11.2` 约定；修改连接配置时同时检查 MMKV 持久化、断线清理和连接页面回显。
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

## 交付前检查

- 只修改任务范围内的文件，保留用户已有改动。
- 运行受影响模块编译；有对应测试时运行单元测试。
- 若修改导航、构建、协议、公共状态或依赖，运行更大范围的构建/测试。
- 检查是否引入明文密钥、固定设备标识、生产地址或不应提交的构建产物。
- 在最终说明中列出修改内容、验证命令和因环境限制未执行的验证。

