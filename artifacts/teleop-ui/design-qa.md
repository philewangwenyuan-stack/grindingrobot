# Native teleop UI verification

final result: passed

## Sources
- Accepted remote mock: exec-ce4eced3-25c3-42ec-be8d-8de6dc3672d1.png.
- Accepted mapping mock: exec-3418d0c2-b49e-4c4d-ac81-a13a8871a49d.png.
- Final native captures: remote-control.png, mapping-control.png and mapping-zoom.png, captured by ManualDriveUiTest on 2026-10-23 (Asia/Shanghai). Map captures include the shared global header, shorter footer and side-panel settings. mapping-zoom.png shows the scanning map at 2x after pinch/pan and live-frame checks. mapping-control-before-sidebar.png retains the earlier layout for comparison.

## Verified
- Remote control content has no map; three parameter sliders, latched two-zone travel buttons, independent stop and horizontal-only steering are visible.
- Mapping retains the existing map renderer/robot footprint and steps. Speed slider and travel buttons occupy the left panel; turn slider and steering occupy the right. Compact slider tracks use the available panel width with +/- buttons on a separate row. Direction/reduction/speed share one status row over a 48dp stop button, with cancel/next at the footer ends. At 1600x966 the map viewport grew from ~547px to ~697px (+27%) by removing the top settings row. All labels/values/limits/controls/hints fit in active and unavailable captures; remote default slider layout is preserved.
- Scanning and Home reuse GlobalTopBar: title-left, centered emergency, right-aligned status with identical 24dp horizontal/14dp top insets and reserved close slot. Home's emergency presentation/state callbacks are preserved. Final map captures checked for overlaps and clipping.
- Defaults show turn gain 70%. At 50% steering, command speed is 0.025 m/s for a 0.05 m/s setpoint; no fixed steering cutoff zones are drawn.
- Buttons, text, numeric values and slider handles fit; +/- vectors retain their glyphs. Right joystick end labels no longer clip against the circular well.
- UI integration verified first opposite click stops, next click reverses; speed slider and 1%/50% steering update command state; a held old joystick cannot restart after stop.
- Scanning map supports pinch zoom and pan using the existing preview transform and 1–8x limits. Map and robot marker share the viewport; incoming frames retain zoom. The fixed lower-left scale button resets to fit. Gesture, frame replacement, limits and reset test produced no drive callbacks.
- P0/P1/P2 issues from initial capture (portrait test orientation, tinted glyphs, thin handles, clipped remote scale) resolved and recaptured.

## Capture boundaries and minor follow-up
- These are actual production Compose components in an isolated test host, with a memory-only sender. Remote capture shows the content area and a test-host preview title, not the outer home navigation chrome.
- Mapping frame/pose are synthetic test data. Wi-Fi/battery/time are emulator status. No live robot data, deployment or movement validation is claimed.
- Existing production colors/typography/global chrome are retained instead of recreating generated mock pixels literally. The larger side joystick improves scale-label spacing; controls, hints and footer were inspected in the new captures. Physical tablet operation remains unverified.

## Verification results
- Final production and test APK build: successful; side-settings log .planning/linear-teleop/map-side-settings-build.log; shorter-footer log .planning/linear-teleop/map-footer-build.log; header log .planning/linear-teleop/global-header-build.log; layout log .planning/linear-teleop/map-taller-build.log; previous zoom log .planning/linear-teleop/map-zoom-build.log; original release log .planning/linear-teleop/release-verification.log.
- Shared common tests: 18 passed, including 9 teleop control tests.
- TCP tests: 81 passed, including turn-limit encoding and legacy defaults.
- Device tests: 49 passed.
- Map viewport/marker/relocalization geometry tests: 9 passed, including 4 new viewport tests.
- ManualDriveUiTest: 4 passed after moving settings into side panels; final instrumentation log .planning/linear-teleop/map-side-settings-ui.log.
- Broader existing map suite: 130 passed, 4 TaskPathGeometryTest color assertions failed in unchanged path-rendering behavior.
- Broader main test compilation blocked by obsolete GnssData field references in MainViewModelGnssDataEquivalenceTest. Main production/app compilation succeeded; GNSS tests were not rewritten.

## 2026-10-24 连续转向、触感及四步一致性

- 已撤回整车先停的提前放行实验。新板端单轮过零和另一轮协同控制91项回归通过；没有改灯态掩盖停车。普通弧线转向不人为全车固定停车；满杆目标前后速度为0，允许必要的瞬时归零。未部署或做实机/ROS验证。
- 20项common测试（含10项控制器、1项触感事件）、5项会话和2项视口测试通过。生产/测试APK成功，日志 phase14-final-captures-build.log。旧NMEA8项、GNSS与轨迹颜色的广泛测试限制与本次变更分开记录。
- 最终6项 ManualDriveUiTest 通过，日志 phase14-native-ui.log。触感计数覆盖端点保持不重复、回中、停止、禁用与确认门控；不声称模拟器能验证平板物理触感。
- 新图 mapping-control.png、mapping-step2.png、mapping-step3.png、mapping-step4.png、mapping-zoom.png、remote-control.png 均1600×966。Step1/遥控为实际内容组件，Step2–4为生产共同顶栏/导航/地图/编辑工具/列表/保存面板的离线组合，没有运行网络ViewModel或提交地图编辑。Step3列表态的地图预览用共同投影渲染器，未截图所有编辑子模式。
- 同尺寸下地图边界、取消及下一步/保存位置一致；范围1–8倍，图中后续步骤保持前一步2倍视角，返回扫描仍为2倍。保存面板采用纵向输入，不挤压字段。标题、急停、状态沿用全局位置。
- 原MuMu数据分区只读；新建隔离实例完成测试，不修改原实例磁盘或用户应用。新建index1实例测试后已关闭并删除，原实例保留。

## 2026-10-24 任务零速补丁
修复遥控磨盘及重定位磨盘/转向三处不完整底盘参数写入，避免遗漏run_speed被板端当成0覆盖。TCP88项（新增7项）与板端执行/临时参数/急停34项通过，最新APK构建成功，日志task-zero-speed-fix-build.log。UI布局未变，无需重拍截图。已被清零的工艺速度须由操作员恢复原值；没有现场参数写入、部署或机器人操作。
