# Persistent device work time

User approved 2026-10-26: lifetime time must survive history deletion. Keep elapsed-task semantics (including pause), do not change area counters or operate physical robot.

1. Design independent board ledger, migration and backward-compatible JSON summary — complete.
2. Implement board lifecycle/background writer integration and offline persistence tests — complete (15 tests pass initially).
3. Parse authoritative counters in APP, keep history only for area, explicit old-board/error handling — complete; TCP98/device55 tests pass.
4. Run board/TCP/device regressions, build APK, package board update and document migration/limits — complete; board60 unique regressions pass, counter16 also passes WSL Linux. ARM64/ARM32 APK built. Freshly fetched deployed source was patched in an isolated local copy and16counter/integration tests passed against that copy. Minimal field patch ZIP built and CRC verified, with exact source/hash manifest and deployment/migration instructions.

Decisions: independent total + per-day milliseconds, elapsed task including pause; atomic fsync+replace, checkpoint5seconds plus start/finish flush wakeups. Startup imports valid retained closed history once and keeps an incomplete baseline marker. New process does not resume any active clock, marks possible unflushed tail. Corrupt counters are never reset/reseeded; deletion denied while counter unavailable. No protobuf layout change (optional JSON object in existing history chunks).

Errors: apply_patch cannot delete+add same path in one patch; failed before mutation and corrected with separate sequential delete/add. No build was launched against missing test file.

Windows emergency suite lacks termios; used WSL Linux where all18 passed. No test was weakened or rewritten for Windows.
Deployment boundary: only source was read remotely. Physical device is not updated; both new board patch and new APP are required to enable independent counters. Existing production records remain unchanged; no migration executed against field runtime state.

No remote deployment/restart authorized this turn. Existing prior APP timing corrections must be preserved unless superseded deliberately. Migration imports valid ended records exactly once; orphan durations and already-deleted history cannot be recovered or fabricated. Handle reboot without counting downtime, daily midnight split, disk corruption/error without silently resetting/reseeding.
