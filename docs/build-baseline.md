# Build and verification baseline

## Supported local build path

The reproducible bootstrap is currently Linux x86-64-specific. `scripts/bootstrap-build.sh` downloads pinned Microsoft JDK 17.0.21.0.2, Apache Ant 1.10.15, JOSM 19678, and JUnit Platform Console Standalone 1.11.4 into `$HOME/.hermes/cache/toolchains`; hashes are recorded/verified by the bootstrap and `build.properties`. The JAR is built against the host-provided JOSM APIs with `--release 17`; JOSM, JUnit, and JSON libraries are not bundled as plugin dependencies.

Run the full build, automated tests, and distribution target with:

```sh
./scripts/bootstrap-build.sh clean test dist
```

For selective test classes, `-Dtest.includes` is a Java regex against class names, e.g. `-Dtest.includes='ConnectionStateTest'`.

## Current local verification

On the current Linux environment, the command above completed successfully: 19 test containers passed; 87 tests were found, started, and successful; none failed, skipped, or aborted. The produced `dist/dawarich.jar` contains 52 entries (73,369-byte JAR; 146,374 bytes of uncompressed contents), including plugin classes/resources and the packaged GPL license. It does not bundle JOSM or JUnit classes. SHA-256 for the locally produced JAR: `70cfc40ea71247f0ec6a818026c69d72ed3abb63de160fb52312ffe0ef040697`. This hash and size are local build evidence, not a release checksum or promise of identical output across different source revisions/toolchains.

The packaged GUI smoke `python3 scripts/gui-smoke.py` also passed locally on JOSM 19678 with an isolated temporary profile and synthetic loopback service: plugin loaded; zero startup requests; two independent source authorizations; two sources with ten synthetic points each; two GET requests; Load re-enabled; OSM editing data unchanged; and shutdown released plugin layers. Receipts are under the ignored `build/gui-smoke-*/` directory. A separate two-authorized-user read-only live pilot passed: every returned ID, coordinate, and timestamp matched independent authenticated reads exactly. No raw data, hostnames, or keys are included here. This does not establish Windows/macOS compatibility.

## CI and remaining checks

`.github/workflows/ci.yml` is configured with read-only `contents: read` permissions and immutable commit-pinned GitHub Actions: the baseline uses the checksum-pinned Microsoft Java 17/JOSM 19678 toolchain, plus a Java 25/JOSM 19689 compatibility job. The baseline also runs the synthetic packaged GUI smoke under Xvfb. The JOSM 19689 JAR was fetched from the official JOSM download endpoint and its SHA-256 verified locally as `feede61a84b3716c556536be38971d5c6d3b389265c7f8d98ef6a2c8aac49d51`. Java 25/JOSM 19689 has not been locally executed in this environment; these are configured CI checks only, not observed remote passes.

No public repository/remote, Actions run, release tag, GitHub Release, or registry entry is confirmed. Before publication, follow [the release gates](publishing.md) and [manual test checklist](manual-test-checklist.md). Windows/macOS bootstrap/install/use remain undocumented and unverified.
