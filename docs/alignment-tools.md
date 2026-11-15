# Alignment tools: implemented versus proposed

## Implemented workflow

A single **GPS → Dawarich GPS…** workflow loads original GPS observations into separate, read-only, source-colored layers. Live progress samples existing counters every 200 ms, including elapsed time while the server has not yet returned a complete page. It makes no additional HTTP requests. Recent/count and complete date-range modes retain the existing bounds, quotas, credentials, and replacement semantics.

Use repeated observations as visual evidence alongside other suitable reference information. The plugin never decides a correct offset, moves imagery, snaps observations to roads, edits OSM objects, or uploads data. More observations or a brighter density cluster must not be labeled as guaranteed positional accuracy.

## Implemented in v0.5.0: reversible heatmap

Choose **Points / Heatmap / Both** above the setup form. Display changes reuse existing local layers and issue no HTTP requests. Points stays the default; the display choice persists independently of credentials or download scope.

- Each source is processed separately in its layer color. Log-scaled alpha shows density relative to that source's current-view maximum. Intensities are not directly comparable across sources/views.
- The visible legend states density is not accuracy. No stationary/pass normalization is applied: a long stop or faster logging can dominate. This is an explicit first-version limitation, not independent-pass confidence.
- All coordinates/IDs/timestamps remain intact. The only smoothing is a derived raster blur of approximately 12 screen pixels with cells at least 4 pixels across; original observations are neither averaged nor connected into tracks.
- Projection, binning, blur, and colorization run on a lazy source-local daemon worker using captured immutable view state. A layer coalesces pending views, cancels obsolete work, and rejects stale publication.
- Both the grid and output image are bounded to 262,144 cells/pixels. Same-view/dataset/style paints reuse the image; Points, replacement, recolor, and destruction clear derived state. A new view shows Preparing heatmap rather than painting a misaligned old image.
- Both overlays the original dots above density, retaining point-rendering cost. Layers remain independently hideable/recolorable with host opacity controls. No disk cache or server changes are introduced.
- Tests cover cluster separation, source color, offscreen/empty data, unchanged original inputs, cache reuse, pan/zoom/resize, recoloring, stale replacement/destroy, cancellation, and actual MapView performance. See performance.md for measured limits.

## Proposed next: normalize stationary / repeated observations

Investigate reversible time/pass weighting so long stationary logs do not drown out separate travel passes. Any implementation must state its weighting and must not label density as guaranteed accuracy or silently discard original observations.

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
