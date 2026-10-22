# Progress
- Inspected report fields, AppState, status collector, task map snapshot and existing watchdog reason mappings.
- Locating authoritative map naming flow and adding display-level mapping.
- Preserved optional readiness and reason in AppState from existing status report fields; did not alter protocol thresholds or generated code.
- Combined current task snapshot and map catalog to resolve names without ordering/numeric-ID assumptions; request catalog on home visibility if unavailable.
- Applied green healthy, red unconverged, gray unavailable through existing SidebarText rendering.
- Added five display tests and connection epoch guarding; diff checks passed, no Gradle/build/test invocation.
