# Home daily vs lifetime work time

2026-10-26 user reports total and today work time always equal. Verify actual source before changing semantics; retain unrelated user/performance changes.

1. Trace UI, calculator, response and persistence — complete; field registry and live query verified.
2. Reproduce confirmed failure and implement minimal correct fix — complete for APP orphan-time accrual; no artificial inequality.
3. Test daily/lifetime, midnight, restart and active/finished boundaries; build affected app — complete: 56 device tests pass, including 13 statistics tests; devDebug APKs built and rechecked after final source edit.

Boundary: the user's specific equality observation lacks its date/screen values and cannot be reproduced from the current board response. No physical tablet is attached to ADB (only an existing emulator). Do not claim that observation is proven fixed. Confirmed idle orphan-time accrual is reproduced and corrected locally, with missing history explicitly marked incomplete.

Do not force distinct numbers when all retained history legitimately belongs to today. Ask whether earlier records exist while independently checking source/test edge cases. No deployment or robot operation.
