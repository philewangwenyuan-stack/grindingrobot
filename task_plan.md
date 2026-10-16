# 任务计划：机器人设置与 RPP 导航参数评估

## 历史目标

将地图列表“+”按钮改为：发送 `0x0512 MapModeRequest`，等待 `0x0513 MapModeResponse` 成功后进入 Step1，再启动 `0x0304` 地图快照轮询；同时核对并说明 APK 打包方式。

## 当前目标

使导航与建图地图中的机器人标记按机器设置 Footprint 的真实米制形状/尺寸绘制，并以 base_link 为运动及旋转中心；验证构建。

## 本轮目标（2026-10-09）

修复旋转按钮、中心与双箭头标记，紧凑设置三页并调整四向距离。

- [complete] 29. 实现图标及设置页调整
- [complete] 30. 三模块编译、地图标记/重定位定向测试与 device 测试通过；全量测试限制见下

## 阶段

- [complete] 1. 梳理现有协议、状态流和按钮入口
- [complete] 2. 补齐 SLLink/TCP 的建图模式请求与响应
- [complete] 3. 修改 UI 启动流程和 Step1 快照轮询
- [complete] 4. 增加/检查测试并执行可用的验证
- [complete] 5. 整理 APK 打包命令与产物位置
- [complete] 6. 重定位页面改为地图 + 初始位置设置布局
- [complete] 7. 同步初始位姿协议模型并接入 0x052A
- [complete] 8. 编译、协议测试并重新打包 APK
- [complete] 9. 修正重定位箭头样式与地图坐标/方向一致性
- [complete] 10. 核对机器人外形/坐标与 RPP 参数的端到端可写链路
- [complete] 11. 同步 Android 协议模型并接入设置读写与地图删除结果
- [complete] 12. 实施机器人设置与 RPP 页面交互
- [complete] 13. Android 端模式、导入、重定位请求去重与状态轮询限流
- [complete] 14. 运行地图回归测试并构建 APK
- [complete] 15. 按参考图重做机器设置与 RPP 页面视觉布局
- [complete] 16. 编译、视觉检查并重新打包 APK
- [in_progress] 17. 修正重定位编辑态、中心点/箭头标记和地图交互
- [pending] 18. 将导航参数收敛到核心字段并统一设置页风格
- [pending] 19. 增加路径规划参数页面并核对板端协议接入边界
- [pending] 20. 做静态检查与定向验证（本轮不默认打包）
- [complete] 21. 重绘机器人外形与坐标示意
- [complete] 22. 编译验证示意图改动
- [complete] 23. 统一三页容器、卡片、输入框与底部操作栏
- [complete] 24. 增加机器设置动态轴向距离标注
- [complete] 25. 定向验证、编译并生成 devDebug APK
- [complete] 26. 梳理地图标记调用点、设置状态与地图坐标变换
- [complete] 27. 统一 Footprint 地图标记绘制并接入导航/建图页面
- [complete] 28. 增加定向测试、编译并生成 devDebug APK

## 决策

- 新建地图使用统一地图 ID `LIVE_MAP`。
- `MapModeResponse` 必须先判断 `RESULT_SUCCESS`，再导航和启动 `startMapSnapshotLoop`。
- 现有生成的 `SlLink.java` 已包含 `MapModeRequest/Response`，不手工修改生成代码。
- 初始位姿使用 ROS map frame 的米制 `x/y` 和角度制 `heading_deg`；微调步长为 0.05m，旋转步长为 5°。
- 重定位页面复用 StartGrindingSessionUiState.mapPreview，地图坐标换算与地图对齐旋转保持一致。
- 重定位页面必须复用其他地图页面的 `worldPointToBitmapOffset` / `taskBitmapPointToFittedPoint` 投影链；箭头屏幕角度使用 `90 - headingDeg + displayRotation`。
- 机器人设置中的多边形点、`base_link -> base_laser_link` 和 RPP 参数必须落到板端配置/运行时接口；若当前 SL-Link 没有字段，需要先扩展协议及 scheduler，再实现 Android UI。

## 错误记录

| 错误 | 尝试 | 处理 |
| --- | --- | --- |
| 当前终端曾无 `JAVA_HOME`/`java` | 前序检查 | 已安装 Temurin JDK 17，并配置用户级 `JAVA_HOME` 与 PATH |
| Gradle 无法启动 | 前序执行构建命令 | 已通过显式 JDK 环境变量重新执行 |
| Android SDK 路径无效 | 配置 JDK 后重新执行 Gradle | 已安装 SDK command-line tools、platform-tools、API 36、Build Tools 36.0.0，并修正 `local.properties` |
| 在工作区根目录执行 git diff | 本次排查 | 当前工作区根目录不是 Git 仓库，改用文件内容和构建验证检查变更 |
| 并行读取源码时 PowerShell 进程异常退出（-1073741819） | 本轮读取文件头 | 输出已完整返回；随后读取其余源码并完成编译验证 |
| 搜索不存在的 feature/main/src/androidTest 目录 | 本轮 rg 检查 | 仅搜索实际存在的测试目录，不影响源码分析 |
| 首轮编译发现设置页双栏括号位置错误 | 修改页面布局后编译 | 已修正，Kotlin 编译及 devDebug APK 构建均通过 |
| PowerShell 将 rg 的 Bash 花括号路径解析为语法错误 | 查找多个 ViewModel | 改为目录级 rg 搜索，不复用该写法 |
| 搜索不存在的 MapGeo.kt 路径 | 查找地图坐标定义 | 定位到 MapScreenStep2ViewModel.kt 中的 MapGeo 定义 |

## 2026-10-09 验证限制

- 地图全量测试 4 个路径颜色断言失败（相关文件未修改）。
- main 全量测试编译被既有 GNSS 字段引用错误阻断（相关文件未修改）。


## 2026-10-09 地图列表卡顿

- [complete] 31. 排查地图列表 APP 卡顿，移除收包格式化及大日志、后台缩略图缓存、列表请求去重与分段计时
- [complete] 32. map/main/device 编译、TCP 56 项与地图定向 8 项测试通过

- 本轮首轮编译：ImageBitmap 委托属性不能智能转换，已改为局部 value 引用。新增测试原路径被模块规则忽略，移到已跟踪测试目录布局，保持 Kotlin package 不变。


## 2026-10-09 单箭头及单页参数布局

- [complete] 33. 单箭头、导航/重定位真实轮廓、恢复 base_link 坐标轴、紧凑参数分组布局
- [complete] 34. 编译测试并打包最新 devDebug（地图定向 5 项、设备 27 项通过）

## 2026-10-10 暂停、结束和急停反馈

- [complete] 35. 修复任务回复关联、重复回复等待、暂停状态文案和急停确认反馈
- [complete] 36. TCP 66 项、设备 27 项测试通过；最终 devDebug APK 编译成功

- [complete] 37. 确认并修复关闭重定位页面误发定位停止请求，devDebug 编译打包成功

## 2026-10-10 导航运行参数调节

- [complete] 38. 已通过 SSH 只读核对设备源码，补充任务专用临时接口 0x0538/0x0539；板端修改位于本地 E:/CODE/C++/Grinder
- [complete] 39. 实现导航页调节、设定/实测区分、请求关联和失败反馈
- [complete] 40. TCP 69 项、设备 32 项、板端离线 9 项测试通过；devDebug APK 编译成功。设备服务尚未部署新接口，未做实机调速

## 2026-10-15 消息中心事件记录

- [complete] 41. 接入 APP/工控机连接、RPP 障碍停车、工控机/底盘连接事件，按变化去重，本地保留最近 500 条及记录时间
- [complete] 42. 同步 optional chassis_connected=21、板端状态及 3 秒上报超时判断，生成各 SDK；旧字段缺省不误报
- [complete] 43. TCP 74 项（新增 5 项）与板端 4 项通过，devDebug 编译成功；已打包两个 ABI APK 和板端更新包，未部署或操作硬件
- [complete] 44. 消息卡片紧凑布局，默认完整显示三条；新增数量与可拖动滚动条，common 9 项测试通过，devDebug 编译打包成功
- [complete] 45. 修复断网重连 footprint 重置：保留同设备已确认轮廓，每次连接自动读实际参数，切换设备清理旧参数；5 项回归、TCP 74 项通过，devDebug 编译成功
- [complete] 46. 平板接入解除急停：enabled=false、严格回复、现场确认、板端结果显示及急停优先；TCP 77 项与新增 main 4 项定向测试通过，独立输出目录 devDebug 构建成功
- [complete] 47. 单按钮跟随实际急停状态；新增可选锁存上报，TCP 78 项和 main 9 项定向测试通过，devDebug APK 与板端更新包生成，尚未部署实机
