# Alignment tools: implemented versus proposed

## Implemented in v0.3.0

A guided Imagery-menu workflow loads original GPS observations into separate, read-only, source-colored layers. Live progress samples existing counters every 200 ms, including elapsed time while the server has not yet returned a complete page. It makes no additional HTTP requests. Recent/count and complete date-range modes retain the existing bounds, quotas, credentials, and replacement semantics.

Use repeated observations as visual evidence alongside other suitable reference information. The plugin never decides a correct offset, moves imagery, snaps observations to roads, edits OSM objects, or uploads data. More observations or a brighter density cluster must not be labeled as guaranteed positional accuracy.

## Proposed next: reversible density view

This is a design direction, not an available toggle.

- Offer Points / Density / Both, keeping the original data intact and individual sources independently controllable.
- Explain the legend as relative observation density, not confidence, an error estimate, or a proposed correct alignment.
- Do not average a whole trip/viewport into a centroid or connect unrelated observations into invented lines.
- Evaluate how to prevent long stationary logs and repeated correlated samples from overwhelming separate travel passes; explicitly identify any time/pass normalization.
- Build bounded grids or tiles off the UI thread, cache by dataset/projection/zoom/style, and reuse prepared images during paints. Do not recompute a full density estimate for every repaint.
- Acceptance: synthetic parallel corridors and stationary clusters remain distinguishable, no source mixing, toggling restores unchanged original observations, stale workers cannot publish, and measured paint/compute/memory cost fits an explicit budget.

## Proposed next: reported-accuracy filter

No accuracy field is currently requested or interpreted by this plugin.

- Verify the actual server payload and importer-specific field semantics first. Do not treat dilution-of-precision values as feet/meters, or missing/zero/invalid values as perfect accuracy.
- Show a user-chosen threshold in feet and an explicit policy for unknown accuracy; report kept/excluded/unknown counts.
- Keep filtering reversible and distinguish original fetched counts from visible filtered counts. Never delete source observations.
- Preserve the current compact-response path when filtering is off. If an expanded payload is required, measure extra transfer size and parsing cost before promising performance.
- Acceptance: known/unknown/zero/invalid fixtures, source separation, visible exclusion accounting, unchanged original data, and transport/parse benchmarks with filtering on and off.

## Proposed next: optional load after panning

Current loads remain explicit and frozen to the map area captured when Load is pressed.

- Initially default off. Wait for movement to stop, coalesce obsolete requests, and allow only one bounded batch at a time.
- Retain old valid layers until a complete replacement is ready. Show partial loading and completed coverage as different states.
- Use only bounded session-local caches, keyed by server, source, key generation, time bounds, options, and filters; invalidate on all identity/scope changes.
- Reuse only coverage actually known to be complete. A newest-N subset for an old viewport does not establish complete coverage of its overlap with a new viewport.
- Do not automatically request data while the user is editing credentials, continuously dragging, or merely reopening the dialog.
- Acceptance: rapid pan/cancel/reopen tests, exact independent viewport/newest-count oracle, bounded request and memory counts, no obsolete publication, no credential crossover, and a measured benefit over explicit loading.

These features require separate implementation and verification. They are not silently enabled by the guided-UI release.
