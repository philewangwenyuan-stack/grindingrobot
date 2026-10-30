# TCP 通信模块

`TcpManager` 是业务入口，由 Hilt 以单例注入。`PairedTcpService` 管理同一设备 IP 的数据端口（默认 8002）和控制端口（8003），每条连接各自解析帧、排队和收发。

## 连接与恢复

- 使用 `connectConfiguredEndpoint()` 连接已保存的设备；`switchTcpEndpoint()` 保存并切换地址。
- `getConnectionStateFlow()` 提供数据连接状态，`channelStatus` 同时提供数据、控制就绪和设备占用状态。
- `onForeground()`、`onBackground()` 由 Activity 生命周期调用。后台取消遥控，亮屏先探测，再按 0.5 / 1 / 2 / 3 秒退避恢复。
- 连接组通过 SL-LinkA `0x0403/0x0404` 获取、校验令牌；会话心跳每秒一次。拒绝其他平板占用和过期令牌。
- 每条 socket 独立持有解析器；完整帧经 `PairedTcpService.Callback` 交付给 `SlLinkManager.handleParsedFrame()`。连接恢复与心跳统一由传输层管理。

## 请求与遥控

业务代码使用 `TcpManager` 的协议方法发送请求，避免发送未封装的文本或心跳。`ChannelRouting` 将速度、停止、软件急停及任务暂停/停止送到控制端口；任务启动/继续、地图和普通配置走数据端口。

`ManualDriveController` 提交最新遥控目标，传输层每 100 ms 独立发送。松手直接提交零速；速度不积压、不在重连后重放。主线程健康超过 300 ms 未更新，或控制写入/确认超过 500 ms，取消本次遥控。恢复连接后需重新操作。

速度续发不等待上一条 ACK，最多记录 8 条待确认速度；最早未确认且未被更新成功回执覆盖的速度仍受 500 ms 期限约束。板端可合并过时速度，更新速度成功后旧等待同步结束。停止、急停保持优先，旧运动代号的迟到拒绝不能取消新一轮操作。

`CommandReplyTracker` 按通道清理待确认请求；数据重连不得取消仍有效的控制确认。地图解码由地图模块的后台执行上下文处理。

任务底图通过 `TcpManager.loadMapSnapshot()` 获取，由 `MapSnapshotLoader` 先订阅再发送，严格按请求 ID 接收完整帧和失败结果。退出、超时及连接重置都会释放本次请求；地图尚未就绪最多尝试三次，失败后可在当前地图重试。开始研磨的地图导入响应还须匹配 `map_id`。

进入任务重定位时，通过 `requestMapModeAndAwait()` 等待定位模式启动；以响应帧 `ack_seq` 关联请求，不把 `enabled=false` 的失败回复过滤掉。断线立即结束等待，上一条停止回复不会结束本次启动等待。`manual_cancel` 日志包含主线程心跳间隔，便于区分界面卡顿与控制确认超时。

## 验证

任务历史统一通过 `TcpManager.loadTaskExecutionHistory()` 查询，设备统计的后台刷新与任务记录、作业统计串行等待，不再因共享接收权被占用直接提示重试。排队和接收总计限时 10 秒；超时、断线、发送失败或退出页面取消时释放本次查询。接收端按 `ack_seq` 接收当前请求的分片，丢弃上一轮的迟到回复。相关回归为 `TaskHistoryQueryTest`。

```powershell
.\gradlew.bat :core:tcp:testDevDebugUnitTest :app:assembleDevDebug
```

重点回归 `PairedTcpServiceTest` 和 `CommandReplyTrackerTest`。真机还需测连续地图传输、弱信号、黑屏亮屏和断线恢复；单元测试不代表实测延迟。
