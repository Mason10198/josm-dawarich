# JOSM Dawarich reference layer

A community-built JOSM plugin for checking aerial imagery alignment against your own original GPS observations, shown as temporary, read-only reference layers. Public source: [Mason10198/josm-dawarich](https://github.com/Mason10198/josm-dawarich). This is not an official Dawarich product. Published releases are listed in the official JOSM plugin directory under **dawarich**. Do not send private instance details, API keys, or GPS data to the project maintainers.

## What it does

1. Open aerial imagery in JOSM and zoom to the area you want to check.
2. Open **Imagery → Check alignment with Dawarich GPS…**. The existing **GPS → Load Dawarich reference…** command also works.
3. Enter your Dawarich server address. Select a person/device and paste its API key into the visible password field. Click **Load GPS for this area**, or use **Use this key** before selecting another source. Each enabled source needs its own key; disable sources you do not want to load.
4. Compare repeated observations against the imagery. Use JOSM's imagery-offset tool for a deliberate adjustment; the plugin never moves imagery, GPS, or editable OSM data automatically. GPS density is not proof of a correct absolute position.

New profiles start with **Most recent points** recommended. Existing saved selections are preserved. Choose a loading mode:

- **Date range:** load all matching observations in the current view for inclusive local dates (`YYYY-MM-DD`), subject to the displayed point budget and network safeguards. The displayed time zone controls day boundaries, including daylight-saving transitions.
- **Most recent points:** request up to **N observations per enabled source within the current view**, ordered by when each GPS fix was recorded, not when it was imported. Dates are disabled and ignored in this mode; older history remains eligible. The status shows the actual recorded range and whether N was reached or fewer points were available. At an equal-timestamp cutoff, any subset of original observations at that timestamp may be chosen.

The view, mode, count, budget, dates/time zone, and current time are captured when Load is pressed. Panning or changing controls does not change an in-flight query. Views over 100 miles require confirmation. Points are not connected into inferred tracks.

**Advanced settings → Total point budget** is adjustable from 1 to 250,000, default 50,000, across all connector layers. Remaining capacity after retained, unselected layers (including hidden/disabled ones) is divided fairly among enabled sources. The recent count must fit each selected source's share; otherwise the request fails before fetching, with instructions to lower the count or raise the budget. A date-range result is never silently truncated. Raising the point budget does not increase the independent 128-request, 60-second, 8 MiB/response, or 64 MiB/download safeguards. Large loads may be slower and may still hit those other safeguards.

While downloading, the dialog shows the active person/device, actual received bytes, request count, elapsed time, and selected points. Recent mode also shows the requested count; there is no invented overall percentage or ETA. Updates sample existing counters every 200 ms, without extra requests. **Stop download** cancels while leaving the dialog open; **Close** also cancels and hides it. Old valid layers stay visible until a complete replacement is ready.

Each source becomes its own colored, hideable JOSM layer. Validated common settings (base URL, local date range, mode, recent count, total point budget, and non-secret source UUID, label, enabled state, and color) are saved in JOSM preferences. API keys are memory-only and must be re-entered after restart. Loaded layers are transient and are not persisted. Old layers are replaced only on a successful refresh of their own source; successive loads do not accumulate dates. Failed refreshes retain the old layer and report it as stale. Disabling a source hides its layer. Removing a source removes its layer.

Layers are intentionally not savable/exportable as GPX or JOSM session layers. They do not modify OSM data, and the plugin does not upload, edit, or publish GPS points. This is a private visual reference, not an anonymization tool: precise locations remain visible in JOSM. Ordinary map-tile requests may disclose the viewed viewport to tile providers; they do not directly send the plugin's GPX or precise point contents. Screen capture, operating-system logging, or other software on your computer may expose displayed data.

## Safety and privacy

- Use HTTPS. HTTP is rejected unless you explicitly enable the unencrypted-HTTP acknowledgement under **Advanced settings**; only use it on a trusted network/endpoint. Prefer a private VPN or a trusted HTTPS reverse proxy for remote access.
- Use a dedicated, least-privileged API key per source. The plugin sends authenticated **GET** requests only, but the key may itself have broader server-side privileges. The plugin does not manage or narrow those privileges.
- `mask_privacy_zones=false` is intentional: this tool requests the unmasked private reference data. Server-side access controls and any mandatory restrictions still apply. Do not weaken Dawarich server restrictions to make this plugin work.
- No accuracy fields are requested or interpreted. There is no accuracy filter, inferred-track-line construction, smoothing, snapping, or averaging.
- Responses and keys are held in memory for the session/request. Closing or cancelling the dialog cancels active loading and clears transient password-editor contents, but does not remove session keys or loaded layers. They remain available until their source is removed, credentials/server change (which clears affected session state), or JOSM shuts down (which clears all session state).

See [Security](SECURITY.md) and [the API contract](docs/api-contract.md) before connecting a real instance.

## Build and test

Requirements: Linux x86-64, `curl`, `tar`, `sha256sum`, and `sha512sum`. The script downloads pinned Microsoft JDK 17, Apache Ant, JOSM 19678 and JUnit artifacts into `$HOME/.hermes/cache/toolchains`, verifies published/recorded hashes, then runs the requested Ant targets. Java 17 is the supported build/test runtime; JOSM supplies the host APIs at runtime and no additional plugin runtime libraries are bundled.

```sh
./scripts/bootstrap-build.sh clean test dist
```

The test selector is a Java regular expression matching test class names (not methods):

```sh
./scripts/bootstrap-build.sh -Dtest.includes='ConnectionStateTest' clean test
```

The JAR is written to `dist/dawarich.jar`. To exercise the packaged JAR in an isolated synthetic GUI smoke (requires Xvfb), run `python3 scripts/gui-smoke.py`; receipts stay under `build/gui-smoke-*/`. It exercises date and recent modes, direct key entry, the Imagery menu, independent authentication, original newest IDs, and coverage from history older than the displayed date range. It also pauses a partial HTTP response to verify visible progress before completion, EDT responsiveness, no premature layer replacement, no extra progress requests, and close/cancel/reopen cleanup. Add `--renderer --points 100000` or `--renderer --points 250000` for actual MapView paint checks (50,000 is the renderer default). This is not a live-service check. This build bootstrap is currently Linux x86-64-specific; Windows and macOS setup has not been documented or verified. GitHub CI checks both Java 17/JOSM 19678 and Java 25/JOSM 19689; check the exact commit's run rather than assuming an earlier pass covers later changes.

## Installation

In **Edit → Preferences → Plugins**, refresh the plugin list, search **dawarich**, check its box, and click the **main Preferences OK** button. Accept the download and fully restart JOSM. Updating uses the same panel's **Update plugins** button. The Imagery and GPS menu entries appear after the plugin has loaded.

### Local manual installation

For a local build, follow JOSM's official [Preferences → Plugins → Manual installation instructions](https://josm.openstreetmap.de/wiki/Help/Preferences/Plugins): put `dawarich.jar` in the `plugins` directory inside the active JOSM user-data directory, restart, then select/enable the manually placed plugin in the plugin list. Determine the active user-data directory from JOSM itself (for example, its status/About information); do not assume a platform-specific path. JOSM's UI can vary by version. See the [manual test checklist](docs/manual-test-checklist.md).

## Project status

The source and packaged artifact are tested on Linux with isolated JOSM profiles. v0.1.0/v0.1.1 have passed protected compatibility CI, guarded release builds, downloaded-artifact GUI checks, official catalog inclusion, and native fresh-profile installation. A separate two-authorized-user read-only pilot matched every returned ID, coordinate, and timestamp against independent authenticated reads. These earlier checks do not automatically certify later commits: before each public release, verify the exact commit, compatibility CI, packaging, runtime behavior, and publication as described in [Publishing](docs/publishing.md). Windows/macOS manual desktop compatibility remains unverified.

## License and contributions

GPL-3.0-or-later; see [LICENSE](LICENSE). This is an independent community project, not affiliated with or endorsed by Dawarich or the JOSM project. Contributions and private vulnerability reports are welcome; see [CONTRIBUTING](CONTRIBUTING.md) and [SECURITY](SECURITY.md).
