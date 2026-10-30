# Findings

- Initial git status: only pre-existing gradle/libs.versions.toml modified; preserve it.
- RemoteSettingsViewModel and MapViewModel share ManualDriveController. travel() currently stops only for opposite direction.
- PairedTcpService owns 100ms speed renewal: target is updated immediately in memory but waits for the next sender cycle (25ms polling, nominal 100ms renewal); zero is urgent. ACK tracing already reports RTT once per second.
- Local board indicator worker sleeps send_interval_sec (default/config 0.5s) after each serial send. Need deployed configuration verification.
- Main remote first activation waits for TASK_STOP ACK; mapping does not use that preparation.
- Read-only SSH verified deployed scheduler_tick_hz=5, indicator send_interval_sec=0.5, backend=serial. Deployed activity is published only by _publish_status() at scheduler tick (200ms). Indicator worker then sleeps 500ms after USB send. Three sampled READY outputs all acknowledged=true, error empty; no active manual operation captured.
- Expected phase waits can accumulate to roughly 0.8s (100ms sender + 200ms activity + 500ms light), excluding task-stop preparation/network/USB/lock contention. This is a scheduling estimate, not measured end-to-end latency.
- Four-second passive sample: control_activity 20 samples, interval 193.11–206.69ms, mean199.83ms; indicator nine READY samples, all USB acknowledged with empty error. Indicator max interval507.25ms; first interval210ms is affected by latched initial status so overall mean467ms should not be reported as steady cadence.
- No ADB devices attached, so live tablet ACK RTT / input-to-light correlation could not be captured. No motion commands, settings changes or board restarts were issued.
- Diagnostic workstation-to-board ping five packets: 0% loss, mean3ms, max13ms. This is not a measurement of tablet control-channel RTT and cannot exclude transient Wi-Fi latency.
- Recommended board improvement: publish activity on actual manual activity transitions and wake indicator worker on resolved state changes, preserving periodic heartbeat and emergency precedence. Investigation only requested for lamp, so board code/runtime left unchanged.
