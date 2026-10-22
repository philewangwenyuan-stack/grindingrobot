# Linear teleop implementation

## Accepted requirements
- Remote has no map; mapping retains its map and workflow.
- Forward/backward buttons latch on click. Opposite direction first stops, next click reverses.
- Signed horizontal steering u in [-1,1] continuously attenuates translation by 1-abs(u), including 1% and 20%; no fixed cutoff or 8% dead zone.
- Full steering is pure rotation, release restores latched travel; explicit stop clears everything and invalidates old touches.
- Parameter sliders with fine +/- adjustment; default turn gain 70%.
- Clear on disconnect, background, screen exit, emergency stop and failed/cancelled takeover; never reconnect into old motion.
- Preserve board disc linkage; no board deployment or hardware operation.

## Phases
1. Explore and choose native UI screenshot harness — complete (isolated feature-map instrumentation on x86_64 emulator).
2. Shared controller, proportional mixer and protocol encoding — complete.
3. Native Compose remote/map UI and lifecycle wiring — complete, visual verification passed.
4. Regression tests, build, actual Compose screenshots — complete. Production APK build passed; 148 common/TCP/device unit tests and 3 native UI tests passed. Broader baseline test limitations recorded below.
5. Follow-up: scanning map pinch zoom/pan — complete. Shared preview viewport (1–8x), synchronized marker transform, live-frame view persistence and reset verified. Production/test APK build, 9 relevant unit tests and 4 native UI tests passed.
6. Follow-up: taller scanning map — complete. Travel/steering pads flank the map; compact status/stop shares the bottom row with cancel/next. Map height grew from about 350px to 500px in the same 1600x966 capture. Production/test build, 18 common unit tests and 4 native UI tests passed; actual captures inspected.
7. Follow-up: global header alignment — complete. Shared Home/scanning header preserves title-left, emergency-center and status-right positions with identical screen insets and reserved close slot. Production/test build, 18 common unit tests and 4 native UI tests passed; refreshed map captures inspected.
8. Follow-up: shorter mapping footer — complete. Direction/reduction/speed share one line; stop target 48dp with 18sp text. Production/test APK build and 4 native UI tests passed; captures inspected, header unchanged and map gained ~43px at 1600x966.
9. Follow-up: move settings into side panels — complete. Speed slider above left travel buttons, turn-gain above right joystick; compact tracks and separate +/- rows fit. Top settings row removed; map height grew from ~547px to ~697px (+27%) at 1600x966. Production/test build and 4 native UI tests passed; map/zoom/remote captures inspected.

## Errors
- First compile: Compose DSL scoped maxHeight reference in inner Box. Fixed with outer captured label inset.
- Baseline main build error: navigationPauseNotice argument belonged to state copy, not trajectory helper. Minimal correction made.
- Baseline instrumentation compilation error: two TaskPreviewPanel test callers omitted required taskName/onTaskNameChange. Added fixture arguments.
- Removed SystemClock import was still used by map snapshot timeout; restored.
- Existing main unit-test suite does not compile: MainViewModelGnssDataEquivalenceTest uses obsolete GnssData fields. Do not expand this teleop task into GNSS rewrite; run actual affected shared/map/TCP/device suites and compile full app separately.
- Map suite: 130/134 pass; 4 unchanged TaskPathGeometryTest color assertions expect red while existing renderer returns green. Keep out of teleop scope.
- Windows classes.jar lock: Restart Manager identified PID 63180, Gradle --status verified IDLE. Stopped only that idle daemon; rebuilding serially in-process.
- Instrumentation: held-pointer test incorrectly reused pointer 0 via performClick; changed stop invocation to semantic click while pointer remains held. Force LANDSCAPE on test activity; initial screenshot was portrait. Changed +/- icons to untinted vector and slider thumb to 28dp circle after visual inspection.
