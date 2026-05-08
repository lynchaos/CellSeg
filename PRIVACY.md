# Privacy Policy — CellSeg

**Last updated:** see git history for this file.

## Data collected

CellSeg collects **no personal data**. Specifically:

- No analytics SDK is included.
- No crash reporting is sent to third parties.
- No advertising identifiers are accessed.
- Images captured or analysed remain on your device unless you explicitly share them via the Android Share sheet.

## Network requests

When using **local segmentation**, no image data leaves your device.

When using **cloud segmentation** (Hugging Face Gradio Space), images are sent to the configured Hugging Face Space for processing. Please review the [Hugging Face Privacy Policy](https://huggingface.co/privacy) and the terms of the Space you use.

Your Hugging Face API token (if provided) is stored in Android's `EncryptedSharedPreferences` and is never logged or transmitted to any server other than `api.huggingface.co` and configured Spaces.

## Storage

All files (images, segmentation outputs, metrics) are stored in app-private storage (`Context.filesDir`) and are deleted when the app is uninstalled.

## Contact

`support@yaylali.uk`
