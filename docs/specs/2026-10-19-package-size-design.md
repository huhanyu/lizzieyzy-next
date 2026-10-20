# Smaller packages without a second download

Status: revised (2026-10-19). This replaces the earlier proposal to download the embedded browser
and readboard on first use.

## Decision

Every package that a user downloads for a fresh install stays complete. Download it, unzip it (or
open the DMG), and run it. It works offline, with Yike sync (JCEF), board recognition (readboard),
KataGo and a default weight already inside. No feature asks for a second download before it works.

There are three reasons:

- The main audience wants software that works right after unzipping. "Download a component first"
  is a step many of them will not understand, or will abandon.
- Board recognition and Yike sync are everyday features for that audience. Most users would hit
  the second download anyway, so the smaller base package would be smaller only on paper.
- A second download adds failure modes that the complete package avoids:
  - slow or blocked GitHub, and proxies;
  - antivirus quarantining DLLs fetched after installation;
  - read-only portable folders;
  - signed macOS bundles that cannot be modified;
  - one more install path to test on every platform.

## Where the size is (next-2026-10-17.1)

Measured from the release zips' central directories.

| Part | Windows `with-katago` zip | Note |
|---|---:|---|
| `weights/default.bin.gz` | 250.0 MB | Already gzip; the default weight is a product choice |
| `jcef-bundle` | 113.6 MB | `libcef.dll` alone is 89.5 MB; locales are already cut to six |
| `readboard` | 88.0 MB | Self-contained .NET 10 + OpenCV |
| `runtime` | 48.4 MB | Already a jlink-trimmed JRE |
| App jar | 22.1 MB | |
| KataGo CPU engine and readme PDFs | 7.5 MB | |
| **Total** | **530.1 MB** | Unpacks to 853 MB |

The NVIDIA package (1,840.5 MB) adds 1,313 MB of CUDA, cuDNN and NVRTC libraries.

## Make each package smaller, with the same steps for users

1. **readboard**. This work happens upstream in qiyi71w/readboard and saves about 21 MB of
   download and 73 MB on disk:
   - Leave out `opencv_videoio_ffmpeg4100_64.dll` (10.4 MB zip, 25 MB on disk).
     - It is OpenCV's video-file backend, pulled in by `OpenCvSharp4.Windows`.
     - The readboard source never uses `VideoCapture` or `VideoWriter` (checked at a292eea).
   - Leave the debugger support files out of the publish output (2.7 MB): `createdump.exe`,
     `mscordaccore*`, `mscordbi`, `Microsoft.DiaSymReader.Native` and `readboard.pdb`.
   - Limit `SatelliteResourceLanguages` to the languages readboard ships. It now carries 13
     WinForms satellite folders (2.6 MB).
   - Replace `readme.rtf`, `readme_en.rtf` and `readme_jp.rtf` (5.9 MB zip, 40 MB on disk, mostly
     embedded images) with a compressed PDF or a link to the online help.
2. **JCEF**. No change: the bundle is already reduced to what Chromium needs.
3. **NVIDIA packages**. These could save about 211 MB (11%), but each needs a test on an RTX
   machine before it ships:
   - `cudnn_adv64_9.dll` (177.4 MB zip) is cuDNN's RNN and attention sub-library. KataGo's CUDA
     backend uses the convolution, ops, heuristic and engine libraries. Check that the CUDA backend
     and the TensorRT probes still start without it.
   - `nvrtc64_120_0.alt.dll` (34.4 MB zip) ships next to `nvrtc64_120_0.dll`, which has the same
     size. Check which of the two cuDNN loads.

## Where downloading parts does help: updates

The Windows updater already plans updates per component. `WindowsUpdatePlan` compares the manifest's
`components[]` with `InstalledUpdateState`, always selects `core`, and selects other changed
components that set `defaultSelectedIfChanged`. Releases, however, publish only `core`. When KataGo,
the runtime, JCEF or readboard change, users are told to download the whole package again.

Publish those parts as versioned components so that the in-app update fetches only what changed,
in the same dialog. This removes a manual step instead of adding one.

- Fresh installs keep using the complete packages above.
- A failed component download stops the update before anything is replaced, and the installed
  version keeps working.
- macOS keeps updating the whole signed app.

## Optional extras stay optional

TensorRT for RTX 30 and older is already an opt-in inside `KataGo 一键设置`, with an offline 7z for
machines without a network connection. Use this pattern only for extras that the app does not need
in order to work.

## Choosing a package is the bigger hurdle

The README's download table has 17 rows. Show one recommended download per platform first:

- **Windows**: NVIDIA RTX 20 or newer → the NVIDIA package; anything else → the OpenCL package
  (it falls back to CPU).
- **macOS**: the DMG for the chip.
- **Linux**: the zip.

Put installers, experimental backends and engine-less packages under "other packages". The
download page can also preselect a package from the GPU that the browser reports, while still
offering the full list.

## Open questions

- The readboard items need qiyi71w's agreement, because readboard is a separate repository.
- The NVIDIA items need a run on real RTX hardware, covering both the CUDA and TensorRT paths.
