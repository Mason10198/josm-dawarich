# Acceptance status

## Verified locally

- Java 17 / JOSM 19678: clean build and 87 tests pass.
- Packaged `dawarich.jar` loaded through the real JOSM plugin loader from an isolated profile outside the source directory.
- Synthetic GUI acceptance: no startup API request; two independently authenticated sources load into separate layers; Load re-enables; editable OSM data is unchanged; shutdown releases reference layers.
- Actual MapView pan/zoom painting at 50,000 synthetic points across one, two, and four layers was exercised under Linux/Xvfb. See `mapview-performance.md` for cold and warmed measurements and limitations.
- Private, read-only server acceptance: two authorized accounts were queried for the same bounded area and date interval through the production Java client/fetcher/coordinator path. Every returned ID, coordinate, and timestamp matched independent owner-authenticated API reads exactly. Private credentials, service addresses, points, identifiers, and activity details are not included in this repository or CI.
- Failure, cancellation, source lifecycle, retained-layer capacity, HTTP redirect refusal, response limits, and multi-key isolation have automated regression coverage.

## Not claimed

- Windows or macOS manual desktop verification.
- A transactional snapshot of a server that changes while requests are running.
- Accuracy filtering, connected routes, inferred centerlines, road snapping, or public GPS-trace upload.
- Official JOSM-directory listing or a published stable release before those external steps are independently read back.

This file records local acceptance rather than claiming all future CI runs, third-party desktops, or publication gates have passed.
