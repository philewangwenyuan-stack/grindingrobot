# Findings

Home DeviceStatusScreen uses separate todayWorkTimeText and totalWorkTimeText. summarizeDeviceStatistics clips today's interval to local midnight and totals entire valid interval; an existing yesterday+today test already expects3600 vs7200. VM requests unbounded history with no date/map/task filter. Need determine whether field data has earlier records, timestamp issues, retained active records or clock mismatch. Async question pending.
# Field verification 2026-10-26

Read-only SSH and live 0x0530 history query on existing board endpoint. Current live task report is IDLE with blank task ID. Returned all 9 retained records (same as persisted registry), all started 2026-10-24; persistence enabled. Thus equality on that date can be legitimate; no proof of swapped UI bindings or today-only response.

Confirmed separate timing bug: 2 old records RUNNING/finished_at=0 survive restart, while live task is IDLE. Previous APP extended both to now. At live capture 00:25:07, old calculation yields today 3014 s and total 239818 s. Seven settled records total 3175 s, today settled total 0 s. Exact orphan durations cannot be trusted/recovered: one trajectory's offsets remain 0 and registry count is 0 despite 1226 stored samples; other last trajectory offset386484ms is only a last observation, not a confirmed finish.

APP correction validates current task against fresh runtime status and current history generation, permits only newest execution of that task, and marks unknown durations as lower bounds with an unsettled-record notice. No fabricated historical finish, device writes or deployment. Baseline 8 targeted tests pass; first modified device suite passed; final suite + APK build in progress.
