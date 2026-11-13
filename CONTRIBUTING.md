# Contributing

This independent community plugin is not an official Dawarich or JOSM product. Contributions should preserve privacy, read-only behavior, strict resource bounds, and compatibility with the declared JOSM baseline.

## Before opening a change

- Keep all tests and sample data synthetic; do not commit instance URLs, API keys, real GPS coordinates/traces, household names, private paths, or screenshots containing them.
- For network changes, retain HTTPS-by-default, GET-only requests, per-source authorization, bounded responses/time/requests, redirect refusal, and server-enforced privacy restrictions. Never add OSM API calls or edit/upload behavior.
- For layer changes, preserve non-savable reference-layer behavior, source isolation, and old-layer retention on failed refresh.
- Make the smallest focused change and add regression tests. Describe any intentional behavior change and its privacy implications.

## Local checks

On Linux x86-64 with the listed prerequisites, run:

```sh
./scripts/bootstrap-build.sh clean test dist
```

The build downloads pinned dependencies and verifies hashes. Tests compile against JOSM 19678 and target Java 17. For focused runs, `-Dtest.includes='ClassRegex'` selects test classes by regular expression. Do not claim tests or platform checks that you did not run. Review [the manual checklist](docs/manual-test-checklist.md) for changes affecting dialogs, layers, networking, installation, or privacy.

## Pull requests

Explain the user-visible change, tests actually run, platforms not tested, and any remaining risks. Use synthetic evidence only. Do not include real service responses, coordinates, keys, or private infrastructure identifiers. Public CI is configured but has not yet been observed running from this local checkout.
