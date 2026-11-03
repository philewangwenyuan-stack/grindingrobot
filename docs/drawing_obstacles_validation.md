# 图纸障碍离线验证记录

日期：2026-11-03。APP 起点 `3e4943a`；板端起点 `1688ac3a47a8965e20a424597047a005aaca202f`，两端工作分支均为 `codex/drawing-obstacles`。未推送、未部署或安装到设备。

## 已通过

| 范围 | 测试总数 | 失败 | 跳过 |
| --- | ---: | ---: | ---: |
| 最新 DXF 解析、单位、块阵列、配准、吸附、曲线限额 | 17 | 0 | 0 |
| 图纸任务策略 core:model | 2 | 0 | 0 |
| 最新 TCP 全套（含图纸协议和区域分片组装） | 146 | 0 | 1 |
| 地图选定回归：投影、工作流视口、路径缓存、擦除几何 | 20 | 0 | 0 |
| feature:main 全套 | 30 | 0 | 0 |
| feature:device 全套 | 64 | 0 | 1 |
| 板端图纸存储/提交/版本与授权 | 24 | 0 | 0 |
| 板端调度/地图隔离/区域分片集成 | 13 | 0 | 0 |
| 真实规划器绕障、不安全后备分支拒绝、忽略兼容 | 3 | 0 | 0 |
| 原有板端实时地图保存 | 6 | 0 | 0 |
| 原有板端规划预处理 | 9 | 0 | 1 |

最新 APP `:app:assembleDevDebug` 成功，输出 arm64-v8a、armeabi-v7a 两个 APK。最终加入区域分片后的构建再次运行 DXF 17 项、TCP 146 项及地图投影测试，均无失败；其余上表项目在此前同一实现分支运行。

协议 descriptor 比较确认原有 693 个字段的名称、编号、类型、标签及所有原有枚举编号均保留，`PolygonRegion.process=10` 未移动。APP 与板端 proto 字节相同；使用 protoc 25.1 同步生成全量 Java、lite Java/Kotlin、Python、nanopb。修改的板端源文件通过 Python 3.8 语法检查；离线执行环境为 Windows/Python 3.10，并非板端 ROS 运行环境。

图纸测试验证普通 MapService 栅格及 overlay 区域不被污染；草稿预览只在独立规划输入中注入。覆盖保存幂等、pending 重启重试、地图版本/几何变化、跨任务授权、停止并发授权撤销、损坏状态文件、完整路径线段、车体轮廓和追加的起步连接。大区域快照测试覆盖乱序、重复、损坏、过期、断线清理、完整回读和帧大小上限。

## 已复现的原有失败

- 地图完整 JVM 套件共 170 项：4 失败、1 跳过。失败均为本地已有、被 Git 忽略的 `TaskPathGeometryTest`。将该文件原样复制到原始提交的独立 worktree，12 项中相同 4 项失败；生产几何文件和这些断言均未修改。
- core:model 完整 JVM 套件共 24 项，其中原有 `NmeaParserAndAssemblerTest` 8 项失败，涉及校验和样例及 Android Log 未 mock；在原始 worktree 复现。新图纸策略 2 项通过。
- `:feature:map:compileDevDebugAndroidTestKotlin` 被已有 `ManualDriveUiTest.kt:288` 缺少 `onConfirmArea`、`onRemoveArea` 参数阻止；原始提交同样失败。这些测试未因本功能被修改。

## 尚未完成

当前无 ADB 设备、模拟器或 AVD，未进行原生页面截图及真实触控验收。Windows 缺少 `termios`，原有板端急停执行回归无法收集，未加入假实现绕过该限制。需在 Linux/Catkin 及设备环境继续运行运动、停止、急停与续跑回归。

尚未提供实际二维基础图进行现场适配和大图性能测量；不能以合成样例替代图纸核对或实车安全间距验收。完整现场步骤见 [图纸障碍说明](drawing_obstacles.md)。交付包仅供配套集成、评审和受控验证。

本地诊断日志、XML 报告、构建产物位于被 Git 忽略的 `.planning/drawing-obstacles/`；交付副本位于 `artifacts/drawing-obstacles/`，以该目录的 SHA-256 清单核对。
