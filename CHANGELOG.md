# Changelog

Published artifacts and checksums are available from the GitHub releases page.

## v0.3.0

- Guide first-time users through imagery setup, GPS-source connection, point selection, and comparison. Add a discoverable Imagery-menu command while retaining the GPS command.
- Provide direct selected-source password entry, source-scoped session keys, field-specific safe validation, readable result summaries, and scrollable setup with fixed action buttons.
- Recommend recent-point mode only for genuinely new profiles. Preserve existing saved choices; move resource limits and unencrypted HTTP into Advanced settings.
- Show live received bytes, request count, elapsed time, selected points, source position, and recent target using a coalesced 200 ms UI timer. Sampling makes no extra network requests and introduces no per-point UI callbacks.
- Separate Stop download from Close, clear transient password copies, stop progress sampling on completion/cancel/hide/dispose, and retain old layers until successful complete replacement.
- Extend packaged acceptance with direct password entry, actual Imagery-menu discoverability, a paused partial response, responsiveness/progress checks before completion, and stalled-request close/cancel/reopen cleanup.
- Keep raw original observations, bounded retrieval, independent credentials, and read-only layers unchanged. Density views, accuracy filtering, and automatic load-on-pan remain proposals, not enabled features; see docs/alignment-tools.md.

## v0.2.0

- Add Most recent points mode: up to N original observations per authorized source inside the frozen viewport, ordered by recorded timestamp across all history; date fields are ignored in this mode.
- Add an adjustable shared point budget from 1 to 250,000 (default 50,000), preserving fair source quotas and retained-layer accounting. Network/time/byte safeguards remain independent.
- Keep Date range mode complete-or-fail rather than silently truncating historical queries. Show clear, distinct point/request/response/download limit messages.
- Show each successful source's actual recorded-time coverage and whether its requested recent count was reached or fewer observations were available.
- Persist non-secret mode/count/budget settings and restore source metadata correctly without persisting API keys.
- Add exact-oracle newest selection, real HTTP descending-order integration, raised-budget coordinator, scope/validation/preferences tests, and packaged two-mode GUI acceptance.
- Exercise actual MapView rendering at 100,000 and 250,000 aggregate synthetic points. Higher budgets are opt-in and can be noticeably slower; see docs/performance.md.

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
