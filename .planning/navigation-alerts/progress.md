# Progress
- Inspected history, bell component, runtime/task reports, blue-white dialog and success footer.
- Added unique monotonic event IDs with legacy migration and a persisted read cursor. Read-through only covers rendered history; later arrivals stay unread until displayed.
- Replaced baked red-dot PNG usage with a plain vector bell and conditional dot. Low-battery warnings join event history only on tier changes, avoiding repeated red dots.
- Preserved localization pause latch from existing protocol; pure tracker prompts once per active cause episode/task, ignores idle/manual pause/old connection data.
- Blue-white single-acknowledgment dialog does not send task control; new protection alerts close the runtime editor to avoid stacking.
- Appended requested success sentence, added unread/collision/localization episode tests, checked vector XML and diffs. No build or tests run.
