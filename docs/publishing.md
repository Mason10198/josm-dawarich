# Publishing and release gates

**No release has been published.** The intended repository is `Mason10198/josm-dawarich`, but remote creation/publication is outside this preparation task. Workflow files are local changes only; no GitHub Actions run, tag, release, or registry entry is claimed. A two-authorized-user read-only pilot passed: every point ID, coordinate, and timestamp matched independent authenticated reads exactly. No raw data, hostnames, or keys are included in public documentation.

## Before enabling or dispatching a release

1. Review the exact public tree and full Git history. Remove private URLs/hostnames, home paths, GPS data, credentials, household names, and private screenshots. Run an appropriate secret/privacy scan and inspect its findings manually; do not interpret a clean scan as proof.
2. Confirm repository ownership, public visibility, license, maintainer/security contact, and that the repository description makes clear this is an independent community project, not an official Dawarich product.
3. Review the API contract, key handling, server-side privacy restrictions, dependencies, permissions, and current Dawarich API behavior. The intentional `mask_privacy_zones=false` request requires a deliberate privacy review and must not be used to bypass server restrictions.
4. Run `./scripts/bootstrap-build.sh clean test dist` on Linux x86-64. Inspect the packaged JAR contents and verify it contains plugin classes/resources/license, not JOSM/JUnit classes, real data, or credentials. Exercise the JAR in an isolated JOSM 19678 profile; do not modify a production profile.
5. Complete [the manual checklist](manual-test-checklist.md), including network and lifecycle checks against a disposable/test instance with synthetic points. Windows/macOS checks remain unverified unless actually performed.
6. Ensure both CI matrix jobs pass on the exact commit to be released. CI uses Java 17/JOSM 19678 and Java 25/JOSM 19689. A new tag must point to a commit reachable from `main`, match the JAR's `Plugin-Version`, and have a successful completed CI run for that exact commit.
7. Review the workflow diff and repository/environment protection settings. Protect `main` against unreviewed changes and protect `v*` release tags against update/deletion; require reviewers for the `release` GitHub Environment. The publish job alone requests `contents: write` and targets that environment; configure its protection before dispatch. The verification/build job has read-only permissions. The workflow is manual only and must not be dispatched until these gates are approved.
8. After review, create an annotated `vX.Y.Z` tag at the approved commit and dispatch **Publish release** with that exact tag. The workflow rebuilds/tests, checks the artifact hash, then creates a GitHub Release with only `dawarich.jar` and its SHA-256 sidecar. Inspect the resulting release assets and notes before announcing anything.

The release job uses `CHANGELOG.md` as release notes. Update it with accurate version-specific notes before tagging. A successful local build, workflow syntax check, or configured-but-unrun workflow is not a public release or CI pass. JOSM registry listing is a separate, later process and is not automated here.

## Local preparation status

As prepared locally: pinned action references, read-only CI permissions, a Java/JOSM compatibility matrix, an exact-commit/tag guard, a separated protected publication job, and a hash sidecar for the built JAR. Still pending: public repository setup, repository/environment protection configuration, actual remote CI runs, a security/history review of the final public commit, manual release gates, any release publication.
