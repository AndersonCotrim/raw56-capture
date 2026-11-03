package com.samsung.android.scan3d;

import android.Manifest;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class Constants {
    public static String[] PERMISSIONS;

    static {

        List<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.CAMERA);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        PERMISSIONS = permissions.toArray(new String[0]);
    }

}
