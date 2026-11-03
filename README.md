# Project Description

This is an Android application developed to acquire RAW images for the [RAW-56 Burst Super-Resolution Dataset](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution) using a Samsung Galaxy S23 Ultra.

Each capture triggers a burst of 56 frames across three rear cameras. The application uses Android's Camera2 API to target the physical cameras with IDs `2`, `6`, and `7`, saving the captured RAW images as Digital Negative (`.dng`) files.

## Installation and Usage

1. **Install the application.** Open the project in Android Studio, connect the smartphone, and run the `app` module. The application requires Android 14 (API 34) or later. The camera IDs are specific to the Samsung Galaxy S23 Ultra configuration.
2. **Configure the output folder.** On first launch, grant the requested permissions and select a destination folder using the Android folder picker. The selected location is saved for subsequent captures.
3. **Capture a burst.** Frame the scene and tap the capture button once. Wait for the acquisition and file-saving process to finish before starting another capture.

## Output Files

Captured DNG files can be accessed through the phone's **My Files** application. Each burst is stored in a separate subfolder named using the timestamp format `yyyyMMdd_HHmmss_SSS`. Files from all three cameras are saved in the same subfolder.

For example, a capture saved to `Documents/` may produce the following structure:

```text
Documents/
└── 20260512_143022_900/
    ├── IMG_20260512_143020_125_2_0.dng
    ├── IMG_20260512_143020_125_6_0.dng
    ├── IMG_20260512_143020_125_7_0.dng
    └── ...
```

Each filename follows the pattern:

`IMG_<timestamp>_<camera_id>_<frame_index>.dng`

The final two fields identify the physical camera (`2`, `6`, or `7`) and the zero-based frame index (`0`–`55`), respectively. For example, `_6_0.dng` and `_7_0.dng` correspond to the first frames captured by cameras `6` and `7` within the same burst.

## Preprocessed Dataset

The [published RAW-56 dataset](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution) uses two of the three captured camera streams:

- **Low-resolution (LR):** A 56-frame RAW burst acquired with the 0.5× ultrawide camera.
- **High-resolution (HR):** A single reference image acquired with the 10× telephoto camera, corresponding to the beginning of the burst.

A separate preprocessing pipeline converts the original DNG files into NumPy arrays, including the aligned LR bursts and HR references.

*The preprocessing code will be linked here once publicly available.*

## Citation

If you find BSRApp or the RAW-56 dataset useful in your research, please cite the associated dataset publication and reference the [dataset repository](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution).
