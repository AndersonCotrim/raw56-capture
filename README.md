# Project Description

This is an Android application for capturing RAW images used in the paper:
> Cotrim, A. N., Engler, C., Barbosa, G., Santos, C. A. N., & Pedrini, H. (2026). A real-world raw 56-frame burst dataset for 8× super-resolution. SSRN. https://doi.org/10.2139/ssrn.7389288

The application uses the Camera2 API to capture 56 RAW frames from three rear cameras (IDs `2`, `6`, and `7`) on a Samsung Galaxy S23 Ultra, saving them as `.dng` files.

## Installation and Usage

1. Open the project in Android Studio and run the `app` module on a Samsung Galaxy S23 Ultra (Android 14 or later).
2. Grant the required permissions and select an output folder.
3. Tap the capture button and wait for the files to be saved.

## Output Files

Each capture is saved in a timestamped subfolder (`yyyyMMdd_HHmmss_SSS`). Files follow the naming convention:

`IMG_<timestamp>_<camera_id>_<frame_index>.dng`

Example:

```text
Documents/
└── 20260512_143022_900/
    ├── IMG_20260512_143020_125_2_0.dng
    ├── IMG_20260512_143020_125_6_0.dng
    ├── IMG_20260512_143020_125_7_0.dng
    └── ...
```

The final two fields identify the physical camera (`2`, `6`, or `7`) and the zero-based frame index (`0`–`55`), respectively. For example, `_6_0.dng` and `_7_0.dng` correspond to the first frames captured by cameras `6` and `7` within the same burst.

## Preprocessed Dataset

The [published RAW-56 dataset](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution) contains 56 low-resolution RAW frames from the 0.5× ultrawide camera and a high-resolution reference from the 10× telephoto camera.

A separate [preprocessing pipeline](https://github.com/AndersonCotrim/raw56-preprocessing) converts the original DNG files into NumPy arrays, including the aligned LR bursts and HR references.