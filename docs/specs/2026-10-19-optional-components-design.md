# Optional components: download the embedded browser and readboard on first use

Status: proposed (2026-10-19).

## Problem

The Windows `without.engine` portable package for next-2026-10-17.1 is a 277 MB zip that unpacks
to 596 MB. Two optional parts make up almost three quarters of the download:

| Part | Zip size | Share | Needed for |
|---|---:|---:|---|
| `app/jcef-bundle` (Chromium) | 113.6 MB | 41% | Yike web sync only |
| `app/readboard` (C#/.NET + OpenCV) | 88.0 MB | 32% | Board recognition only |
| `runtime` (JRE) | ~48 MB | 17% | Always |
| `app/lizzie-yzy2.5.3-shaded.jar` | 22.1 MB | 8% | Always |

readboard's `readme.rtf` and `readme_en.rtf` alone take 38.9 MB on disk once unpacked, because they
contain embedded images.

## Proposal

The update system already has what this needs. Signed manifests (`SignedUpdateEnvelope`, Ed25519)
list `components[]`, each with `id`, `platform`, `flavor`, `version`, `assetName`,
`downloadUrl`, `mirrorUrls`, `sizeBytes`, `sha256` and `installAction`.
`ResumableDownloader` resumes downloads, checks size and SHA-256, and tries R2 before GitHub.
`InstalledUpdateState` records what is installed. Today only `core` is published.

1. Publish `jcef` and `readboard` as components with their own versions (the JCEF release tag,
   the readboard version). Component assets are uploaded once per component version, not once per
   app release.
2. Base packages (`without.engine`, `core-update`) no longer contain `app/jcef-bundle` or
   `app/readboard`.
3. When a Yike room or board sync is first used, show one dialog ("download the browser component,
   about 114 MB") with progress and cancel. Then download through `ResumableDownloader`, verify,
   extract, and record the component in `InstalledUpdateState`. If the app directory is
   read-only, install per user.
4. Later updates download a component only when its version changes; `WindowsUpdatePlan` already
   selects changed components.
5. Replace the readboard RTF readmes with a link to the online help, or a compressed PDF.

### Platform notes

- **Windows**: components extract next to the app, or per user when the portable folder is
  read-only.
- **macOS**: writing code into a signed `.app` breaks its signature. Either keep JCEF bundled on
  macOS, or install a separately signed and notarized component under
  `~/Library/Application Support/LizzieYzy Next/components/` and point
  `CefAppBuilder.setInstallDir` at it.
- **Linux**: jcefmaven already downloads JCEF at runtime; no change is needed.
- **Offline use**: the full bundles (`with-katago`, `nvidia`) keep every component, and a
  downloaded component zip can be imported by hand.

## Expected impact

The Windows base package drops from 277 MB to about 75 MB, and every bundle that includes
JCEF and readboard shrinks by about 200 MB.

## Open questions

- Antivirus false positives on DLLs downloaded after installation.
- Corporate proxies: the existing `NetworkProxy` settings must apply to component downloads.
- Whether the first-use prompt should also run from Settings so it can be prepared ahead of time.
