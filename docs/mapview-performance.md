# Actual MapView paint check

Reproduce after building the JAR:

```sh
python3 scripts/gui-smoke.py --renderer
```

This extends the isolated packaged-plugin GUI test with 50,000 synthetic fixes across one, two, and four layers. Each timed frame calls the real JOSM `MapView.paint` on the Swing event thread after small center/scale changes and layer invalidation, painting to a raster with an explicit clip. Imagery is disabled. This is CPU paint work in a Linux/Xvfb session, not input-to-display latency or a guarantee for other hardware, graphics stacks, imagery layers, Windows, or macOS.

The recorded run used JOSM 19678, Microsoft JDK 17.0.21.0.2, the same two-vCPU Linux container as the HTTP benchmark, and a 1280×900 Xvfb display. The first paint is reported separately; 20 subsequent frames are sorted for the warmed statistics. Machine-readable values are in `mapview-results.json`.

| Layers | Points | First paint ms | Warm mean ms | Warm p95 ms | Warm max ms |
|---:|---:|---:|---:|---:|---:|
| 1 | 50000 | 101.93 | 49.23 | 78.75 | 81.67 |
| 2 | 50000 | 38.64 | 40.50 | 51.68 | 51.80 |
| 4 | 50000 | 39.88 | 42.78 | 46.78 | 88.33 |

All warmed paints in this run were below the 100 ms target. The first one-layer paint was about 102 ms and missed that target; cold-start/JIT and offscreen-allocation costs are not hidden by the warmed summary. A separate earlier run was faster, so these are environment-specific observations rather than stable performance percentiles. The test also verified no startup API request, two isolated synthetic source loads, Load re-enablement, unchanged editable OSM data, and release of reference layers at shutdown.
