# RAW-56 Capture

This is an Android application for capturing RAW images used in the paper:
> Cotrim, A. N., Engler, C., Barbosa, G., Santos, C. A. N., & Pedrini, H. (2026). A real-world raw 56-frame burst dataset for 8× super-resolution. SSRN. https://doi.org/10.2139/ssrn.7389288

The application uses the Camera2 API to capture 56 RAW frames from three rear cameras (IDs `2`, `6`, and `7`) on a Samsung Galaxy S23 Ultra, saving them as `.dng` files.

## Requirements

- Samsung Galaxy S23 Ultra
- Android 14 (API 34) or later
- Android Studio

## Usage

1. Open the project in Android Studio and run the `app` module on the device.
2. Grant the required permissions and select an output directory.
3. Tap the capture button to acquire and save the burst.

## Output

Each capture is stored in a timestamped directory (`yyyyMMdd_HHmmss_SSS`) containing files named `IMG_<timestamp>_<camera_id>_<frame_index>.dng`.

```text
captures/
└── 20260512_143022_900/
    ├── IMG_20260512_143020_125_2_0.dng
    ├── IMG_20260512_143020_125_6_0.dng
    ├── IMG_20260512_143020_125_7_0.dng
    └── ...
```

The camera ID identifies the physical camera, and the frame index ranges from `0` to `55`. The [preprocessing pipeline](https://github.com/AndersonCotrim/raw56-preprocessing) uses camera `2` (ultrawide LR) and camera `7` (telephoto HR) to generate aligned training pairs.

## Related Resources

- **Dataset:** [Hugging Face](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution) ([DOI](https://doi.org/10.57967/hf/10146))
- **Preprocessing:** [raw56-preprocessing](https://github.com/AndersonCotrim/raw56-preprocessing) — DNG processing and dataset generation.
- **Training and Evaluation:** [SBFBurst](https://github.com/AndersonCotrim/SBFBurst) — baseline training and evaluation scripts.

## Citation

Cotrim, A. N., Engler, C., Barbosa, G., Santos, C. A. N., & Pedrini, H. (2026). *A real-world raw 56-frame burst dataset for 8× super-resolution*. SSRN. [DOI: 10.2139/ssrn.7389288](https://doi.org/10.2139/ssrn.7389288).
