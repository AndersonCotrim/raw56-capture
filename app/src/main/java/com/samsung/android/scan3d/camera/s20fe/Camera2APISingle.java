package com.samsung.android.scan3d.camera.s20fe;

import androidx.documentfile.provider.DocumentFile;

public interface Camera2APISingle {

    void openPreview();

    void captureBurst(DocumentFile documentFolder);

    void closeCamera();

}
