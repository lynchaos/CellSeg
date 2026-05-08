# CellSeg

[![Build](https://github.com/lynchaos/CellSeg/actions/workflows/android.yml/badge.svg)](https://github.com/lynchaos/CellSeg/actions)
[![GitHub release](https://img.shields.io/github/v/release/lynchaos/CellSeg?label=release)](https://github.com/lynchaos/CellSeg/releases/latest)
[![License: MIT](https://img.shields.io/badge/licence-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen)](https://developer.android.com)
[![Website](https://img.shields.io/badge/website-cellseg.yaylali.uk-teal)](https://cellseg.yaylali.uk)

**CellSeg** — on-device microscopy cell segmentation for Android.

Segment brightfield, phase-contrast, and fluorescence images directly on your Android device using the [Cellpose](https://github.com/MouseLand/cellpose) cyto3 model (ONNX, on-device) or via a Hugging Face Gradio Space (cloud fallback). No data leaves your device when running locally.

> **Research use only.** CellSeg is not a medical device and is not CE/FDA-marked. Do not use for clinical, diagnostic, or therapeutic decision-making.

---

## Features

- Capture images via camera or pick from gallery (TIFF supported)
- Tag samples with channel, magnification, cell line, well ID, timepoint
- Local segmentation with Cellpose cyto3 ONNX (~26 MB, downloaded on first use)
- Cloud segmentation via Hugging Face Gradio Spaces (public or private)
- Adjustable parameters: diameter, flow threshold, cellprob threshold, channels
- Per-run metrics: cell count, confluence %, area statistics
- Run history, batch export (CSV)
- Cellpose attribution acknowledgement on first launch

---

## Build

### Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 21 |
| Android SDK | API 35 |
| Android NDK | _not required_ |

### Steps

```bash
git clone https://github.com/lynchaos/CellSeg.git
cd CellSeg
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

### Run unit tests

```bash
./gradlew testDebugUnitTest
```

### Run instrumented tests (device/emulator required)

```bash
./gradlew connectedDebugAndroidTest
```

---

## Architecture

```
app/
├── data/
│   ├── db/          Room entities, DAOs, AppDatabase
│   ├── datastore/   Proto DataStore settings
│   ├── filestore/   File-path helpers (originals, masks, exports)
│   ├── local/       TokenStore (EncryptedSharedPreferences)
│   ├── ml/          ONNX preprocessing, inference, postprocessing
│   └── remote/      Gradio HTTP/SSE client
├── di/              Hilt modules
├── domain/
│   ├── backend/     SegmentationBackend interface + progress types
│   ├── model/       Domain models (Sample, SegmentationRun, …)
│   └── repo/        Repository interfaces
└── ui/
    ├── navigation/  Type-safe NavGraph (kotlinx.serialization routes)
    ├── screen/      One package per screen (Screen + ViewModel)
    └── theme/       Material 3 theme
```

### Key dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose + Material 3 | UI |
| Hilt 2.52 | Dependency injection (KSP) |
| Room 2.6.1 | Local database (KSP) |
| DataStore Proto | Settings persistence |
| CameraX 1.4 | Camera capture |
| ONNX Runtime Android 1.19 | On-device inference (NNAPI → CPU/XNNPACK) |
| OkHttp + Retrofit | HTTP, Gradio SSE stream |
| Moshi | JSON (KSP codegen) |
| Coil 2.7 | Image loading |
| Android-TiffBitmapFactory | TIFF decode |

---

## Model

The ONNX model (`cyto3-fp16.onnx`, ~26 MB) is **not bundled** in the APK. It is downloaded on first use from:

```
https://huggingface.co/lynchaos/cellpose-cyto3-onnx/resolve/main/cyto3-fp16.onnx
```

To rebuild the model from Cellpose weights, see [`tools/convert_cyto3_to_onnx.py`](tools/convert_cyto3_to_onnx.py).

---

## Licence

Source code: [MIT Licence](LICENSE)

The Cellpose **codebase** is BSD 3-Clause (Howard Hughes Medical Institute).

The Cellpose cyto3 **model weights**, converted to ONNX FP16 and hosted at
`huggingface.co/lynchaos/cellpose-cyto3-onnx`, are redistributed with the explicit
permission of the upstream authors at HHMI Janelia (granted by Marius Pachitariu,
May 2026), under the conditions of attribution and licence propagation.
See the [About page](https://cellseg.yaylali.uk/about) for full details.

---

## Privacy

[Privacy Policy](https://cellseg.yaylali.uk/privacy/)

No analytics, no ads, no IAP, no third-party telemetry. See also [`PRIVACY.md`](PRIVACY.md).

---

## Support

`support@yaylali.uk`
