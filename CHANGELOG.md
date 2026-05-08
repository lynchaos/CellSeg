# Changelog

All notable changes to CellSeg are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/).

---

## [0.1.0] – 2026-01-01

### Added
- On-device cell segmentation via Cellpose cyto3 ONNX model (XNNPACK-accelerated).
- Cloud fallback via Hugging Face Gradio Space with automatic SSE streaming.
- CameraX live-capture and gallery picker for JPEG / PNG / TIFF input.
- TIF/TIFF multi-channel import with `mil.nga:tiff` decoder.
- 2-up crossfade slider comparing original and labelled overlay in RunDetail.
- Flows pane (visualise flow magnitude) in RunDetail.
- Re-run and Delete actions on RunDetail screen.
- Export dropdown (CSV metrics, JSON full data, labelled PNG overlay) on RunDetail.
- Backend provenance tag ("Local" / "Cloud") on run cards.
- Batch processing queue (BatchScreen) with progress tracking and per-item retry.
- History screen with keyword search and Local / Cloud / All filter chips.
- Analyse screen: max-resize slider, diameter preset sheet, Reset button, model-guard.
- Settings screen: calibration management (add / remove), data management (clear cache, export all, delete all), About section with GitHub and Privacy links.
- Model Management screen: SHA-256 display, last-verified timestamp, test-inference benchmark, re-download, delete.
- Onboarding flow with optional cyto3 model download.
- Proto-backed DataStore settings (`SettingsRepository`).
- Room v2 database with `diameter` column migration.
- `MetricsCalculator`: cell count, confluence %, mean/median/stdev area (px + µm²), density (cells/cm²), 20-bin histogram.
- `SegmentationParams` validation (`validate()`) with documented ranges.
- FileProvider-based share sheet for all export types.
- Unit tests: `MetricsCalculatorTest`, `SegmentationParamsValidationTest`, `MaskDecoderTest`, `CellposePreprocessorTest`, `FlowFollowingPostprocessorTest`, `GradioSseParserTest`.

---

[0.1.0]: https://github.com/yaylali/cellseg-android/releases/tag/v0.1.0
