# BSRApp

BSRApp is the Android application used to collect RAW images for the RAW-56 burst super-resolution dataset on a Samsung Galaxy S23 Ultra. Each press of the capture button starts a burst of 56 frames on each of three rear cameras. The app targets all three physical cameras (Camera2 IDs `2`, `6`, and `7`) in each capture request. A complete saved burst contains 168 Digital Negative (`.dng`) files.

## Capture a burst

1. Open this project in Android Studio, connect the phone, and run the `app` module. The project requires Android 14 (API 34) or later. The camera IDs are specific to the device configuration.
2. On first launch, grant the requested permissions. In the Android folder picker, choose a folder you can find again, such as `Documents/BSRApp`. The app remembers this folder for subsequent captures.
3. Frame the scene and tap the capture button once. When the app asks **“Deseja salvar captura?”** (Save capture?), tap **Sim** and wait for saving to finish.

## Find the DNG files

Open **My Files** on the phone and navigate to the folder you chose. Each saved burst has its own subfolder named with a date and time in the format `yyyyMMdd_HHmmss_SSS`. All three cameras write to that same subfolder.

For example, if you chose `Documents/BSRApp` in internal storage, the first frame from each camera might appear as follows:

```text
Documents/BSRApp/
└── 20260512_143022_900/
    ├── IMG_20260512_143020_125_2_0.dng
    ├── IMG_20260512_143020_125_6_0.dng
    └── IMG_20260512_143020_125_7_0.dng
```

In each filename, the last two numbers identify the physical camera and the zero-based frame index (`0` through `55`). For example, files ending in `_6_0.dng` and `_7_0.dng` belong to the same capture request. To copy a full burst to a computer, connect the phone by USB, select **File transfer**, and copy the timestamped subfolder.

## Preprocessed dataset

The published dataset uses two of the three camera streams. Each scene pairs the 56-frame burst from the 0.5× ultrawide camera with a single high-resolution reference from the 10× telephoto camera, captured with the first burst frame. A separate preprocessing project converts the DNG captures into NumPy arrays.

| File | Contents |
| --- | --- |
| `lr.npy` | 56 low-resolution Bayer frames |
| `hr.npy` | High-resolution reference image in linear RGB |
| `h_norm.npy` | Residual affine transforms for aligning the burst frames |

Download the preprocessed data from [RAW-56 Burst Super-Resolution on Hugging Face](https://huggingface.co/datasets/andersoncotrim/raw-56-burst-super-resolution).

The GitHub link for the preprocessing project will be added here when available.
