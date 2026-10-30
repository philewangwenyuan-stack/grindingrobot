# Findings

- Initial worktree clean; previous task changes already committed externally.
- Homepage navigation DISC_RPM opens TaskRuntimeSettingsDialog, which currently uses a numeric input and plus/minus. Keep temporary task settings and explicit Apply semantics, and disabled before task starts.
- Shared DriveParameterSlider is the remote page slider; needs enabled flag for loading/applying.
- Relocalization buttons edit local initial pose, not chassis commands. Current lambda captures pose; repeat handler must use rememberUpdatedState to avoid stale pose during recomposition.
- Read map coordinate/rotation documents; no coordinate transform changes needed.
