# Full home execution geometry

2026-10-26 user asks to replace home dotted/capped actual execution trace with full visible geometry using fewer resources. Prior statistics/path work must be preserved; no robot operations/deployment.

1. Verify actual vs planned trace, sample cap, lifecycle and geometry transforms — complete.
2. Implement full-session compact geometry and incremental/cached drawing; separate latest robot pose — complete.
3. Verify correctness (turns/colors/gaps/rotation/zoom/session reset), long input and memory/CPU comparisons — complete:64normal unit tests pass (14trajectory states),4native render tests pass,1host opt-in benchmark and1native benchmark pass;504rawsamples/24groups exported.
4. Build APP, supply artifacts/data with measured limits — complete: APK/test package built; Chinese report/raw CSV/summary/images/source hashes+ZIP generated. Isolated owned MuMu1 timestamp1792949722801335 shut down and deleted after exports; final inventory only original0 with unchanged identity/size. No physical tablet install or robot operation.

Existing actual trace: List<TaskPoseUiItem> appended by copy and takeLast(500), UI projects whole list and draws line+circle for each sample. Planned path already caches native Path from previous task. Need preserve full spatial trace while controlling storage; no misleading claim that retaining arbitrary complete raw samples always costs less than capped 500 points. Resource comparisons must distinguish old same-coverage and old500-cap baseline.
