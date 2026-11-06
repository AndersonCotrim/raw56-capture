package com.samsung.android.scan3d.camera;

import androidx.documentfile.provider.DocumentFile;

public interface Camera2API {

    void init();

    void captureBurst(OnCaptureBurstFinished onCaptureBurstFinished);

    void setSaveDir(DocumentFile pickedDir);

    void save();

    void close();
}
