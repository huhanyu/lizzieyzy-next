# Release assets: separate engine packs and shorter release notes

Status: proposed (2026-10-19).

## Problem

- next-2026-10-17.1 has 43 assets totalling 16.1 GB. There were 144 releases (63 pre-releases)
  between 2026-03 and 2026-10, mostly with the same engine and runtime files.
- In October the median Windows release build took 77 minutes, and "Publish Requested
  Pre-release" failed 4 of its 14 runs.
- KataGo builds, CUDA/TensorRT/ROCm/OpenVINO runtimes, JCEF and readboard change a few times a
  year, but the app jar changes almost every day.
- The release page is about 64 KB and 383 lines. Each of six languages repeats the full download
  guide, so users struggle to find which file to download.

## Proposal

1. **Engine packs.** Publish each engine/runtime combination once, as a versioned
   `engine-pack-<backend>-<katago-version>-<platform>` asset. The "Pinned KataGo Source" workflows
   already build pinned sources. App releases reference the packs from
   `lizzieyzy-next-download-catalog.json`.
2. **App releases.**
   - Pre-releases ship only `core-update`, the `without.engine` packages, and the
     catalog/manifest/envelope files.
   - Full bundles (`with-katago`, `nvidia`, …) are built for stable releases only.
   - The updater installs engine packs as components, using the same flow as
     [optional components](2026-10-19-optional-components-design.md).
3. **Release notes.** Each language section contains only "what's new" (from `## Unreleased` in
   CHANGELOG.md) and one link to the download chooser on goagent.top. The download guide lives
   in [docs/PACKAGES.md](../PACKAGES.md) and on the website, not in every release.

## Migration

1. Add a compact mode to `scripts/generate_release_notes.py` and teach
   `scripts/validate_release_notes.py` to accept it. This has no effect on users.
2. Publish engine packs as their own releases. Point the catalog at them, and keep full bundles
   for stable releases.
3. Let the updater install engine packs as components.

## How success is measured

For each release: uploaded bytes, build minutes, publish failure rate, and release-page length.
Targets for a pre-release: under 2 GB uploaded and under 30 build minutes.
