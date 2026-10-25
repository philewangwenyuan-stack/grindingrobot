# Path rendering and receive performance

User asks to verify reported APP hotspots and optimize confirmed ones; no deployment or hardware operations.

1. Verify grouping, projection, layer and receive lifecycle — complete.
2. Cache immutable path grouping/projection and batch static strokes; preserve breaks/colors/viewport geometry — complete, unit/oracle/native pixel checks pass.
3. Bound background full JSON parsing, preserve response ordering/session isolation and responsive receive loop — complete, TCP 94 tests pass.
4. Regression/performance checks, affected tests and production build — complete locally. TCP94, cache/viewport8 and nativeUI8 pass; production/test APK build passed. Physical tablet performance not measured; no robot deployment/operation.

Avoid claims about field latency without measurement. Preserve existing user changes.

Known baseline: TaskPathGeometryTest has four obsolete red-color assertions against unchanged green rendering. Keep behavior and report these separately.

5. User asks for quantified benefits and data — in progress. Build opt-in paired JVM grouping/geometry and JSON handoff benchmarks plus Android offscreen rendering benchmark. Preserve old baseline from commit56aeb085ba85cfbc973bcbb3c6f0d85b0befb1fe. Same synthetic paths, warmups5, samples21, alternating old/new order, report medians/P95 and raw records. Separate first-build from cached draw and receive blocking from result-ready latency; do not claim physical tablet FPS/network/planner speed.
