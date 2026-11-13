# Security and privacy policy

## Scope and status

This is a community project, not an official Dawarich product. It has no published release or monitored public security inbox yet. Until a public repository/contact channel exists, do not send credentials, private instance URLs, raw GPS traces, household names, or vulnerability details to an unverified address or issue tracker. A two-authorized-user read-only pilot passed; no raw data, hostnames, or keys are included here.

## Data handling

The plugin sends HTTPS requests by default to the configured endpoint and its `/api/v1/points` path. A source API key is sent in that source's `Authorization: Bearer` header on GET requests. API keys and loaded custom layers are session-only; validated non-secret source settings (base URL, local date range, source UUID, label, enabled state, color) persist in JOSM preferences. Closing the dialog cancels active work and clears transient password-editor contents but does not clear session keys or layers. Removing a source, changing credentials/server, or shutting down clears applicable session state. This does not guarantee that the OS, JVM, proxy, server, or screen capture retains no metadata or imagery. Ordinary tile requests may disclose viewport bounds to tile providers, but do not directly send plugin point contents.

Only use a trusted endpoint. HTTP requires explicit acknowledgement and exposes the key and GPS query/results to network observers. Never use HTTP over an untrusted network. Use dedicated least-privilege keys even though this plugin calls GET only; permissions encoded in a key may be broader than read-only. The plugin does not test or constrain the key's server-side authorization scope.

The query deliberately sets `mask_privacy_zones=false` to display unmasked private reference points. Server-side mandatory restrictions still govern the request; do not disable or bypass them. Consider this data highly sensitive. The plugin has no accuracy-based filter, no location anonymization, and no track-line inference, smoothing, snapping, or averaging. Do not export/share layers or screenshots containing private locations.

The client enforces limits per load batch: at most 50,000 aggregate retained-plus-new points, 128 requests, 60 seconds, 8 MiB per response, and 64 MiB total response bytes. Source quotas are divided fairly across enabled sources after counting retained layers; unused quota is not redistributed. A failed refresh preserves the prior source layer, which remains counted in the aggregate limit even if hidden. Requests are sequential; date-window pagination has no server snapshot guarantee. See [API contract](docs/api-contract.md).

## Reporting a vulnerability

Until a verified private reporting channel is published, do not disclose details publicly if doing so could expose a user or instance. Contact the repository owner through a verified account/channel only after independently confirming its identity. Include a minimal reproduction using synthetic data, affected commit/build, impact, and mitigations; redact instance names, URLs, keys, coordinates, and household identifiers. Do not attach real GPS data or credentials. There is no response-time commitment at this pre-release stage.

## Maintainer release checks

Before publishing, inspect the complete tree and history for secrets, real hostnames, private paths, personal data, and GPS coordinates; review dependencies and permissions; confirm security contact instructions; run automated tests plus the manual checklist on an isolated profile; and verify the exact tagged commit and CI. Never treat an automated string scan as proof that private data is absent.
