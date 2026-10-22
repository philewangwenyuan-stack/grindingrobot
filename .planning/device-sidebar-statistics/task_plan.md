# Device sidebar real statistics

## Objective
Implement approved blue/white running and idle sidebars, always visible, with real history-backed daily area, daily elapsed work duration and lifetime retained-history area. Do not compile per user preference.

## Phases
1. [complete] Inspect task history, live reports and sidebar layout.
2. [complete] Add history summary and coordinated refresh; activity depends on history availability.
3. [complete] Bind sidebar metrics and preserve idle/running behavior.
4. [complete] Added aggregation/state tests, reviewed changes and passed diff checks. Tests/build deliberately not run.

## Constraints
- Preserve unrelated user modifications in protocol/generated Java/TCP tracker/README.
- Protocol has execution start/finish timestamps, not pause-excluded working seconds or an immutable lifetime counter.
- Use retained execution history for totals; disclose duration includes pauses.
- No build, compilation or device actions.
