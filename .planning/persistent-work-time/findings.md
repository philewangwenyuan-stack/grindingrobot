# Findings

Existing board has no independent clock ledger. task_registry.json stores execution history and deletes remove records; APP derives both time and area from history. Existing history protocol (0x0530/0x0531) carries JSON bytes and can accept an optional independent work_time_statistics object without changing protobuf/generated SDK. APP parser currently ignores unknown fields.

Prior field read: 9 records from Oct24, 7valid ended records3175s and2orphanRUNNING. CurrentIDLE. These remain untouched on board. New ledger needs one-time seeding of only known closed durations and permanent incomplete-baseline marker for missing durations. Live accounting must be independent of the history/result-image path, and must persist outside the motion/control callback.

Implementation: WorkTimeStatistics persists total/per-day milliseconds and a legacy/missing-tail count in separate JSON. Single daemon writer, accounting lock released before fsync+replace; idle unchanged payload skips disk writes. Lifecycle integration is at begin/finalize (before result material) and shutdown, not high-rate motion tick. Normal stop/shutdown requests final flush; crash recovery resumes only durable totals, never active clock/downtime. Independent counter errors block history delete; corrupt file never reset from remaining history. Windows16new tests pass, related execution7+continuation10+runtime9pass. Emergency18 passed in WSL after Windows lacked termios; this is platform dependency, no source test rewrite.

APP lifetime/today time comes only from optional authoritative summary; null old-board metadata -> unknown with update notice, unavailable/invalid -> unknown with read-error notice. JSON strict validation includes schema, nonnegative totals, today<=total, calendar day and incomplete count. Existing area semantics retained. Removed previous APP-only legacy live extrapolation because board now owns clock. No protocol-generated files changed.

One initial read used board cwd for Android .planning files and failed; correct scope is Android repo for task plans, board repo for scheduler source. No production error.
