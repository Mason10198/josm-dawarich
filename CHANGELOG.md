# Changelog

Published artifacts and checksums are available from the GitHub releases page.

## v0.1.1

- Show fixed, privacy-safe per-source failure reasons instead of only FAILED. Request timeouts, authorization failures, connectivity failures, protocol problems, and capacity limits can now be distinguished.
- Put each source status on its own line for readability.
- Suppress unknown/raw error text and retain escaping of source labels; no API keys, HTTP bodies, URLs, or point identifiers are added to diagnostics.
- Add failure-display regression coverage and initialize the action-test preferences so the class also runs independently.
- This is a diagnostic fix, not a claim that every reported connection failure is resolved.

## v0.1.0

First public release; exact-commit compatibility CI, downloaded-artifact GUI verification, official catalog inclusion, and fresh native installation passed.

- Add a JOSM menu action to load read-only Dawarich reference points for the current map view.
- Add per-source labels, colors, enable/disable controls, and session-only API-key entry.
- Add bounded, date-windowed GET fetching, fair source quotas, cancellation, and stale-layer preservation on failed refresh.
- Document privacy, API, local build, and manual release checks.
- Add pinned, read-only GitHub Actions CI and a manually guarded, reproducible release workflow. Both compatibility jobs passed for the initial implementation PR; each release still requires successful CI on its own exact commit.
