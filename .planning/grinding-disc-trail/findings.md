# Findings
- Live trajectory uses base_link points, 4px lines and 5px sample circles. Replay uses robot width as trail width.
- Shared robot marker currently draws footprint and heading only; no existing disc geometry found.
- Use max footprint X minus radius (0.45m), mid footprint Y; fallback to configured centered length/width.
- Compute offset in world coordinates before common map projection and zoom, avoiding map heading errors.
