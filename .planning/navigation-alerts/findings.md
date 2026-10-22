# Findings
- BellDotIcon is a PNG with a permanently visible red dot. Notification history has no read marker.
- DeviceStatusReport already provides localization_pause_latched and collision_imminent. Latch is used by event tracker but not preserved in RuntimeTaskState.
- RPP collision imminent means obstacle stop, not necessarily scheduler PAUSED; dialog wording must not claim a command was sent by APP.
- Runtime task editor provides existing blue-white rounded AlertDialog visual baseline.
- RelocalizationStatusFooter contains the existing success text.
