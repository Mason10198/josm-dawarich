# Build and verification baseline

## Supported local build path

The reproducible bootstrap is Linux x86-64-specific. `scripts/bootstrap-build.sh` downloads pinned Microsoft JDK 17.0.21.0.2, Apache Ant 1.10.15, JOSM 19678, and JUnit Platform Console Standalone 1.11.4 into `$HOME/.hermes/cache/toolchains`; hashes are verified by the bootstrap and `build.properties`. The plugin targets Java 17; JOSM, JUnit, and JSON libraries are not bundled.

```sh
./scripts/bootstrap-build.sh clean test dist
python3 scripts/gui-smoke.py
```

Selective class tests use a Java regex, e.g. `-Dtest.includes='ConnectionStateTest'`.

## v0.2.0 local verification

The feature passed 112 automated tests and isolated packaged-plugin GUI acceptance on Linux/JOSM 19678. The GUI exercised both date and newest-N modes, two independent synthetic authorizations, exact newest original IDs, history older than ignored date fields, actual recorded coverage, count availability, unchanged OSM editing data, and shutdown cleanup. Higher-count actual MapView measurements and their limitations are in [performance.md](performance.md). Receipts stay in ignored `build/gui-smoke-*/` directories. Release checksums belong to each published release's sidecar, not a stale source-document hash.

Earlier private read-only two-user validation matched IDs, coordinates, and timestamps against independent authenticated reads. It is separate evidence, not a claim that this feature was live-tested using a new production key. No raw points, private hostnames, account mappings, or credentials are included here. Windows/macOS manual desktop compatibility is not established.

## Compatibility CI and artifact provenance

CI has read-only `contents: read` permission and commit-pinned GitHub Actions. The baseline uses the checksum-pinned Java 17/JOSM 19678 toolchain and runs the packaged GUI under Xvfb. The compatibility job uses Java 25 and the JOSM project's versioned GitHub release asset `19689/JOSM-Linux-java25-19689.jar`.

That asset's SHA-256, verified against the publisher's GitHub release asset digest, is `97357c101fbfb81d3b68f34bf1c63918acb88b897d109d697e0a1363d012ad1f`. Its embedded revision is 19689. The separate revision-numbered nightly snapshot URL is not byte-immutable: a later download changed only `REVISION`, the manifest, and the two signing entries, while all class/resource entries remained identical. CI therefore pins the versioned publisher release asset instead of accepting changing nightly hashes or disabling verification.

Java 25 compatibility is exercised by remote CI, not locally on the Java 17-only baseline. Check the exact commit's run; an earlier pass does not certify later changes. v0.1.0/v0.1.1 were published and verified through the official directory. Every later release must independently pass [publishing.md](publishing.md), exact-commit CI, artifact verification, and catalog/native-install checks.
