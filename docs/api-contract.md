# API and behavior contract

This document describes the plugin's current client behavior, not a promise about every Dawarich server version. Confirm the endpoint/query behavior against the exact server version and its documentation before connecting a real account. Synthetic local tests are not a live-service compatibility guarantee.

## Request

- Base URL is entered by the user; the client appends `/api/v1/points` while preserving any configured path prefix. Credentials in URL user-info, query, or fragment are rejected. HTTPS is required unless the user explicitly acknowledges HTTP.
- Each enabled source is queried sequentially with its own `Authorization: Bearer <key>` header. HTTP method is GET. Redirects are refused. No OSM authentication, OSM edits, uploads, or publishing requests are made.
- Query parameters: `page=1`, `per_page=10000`, `order=asc` for dates or `order=desc` for recent mode, `slim=true`, `include_anomalies=false`, `mask_privacy_zones=false`, `min_latitude`, `max_latitude`, `min_longitude`, `max_longitude`, `start_at`, and `end_at` (inclusive epoch seconds).
- Disabling masking is intentional for a private reference use case. Mandatory server-side controls still apply; never remove or weaken server authorization/privacy restrictions. The plugin makes no claim that a key is actually read-only: the key's server-granted privileges are outside the client's control.
- Response must be HTTP 200 with JSON content type, identity content encoding, `X-Current-Page: 1`, and a valid `X-Total-Pages`. The client accepts the current compact point schema (including numeric point ID, timestamp and latitude/longitude) and ignores no accuracy value to infer quality: no accuracy fields are requested or used.

## Bounds, time, and pagination

The viewport and dates are captured once when Load is pressed. Date endpoints are local calendar dates in the displayed system zone; the start is the beginning of the start date, and the end is the end of the end date, capped at the captured current instant. DST days therefore need not be exactly 24 hours. Date parsing is strict `YYYY-MM-DD`; verify the displayed zone and calendar dates.

Antimeridian-crossing views are unsupported; longitude bounds must be ordered and no wider than 180 degrees. Very broad/world bounds are rejected. A view whose diagonal exceeds 100 miles prompts for confirmation. Narrow the view/time interval instead of attempting a globe-wide query.

When a time window has more than one page, the client bisects the inclusive second range and fetches subranges in stable chronological order until each yields at most one page. It rejects duplicate IDs and points outside the requested time/rectangle. This limits page size, but it is **not a snapshot**: data can change during the fetch, and adjacent requests have no server-side transaction or snapshot token. A point updated during pagination can be missed or duplicated (duplicates fail the batch). For very dense single-second windows, the request fails and asks for a narrower area; there is no offset pagination fallback.

Recent mode covers all positive recorded timestamps through frozen now; displayed date fields do not restrict it. Descending page-1 traversal visits newer time windows first. If a validated descending page already supplies the remaining requested count, those original observations are selected and traversal stops; otherwise multi-page probes are discarded and split until their contents can be accepted without unstable offset pagination. At a cutoff tied to one recorded second, any subset of original observations at that timestamp is acceptable. A multi-page, single-second window that cannot supply the remaining count fails explicitly rather than dropping unknown observations. The final source contains at most N observations, reports its actual recorded coverage, and distinguishes reached N from fewer available. Rectangle filtering occurs on the server before newest selection; results are checked against the frozen scope on the client.

## Resource and source rules

One batch shares an adjustable aggregate point budget (default 50,000; allowed 1..250,000 including retained connector layers), 128 requests, 60 seconds, 8 MiB per response, and 64 MiB total response bytes. Raising point capacity does not raise the network safeguards. Requests are sequential and do not exceed one active request for the batch. Source quotas are divided evenly across enabled sources after accounting for retained unselected layers; integer remainder is not redistributed, and an unused/failed source's quota is not redistributed. Hidden retained layers still count. Recent N is per source and must fit each share; invalid combinations are rejected before source retrieval, not silently reduced. A zero remaining quota is not extra capacity. Date mode fails rather than truncating a result exceeding its share. Source publication independently verifies configured aggregate capacity and frozen options.

A successful source result creates or replaces only that source's layer. A failed refresh retains the old layer and marks it stale; a new source that fails gets no layer. Server changes cancel the batch and remove owned layers. Disabling a source hides its layer; removing a source removes it. Cross-source duplicate point IDs are reported as overlap, not merged.

The layers are read-only, non-savable JOSM layers, not GPX and not session-persisted custom layers. API keys are memory-only and must be entered again after restart. Validated non-secret common settings (base URL, local date range, source UUID, label, enabled state, and color) persist in JOSM preferences. No anonymization, accuracy filter, track-line construction, smoothing, snapping, or averaging occurs. Displaying exact private locations remains sensitive. Ordinary map-tile requests may disclose viewport bounds to tile providers, but do not directly send the plugin point contents; other independent JOSM services have separate network/privacy behavior.

## Error handling

Authentication failures, invalid responses, redirects, server/rate-limit failures, connectivity failures, cancellation, deadline, and limit errors fail closed. A shared batch failure can cause subsequent sources to be skipped; old layers are retained. Error messages should not contain keys, raw bodies, or full query URLs. Never paste private service logs/responses into public issues.
