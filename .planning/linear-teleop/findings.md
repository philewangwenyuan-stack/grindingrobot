# Findings
- Existing app defaults turnSpeed=70, but TcpManager ignores steeringSpeed. Existing protocol already includes max_turn_speed_ratio.
- Board mixes x/y independently. Use fixed M=.15, speed_ratio=1, y=direction*speed/M*(1-abs(x)), max_turn_speed_ratio=gain.
- This new proportional mix fits wheel envelope: abs(y)+abs(x)*gain<=1 for speed<=M and gain<=1.
- Current remote settings writes implicitly enable disc; new drive/turn slider must not invoke those writes.
- Android SDK/JDK17 installed; adb lists emulator-5554 and 127.0.0.1:16384. Use isolated library instrumentation screenshots, not production activity/network.
- Reference images: exec-ce4eced3-25c3-42ec-be8d-8de6dc3672d1.png (remote), exec-3418d0c2-b49e-4c4d-ac81-a13a8871a49d.png (map).
- Existing ManualControlSession tests protect immediate zero and late STOP ACK ordering; retain and extend equivalent shared controller guarantees.
## Taller scanning map follow-up
- The lower full-width control deck consumed map height even though travel and steering could occupy side columns. Extracted existing pads for reuse, preserving the remote deck layout and all existing input semantics.
- Scanning map now uses weighted center height with fixed left/right pads. A compact bottom status/stop footer aligns with cancel/next; pinch/pan remains inside the map. Actual 1600x966 captures show ~500px map height, previously ~350px.
- Common 18 unit tests and all 4 existing native integration tests passed; full production/test APK build succeeded. No controller or protocol changes in this layout follow-up.

## Global header alignment follow-up
- Existing Home header places emergency at TopCenter and status at TopEnd with a 14dp gap plus 36dp close slot, inside 24dp horizontal/14dp vertical screen insets. Scanning redesign had moved emergency to the far right.
- Shared GlobalTopBar now renders both pages with the original Home positions/emergency presentation. Scanning reserves the close slot without introducing another navigation action; map/footer input logic unchanged.

## Steering endpoint cyan-to-green investigation
- APP target at full steering is x=+/-1, y=0, speed_ratio=1, turn limit .7. Pure rotation remains motion and the 100ms sender stays active; a new >500ms held-endpoint test passes for both signs. Board dispatch classifies manual commands by their protobuf field, not by y being zero.
- Local board repo E:/CODE/C++/Grinder has manual_reverse_guard_sec=1.0, manual_command_timeout_sec=.5. Mixed wheel commands can reverse the inner wheel during large steering even when forward/backward selection stays unchanged. The guard publishes zero, and old code assigned _manual_command_active=bool(post_guard_moving), making indicator MANUAL fall to READY when disc is off.
- New fake-clock tests executing actual scheduler and resolver reproduced MANUAL->READY for both signs before the fix. Source now assigns manual activity from nonzero wheel request before the guard. Actual protected outputs and disc linkage are unchanged; explicit zero, watchdog and emergency still clear activity. This also keeps the watchdog active during the dwell instead of skipping it.
- Validation: APP ManualDriveControllerTest 10 passed. Board manual suites 7, indicator policy 14 + integration 17, scheduler emergency 18 and chassis safety 20 passed (76 board tests). Windows safety tests require POSIX termios and were rerun successfully under Ubuntu20.04 WSL/Python3.8. One scheduler output-barrier test timed out under parallel runs; sequential rerun all18 passed. Python3.8 compile checked. No field logs or deployed version observed, so other field causes are not excluded.
- Board pre-existing README/super_lio.yaml edits preserved; only added relevant README paragraph, scheduler activity fix and new regression file. Android production code/APK unchanged in this investigation. No deployment or physical movement performed.

## User correction: forward + steering, not a direction-button reversal
- The user rejects fixing only the light and clarified that forward stays selected while pulling left/right. Retracted the requested_moving activity change and the corresponding README claim; light/safety logic is back to the original behavior. Previous phase10 fix is historical and no longer active.
- Extended tests using the actual mixing/guard methods: five pass. Constant repeated full-turn commands do not restart the guard. Same-side input changing 20% -> 80% flips the inner wheel while forward stays selected; returning 80% -> 20% flips it back and triggers another dwell. This is an integration conflict between the new proportional mix and existing per-wheel reversal protection, not evidence of an operator switching forward/backward.
- With defaults speed=.05, wheel envelope=.15, turn gain=.7, continuous pre-rounding threshold is u=(.05/.15)/(.05/.15+.7)=~.3226. 20% steering gives wheel velocities approximately (-.061,-.019)m/s, 40% gives (-.072,+.012)m/s (swap sides for opposite steering). Actual firmware conversion rounds to integer RPM. Full-turn and linear attenuation requirements inherently allow per-wheel reversal, so changing the guard policy requires a deliberate motor-safety design; it is not solved by changing LED ownership.
- Only production change remaining is richer existing reverse-guard diagnostic text: previous/requested wheel RPM, remote axes and speed ratio. This distinguishes a real mixed-wheel sign flip from command timeout. No protection disabled, no device logs/deployment/hardware operation. Android held-endpoint regression retained.

## Direct steering-wait fix (supersedes phase11 diagnostic-only state)
- User asked to fix now rather than wait for another field reproduction. Chassis enable_cmd_ramp_limit is false in current YAML, so simply removing scheduler protection would leave immediate signed wheel reversals. Kept the existing timed protection as fallback and for simultaneous two-wheel reversal.
- Scheduler now permits early completion only for single-wheel reversal with two distinct, strictly increasing measurement timestamps after the stop request, valid/fresh feedback (within manual_command_timeout_sec), zero measured RPM on both wheels and zero target RPM on both. Invalid/stale/moving/nonzero-target/conflicting duplicate messages reset confirmation; duplicate cached zero reports do not count twice. Changing requested wheel signs invalidates old confirmation and restores full timed dwell. Explicit zero clears confirmation; a timer/callback alone never resumes motion.
- Chassis WheelSpeedState.header.stamp now preserves _actual_feedback_stamp (Modbus transaction midpoint estimate), and feedback_valid requires a measurement timestamp. Re-publication cannot make cached data look fresh. No message/protobuf changes and no indicator ownership change. Must update scheduler and driver together.
- New production-method regression contains 11 tests including both full steering sides, .2s fake-clock verified-stop release, timed no-feedback fallback, both-wheel reversal, stop/new-session and new-goal confirmation isolation. Existing wheel-feedback test now checks measurement stamp and missing-stamp validity.
- Final WSL/Python3.8 regression: manual queue3 + steering11 + indicator31 + scheduler emergency18 + chassis motion20 + wheel feedback5 =88 passed. Syntax and diff checks passed. Log .planning/linear-teleop/manual-steering-fix-board-tests.log. No ROS full build/live-topic, deployment, device logs or physical movement validation. Confirmed measured stop can shorten a necessary pause; no claim of zero physical pause or a fixed hardware response time.

2026-10-24：已回退整车等待提前释放实验，新增板端 ManualWheelTransition 单轮有界过零与双轮协调；保留真实前后反转完整等待、显式停止/急停/看门狗清零。纯状态机7测试通过，正在替换旧集成用例。用户确认磨盘只是问规则，未修改其启动语义。后续继续共同建图布局/视口和触感。

2026-10-24 最终本地验收：板端91项回归、APP common20项、MapBuildSessionStream5项、MapWorkflowViewport2项、原生Compose6项全部通过，生产/测试APK构建成功。91项包含回中时协调轮不超目标轮速、相同时间戳重发不突破斜坡、随机输入的轮速符号与前后包络。新增回中用例曾发现协调逻辑追求精确前后和而抹掉反向加速进度，已修复为允许小于目标的前后分量并回归通过。新截图已逐张检查。Step2–4为生产组件离线组合，未启动网络ViewModel；真实ROS运动/触感硬件待现场验证，未部署。
环境问题：原emulator-5554实际为MuMu，/data因既有问题只读，流式/推送安装均失败。保留原实例，新建MuMu index1（created_timestamp=1792803553022135，ADB16416）隔离完成截图；原实例显示设置已恢复。测试临时实例index1已关闭并删除，info all确认仅剩原index0。更广core:model旧NMEA8项失败，单独会话5项通过；此前main GNSS编译与地图4颜色断言限制仍记录，未扩展修复。

2026-10-24 任务启动报错定位：scheduler _start_execution检查_chassis_settings.run_speed<=0返回用户原文。RemoteSettingsViewModel提交磨盘与MapHome重定位磨盘/转向共3处只构造部分ChassisSettings，非optional proto3字段未发则解码0；板端handle_settings_write_request无条件覆盖run_speed并持久化。这条源码链路足以复现，现场最后写入尚无日志。新遥控速度是本会话参数，不恢复自动任务速度。已改为复制最近读取的完整底盘参数后只改目标字段，缺少快照则读取并拒绝写入；不更改任务零速保护/磨盘使能规则。正在验证。

任务零速修复验证：core:tcp全套88项通过（新增ChassisSettingsUpdateTest7项），板端执行速度/任务临时参数/急停34项通过。测试覆盖旧局部写入解码run_speed=0、磨盘0rpm保留任务速度与既有enable规则、转向保留其余参数、缺失快照不构建请求、原来明确为0不自动替换。生产APK打包仍在进行；没有更改或连接现场机器人参数，未改变板端启动保护或协议。

2026-10-24 任务零速修复交付：生产APK构建成功（task-zero-speed-fix-build.log，4m31s），TCP88项含新增7项、板端相关34项通过。APP三处入口已接专用完整快照API；协议、板端实现、任务启动零速保护均未改。没有读取现场日志或写现场参数，用户本次现场最后一次写入来源未由日志证实，但旧局部报文零速覆盖链路已由源码与协议往返测试复现。已有持久化0需用户在工艺设置恢复原值，新APK仅防止再次误写。未部署或操作机器人。
