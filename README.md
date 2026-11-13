# JOSM Dawarich reference layer

A community-built JOSM plugin for viewing selected Dawarich GPS fixes as temporary, read-only reference layers. Public source: [Mason10198/josm-dawarich](https://github.com/Mason10198/josm-dawarich). This is not an official Dawarich product. Check [Releases](https://github.com/Mason10198/josm-dawarich/releases) for published artifacts; official JOSM-directory registration is a separate step. Do not send private instance details, API keys, or GPS data to the project maintainers.

## What it does

Open **GPS → Load Dawarich reference…**, configure a server and one or more labeled sources, enter each source's API key, choose an inclusive date range, then load the current map view. The view bounds and date/time-zone interpretation are captured when you press Load; panning or zooming afterward does not change an in-flight query. Large views over 100 miles require confirmation. Date fields use `YYYY-MM-DD`; the displayed system time zone determines day boundaries, including daylight-saving transitions. If exact dates matter, confirm both the date and time zone before loading.

Each source becomes its own colored, hideable JOSM layer. Validated common source settings (base URL, local date range, and non-secret source UUID, label, enabled state, and color) are saved in JOSM preferences. API keys are memory-only and must be re-entered after restart. Loaded layers are transient and are not persisted. Old layers are replaced only on a successful refresh of their own source; failed refreshes retain the old layer and report it as stale. Disabling a source hides its layer. Removing a source removes its layer.

Layers are intentionally not savable/exportable as GPX or JOSM session layers. They do not modify OSM data, and the plugin does not upload, edit, or publish GPS points. This is a private visual reference, not an anonymization tool: precise locations remain visible in JOSM. Ordinary map-tile requests may disclose the viewed viewport to tile providers; they do not directly send the plugin's GPX or precise point contents. Screen capture, operating-system logging, or other software on your computer may expose displayed data.

## Safety and privacy

- Use HTTPS. HTTP is rejected unless you explicitly enable the unencrypted-HTTP acknowledgement; only use it on a trusted network/endpoint. Prefer a private VPN or a trusted HTTPS reverse proxy for remote access.
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

The JAR is written to `dist/dawarich.jar`. To exercise the packaged JAR in an isolated synthetic GUI smoke (requires Xvfb), run `python3 scripts/gui-smoke.py`; receipts stay under `build/gui-smoke-*/`. Add `--renderer` for the opt-in actual MapView paint check. This is not a live-service check. This build bootstrap is currently Linux x86-64-specific; Windows and macOS setup has not been documented or verified. GitHub CI checks both Java 17/JOSM 19678 and Java 25/JOSM 19689; check the exact commit's run rather than assuming an earlier pass covers later changes.

## Local manual installation

Build the JAR, then follow JOSM's official [Preferences → Plugins → Manual installation instructions](https://josm.openstreetmap.de/wiki/Help/Preferences/Plugins): put `dawarich.jar` in the `plugins` directory inside the active JOSM user-data directory, restart, then select/enable the manually placed plugin in the plugin list. Determine the active user-data directory from JOSM itself (for example, its status/About information); do not assume a platform-specific path. JOSM's UI can vary by version. This is not a published registry installation. See the [manual test checklist](docs/manual-test-checklist.md).

## Project status

The source and packaged artifact have been exercised on Linux with an isolated JOSM 19678 profile; the recorded project baseline includes 87 automated tests and an isolated GUI load check. A separate two-authorized-user read-only pilot passed: every returned ID, coordinate, and timestamp matched independent authenticated reads exactly. These are not claims of remote CI or publication. Windows/macOS have not been manually verified. Before any public release, run the documented manual checklist and review the exact release commit, CI, security, and packaging evidence in [Publishing](docs/publishing.md).

## License and contributions

GPL-3.0-or-later; see [LICENSE](LICENSE). This is an independent community project, not affiliated with or endorsed by Dawarich or the JOSM project. Contributions and private vulnerability reports are welcome; see [CONTRIBUTING](CONTRIBUTING.md) and [SECURITY](SECURITY.md).
