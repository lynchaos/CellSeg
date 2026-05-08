# Contributing to CellSeg

Thank you for your interest in contributing!

## Development setup

1. Install JDK 21 and Android SDK (API 35).
2. Clone the repo and open in Android Studio Ladybug (2024.2) or later.
3. The ONNX model is downloaded at runtime — no manual setup needed.

## Code style

- Kotlin only, no Java.
- Follow the existing Compose + MVVM patterns.
- No KAPT — use KSP for annotation processing.
- No NDK/C++.
- No Firebase, analytics SDKs, or ads.

## Submitting changes

1. Fork the repository and create a feature branch from `main`.
2. Ensure `./gradlew testDebugUnitTest` passes.
3. Open a pull request with a clear description of the change.

## Licence

By contributing you agree that your contributions will be licensed under the [MIT Licence](LICENSE).
