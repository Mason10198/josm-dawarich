# Task 1: build baseline and scope

Standalone Java 17 / Ant scaffold. Inputs: JOSM snapshot 19678 (official distribution URL), Microsoft OpenJDK 17.0.21.0.2, Apache Ant 1.10.15, JUnit Platform Console Standalone 1.11.4. Exact URLs and SHA-256/SHA-512 pins are in `build.properties`; `scripts/bootstrap-build.sh` fetches into `$HOME/.hermes/cache/toolchains`, verifies them, then runs Ant. On this host the JDK publisher checksum sidecar was verified; the JOSM and JUnit files were SHA-256 recorded after official/Maven repository downloads; Apache's published SHA-512 sidecar was verified.

`ant clean test` passes ArtifactContractTest; `ant dist` emits `dist/dawarich.jar` (SHA-256 `8dbe3d8b6090b0775cbfdef26dbae1dde19f4e76e552aa8948de70a615fa5d89`). It contains only the plugin class and SVG/icon/resource manifest; no JOSM, JSON-P, or JUnit classes. JOSM 19678 exposes `jakarta.json` classes, but this thin scaffold has no JSON dependency/use. Plugin constructor only invokes `Plugin` superclass; no network, private GPS, map data, or keys are accessed.

Synthetic artifact-contract evidence only; no real user data or external service has been used. GUI startup smoke is still to be run separately before claiming runtime loader proof.
