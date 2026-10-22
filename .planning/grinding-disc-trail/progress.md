# Progress
- Inspected live/replay drawing and footprint marker. Confirmed heading in world coordinates, projection shared with map path.
- Narrowed searches after an invalid wildcard path and missing guessed filenames; located definitions with rg.
- Implemented shared footprint/length geometry and optional disc overlay. Live and replay trails now follow world-space disc centers and physical 0.9m diameter.
- Robot marker remains anchored to base_link; geometry responds to footprint/length state changes. Kept existing live pass colors and replay color.
- Corrected optional overlay parameter initially matched to wrong nearby signature before verification.
- Added tangency/dimension/heading/fallback tests. Diff checks passed; no builds/tests executed by user preference.
