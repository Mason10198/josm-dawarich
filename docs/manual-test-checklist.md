# Manual test checklist

Run against an isolated JOSM profile and a disposable Dawarich test instance populated only with synthetic coordinates/keys. Never use a production profile, household account, real GPS trace, or key for screenshots/bug reports. Record JOSM version, OS, plugin commit, test result, and any failure without recording private hostnames, keys, or coordinates.

## Automated packaged GUI smoke

After building the JAR and installing Xvfb, run:

```sh
./scripts/bootstrap-build.sh clean test dist
python3 scripts/gui-smoke.py
```

The helper starts JOSM with a temporary isolated profile and a local synthetic HTTP server. It verifies packaged-plugin loading, no startup requests, direct key entry, the Imagery-menu command, separate authorization for two sources, two synthetic ten-point layers in date mode, and then newest-N mode with exact original newest IDs, historical timestamps older than the ignored date fields, actual coverage/count status, and fewer available on one source. A paused partial HTTP response verifies changing received-byte/elapsed progress before completion, an EDT ping under 500 ms, no extra requests, no partial layer replacement, and timer cleanup. A stalled request verifies Close cancellation, password-editor wiping, retained layers, and idle reopening without a download. It also verifies Load re-enabling, unchanged empty OSM editing data, and shutdown cleanup. It writes local receipts under `build/gui-smoke-*/` (`josm.log`, `synthetic-load.png`, `synthetic-recent.png`, and `synthetic-progress.png`). Inspect them locally and do not upload screenshots/logs without confirming they contain no private information. This synthetic smoke does not prove compatibility with a real Dawarich server, real credentials, or another operating system.

## GUI/manual acceptance

- [ ] Install the built JAR into a clean, disposable JOSM profile using JOSM's documented local plugin installation method; restart and verify the plugin loads without an error dialog.
- [ ] Verify **Imagery → Check alignment with Dawarich GPS…** and **GPS → Load Dawarich reference…** open the same guided configuration panel and the map view is captured at Load time.
- [ ] Check the visible password field follows the selected person/device, use a different key per source, and confirm selecting another source or changing server clears transient text rather than assigning it to the wrong source. Invalid endpoint/key/date/count feedback must not echo a secret.
- [ ] Verify fresh profiles recommend recent mode, existing saved choices persist, and budget/HTTP controls are discoverable under Advanced settings. At smaller supported window sizes, setup scrolls while Load/Stop/Close stay accessible and results wrap/scroll without losing content.
- [ ] Verify HTTPS works; plain HTTP is rejected unless its explicit acknowledgement is enabled. Verify changing the server invalidates keys/sessions, and that no URL user-info/query/fragment is accepted.
- [ ] Add two synthetic sources with different keys. Confirm keys appear masked, labels/colors are independently configurable, and each request carries only the selected source's key.
- [ ] Confirm requests are GET-only, follow no redirects, include the intended path prefix and query contract, and cannot mutate OSM data. Use a local mock or disposable test account; do not paste captured requests/responses publicly.
- [ ] Verify date format validation, local-zone date boundaries, a DST transition date, future/reversed date rejection, and correct inclusive end handling. Confirm displayed time zone before the request.
- [ ] Verify antimeridian or overly broad bounds are rejected; a view over 100 miles prompts and declining starts no request.
- [ ] Verify a normal synthetic response creates separate colored read-only layers; pan/zoom and confirm points reproject; hide/show, rename/recolor, disable, and remove a source.
- [ ] Verify refresh replaces only the successful source's layer; a failed refresh retains that source's old layer; changing server cancels work and clears owned layers.
- [ ] Verify retained hidden layers still count toward aggregate capacity; per-source quota is fair and unused quota is not redistributed.
- [ ] With controlled mock responses, exercise auth failure, redirect, 429, 5xx, malformed JSON/headers, oversize response, duplicate/out-of-bounds points, dense single-second window, cancellation, deadline, and request/point limits. Confirm errors do not expose keys, raw response bodies, or full credential-bearing URLs.
- [ ] Confirm `mask_privacy_zones=false` is sent as designed and separately confirm the server's mandatory restrictions remain effective; stop if server policy does not permit this private reference use.
- [ ] Confirm layers are not savable/exportable as GPX or session-persisted custom layers; keys require re-entry after restart, while validated non-secret common source settings persist in JOSM preferences.
- [ ] Use Stop download, Close, and the window close control during and after a load; confirm active load cancellation, stopped progress sampling, no second active load while cancellation drains, and transient password-editor cleanup while session keys and layers remain. Remove a source or change server/credentials and confirm applicable session state clears; shut down JOSM and confirm all session state clears. Verify the OSM editing dataset is unchanged.
- [ ] Check JOSM tile/network behavior separately: this plugin cannot prevent unrelated map tile services from receiving viewport requests.

## Platforms and release evidence

- [ ] Record actual OS/JOSM version and the commit/JAR hash tested.
- [ ] Java 17/JOSM 19678 is the baseline. CI's Java 25/JOSM 19689 job is a compatibility test, not a substitute for manual GUI testing on target platforms.
- [ ] Windows and macOS are not currently documented/verified build platforms. Do not mark them passed unless tested on those systems.
- [ ] Before publication, review [Publishing](publishing.md), the exact commit/tag, CI runs, artifact contents/hash, and the public tree/history for private data.
