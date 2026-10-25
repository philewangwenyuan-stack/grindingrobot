# Path rendering and receive performance

User asks to verify reported APP hotspots and optimize confirmed ones; no deployment or hardware operations.

1. Verify grouping, projection, layer and receive lifecycle — complete.
2. Cache immutable path grouping/projection and batch static strokes; preserve breaks/colors/viewport geometry — complete, unit/oracle/native pixel checks pass.
3. Bound background full JSON parsing, preserve response ordering/session isolation and responsive receive loop — complete, TCP 94 tests pass.
4. Regression/performance checks, affected tests and production build — complete locally. TCP94, cache/viewport8 and nativeUI8 pass; production/test APK build passed. Physical tablet performance not measured; no robot deployment/operation.

Avoid claims about field latency without measurement. Preserve existing user changes.

Known baseline: TaskPathGeometryTest has four obsolete red-color assertions against unchanged green rendering. Keep behavior and report these separately.

5. User asks for quantified benefits and data — complete. Opt-in paired JVM grouping/geometry and JSON handoff plus Android software offscreen benchmarks passed. Frozen baseline commit56aeb085ba85cfbc973bcbb3c6f0d85b0befb1fe; warmups5, samples21, alternating order. Exported1092 raw rows/26 summaries, Chinese report/CSV, raw Excel, environment/hashes and reproducible source ZIP. Artifact QA:11 data hashes and7 source hashes, ZIP CRC, summary arithmetic and zero Excel formula errors verified. Preserve negative outcomes; no physical tablet FPS/network/planner speed claim.
