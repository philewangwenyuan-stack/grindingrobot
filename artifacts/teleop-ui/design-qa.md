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
