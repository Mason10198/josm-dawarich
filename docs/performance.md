# Synthetic performance benchmark

Run with the repository's pinned Java 17/Ant toolchain:

```sh
./scripts/bootstrap-build.sh clean test benchmark
```

The `benchmark` target starts a loopback-only synthetic HTTP server (no credentials or external service), then sends generated point responses through the production JDK `HttpClient`, `DawarichClient`, `TimeWindowFetcher`, `LoadReferenceTask`, and `MainLayerManager`. It verifies exact aggregate and per-source point counts. The fixture uses 1970-01-01 timestamps within a frozen epoch-99 query, coordinates near (0,0), distinct per-source IDs, and divides the 50,000-point four-source case into 12,500 points per source. Responses are capped at 10,000 points and the production fetcher must split time windows. No OSM imagery or GPS data is involved.

Results are written to `build/benchmark-results.json` and are intentionally not committed as machine-independent measurements. One run per configuration is reported; these are observations, not repeatable medians or p95 estimates. Heap deltas are post-GC used-heap snapshots with the fixture/server already allocated; they are not peak allocation or RSS. The heap-pool sum is a cumulative JVM high-water diagnostic, not a simultaneous-live-set estimate. Render work calls the production `DawarichLayer.paintPoints` seam over a synthetic 1024x768 raster; it is not JOSM `MapView`/projection/UI frame rate. Cancellation timing measures from immediately before `task.cancel()` until its future completes, with the loopback server stalled either before response headers or after sending/flushing the opening `[` of a JSON body.

## Higher point budgets (v0.2.0)

The point budget is adjustable to 250,000; 50,000 remains the default. To reproduce the higher-count **actual MapView** measurements:

```sh
python3 scripts/gui-smoke.py --renderer --points 100000
python3 scripts/gui-smoke.py --renderer --points 250000
```

Both runs first exercised the installed plugin's real date-range and recent-mode UI against a synthetic HTTP server. Recent mode selected exact newest IDs, included historical observations older than the ignored date fields, and reported fewer available for a second independently authenticated source. The GUI fixture explicitly resets the viewport before the second query; JOSM may adjust its view when layers are added, so stale fixture bounds are not a valid connector failure.

Machine-readable measurements are in [recent-mapview-results.json](recent-mapview-results.json). Each configuration used 21 actual `MapView.paint` calls on the EDT while changing center/scale and invalidating layers; first paint is separate from 20 warmed frames. Imagery was disabled. This is one synthetic Linux run per configuration, not a Windows desktop, API download, peak-memory, or sustained-interactivity guarantee.

| Aggregate points | Sources | Warm mean ms/frame | Warm p95 ms/frame | First paint ms |
|---:|---:|---:|---:|---:|
| 100,000 | 1 | 99.0 | 118.6 | 107.3 |
| 100,000 | 2 | 92.4 | 108.8 | 102.8 |
| 100,000 | 4 | 98.3 | 115.8 | 88.3 |
| 250,000 | 1 | 214.2 | 236.2 | 244.5 |
| 250,000 | 2 | 201.4 | 218.3 | 197.9 |
| 250,000 | 4 | 224.0 | 263.6 | 214.1 |

Higher counts render correctly but do **not** meet the earlier <100 ms/frame target consistently; 250,000 points is noticeably slower. This supports an opt-in adjustable ceiling rather than claiming the largest budget is universally fast. A coordinator regression separately publishes 77,222 synthetic observations with a 100,000-point budget, proving that the higher setting is enforced end-to-end rather than only accepted by the dialog.

## Guided UI / live progress acceptance (v0.3.0)

The packaged GUI acceptance pauses an actual partial HTTP response. It verifies received-byte/elapsed labels change before the page completes, no partial dataset replaces an old layer, a UI-thread ping completes within 500 ms, only the expected request is issued, and the timer stops on completion. A second stalled request exercises Close cancellation, editor wiping, retained layers, idle reopen, and no automatic download. These are bounded synthetic checks, not a live-server latency promise.

Alternating released-v0.2.0/current-candidate HTTP benchmark runs are retained in `ux-progress-benchmark-results.json`: two fresh JVMs per version. All nine configurations preserve the exact original point oracle and the same HTTP request counts. Timings are noisy: at 50,000 points/one source the candidate took 930/951 ms versus 690/732 ms for the release; other configurations overlap or reverse the ordering. This is not sufficient to claim a speedup or rule out a regression. It excludes the Swing timer and real network latency. Production rendering code is unchanged; progress uses constant-size snapshots and accepted-page counter updates, not per-observation callbacks or additional requests. Use actual MapView and paused-response checks alongside these results rather than treating a local microbenchmark as end-user throughput.

## Recorded run

Environment: Linux 7.0.14-26-pve (glibc 2.41), Microsoft OpenJDK 17.0.21.0.2, AMD Ryzen 7 PRO 8845HS, 2 visible logical CPUs. The produced plugin jar was 73,369 bytes (52 entries; thin, no bundled dependency jars).

| Aggregate points | Sources | Load ms | Post-GC used-heap delta | 20 painter frames ms | HTTP requests |
|---:|---:|---:|---:|---:|---:|
| 1,000 | 1 | 361 | 7,867,000 B | 23 | 1 |
| 1,000 | 2 | 94 | 781,320 B | 14 | 2 |
| 1,000 | 4 | 169 | 1,711,296 B | 22 | 4 |
| 10,000 | 1 | 67 | 3,851,616 B | 116 | 1 |
| 10,000 | 2 | 84 | 4,888,464 B | 137 | 2 |
| 10,000 | 4 | 126 | 1,566,296 B | 124 | 4 |
| 50,000 | 1 | 618 | 5,864,136 B | 578 | 15 |
| 50,000 | 2 | 542 | 9,665,160 B | 587 | 14 |
| 50,000 | 4 | 461 | 9,590,344 B | 602 | 12 |

A stalled-header cancellation completed in 3 ms; cancellation after the JSON body began completed in 0 ms at millisecond precision (sub-millisecond elapsed time). At 50,000 points, painter work averaged about 29–30 ms per frame across source counts (20-frame total divided by 20). These synthetic results meet the 3-second load, <100 MiB post-GC incremental heap, and <100 ms painter-work/frame targets in this run. The 1,000-point first configuration includes cold-start/JIT overhead; the single-run measurements are noisy and are not a substitute for repeated trials or full desktop-renderer profiling.
