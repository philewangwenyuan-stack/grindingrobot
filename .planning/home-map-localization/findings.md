# Findings
- DeviceStatusReport already includes optional localization_ready, localization_pause_latched, localization_status_reason; existing user changes use them for event tracking only.
- Homepage currently uses legacy radar_system_status from a different /slamware endpoint.
- Board evaluates configured Super-LIO bad-frame/fitness/inlier/correspondence/covariance/jump/TF watchdog. APP must display its result, not replicate thresholds.
- Existing TaskMapDisplaySnapshot stores string mapId; MapImagePayload.mapId is numeric and must not be used to look up a catalog name.
- Runtime sample receipt time and connection epoch prevent a previous connection's healthy report from immediately appearing after reconnect.
- Missing TF/odom/diagnostics/IMU/LiDAR/heartbeat reasons map to an em dash; confirmed registration/covariance/pose-jump failures map to unconverged.
- Old firmware has no composite watchdog; valid finite reported pose is used as compatibility evidence only when readiness is absent.
