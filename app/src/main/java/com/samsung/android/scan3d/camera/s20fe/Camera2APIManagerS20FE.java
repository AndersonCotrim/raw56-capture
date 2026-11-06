package com.samsung.android.scan3d.camera.s20fe;

import android.app.Activity;
import android.content.Context;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.samsung.android.scan3d.camera.Camera2API;
import com.samsung.android.scan3d.camera.OnCaptureBurstFinished;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera2APIManagerS20FE implements Camera2API {
    private static final String TAG = "Camera2APIManager";

    private final int N_BURST = 42;

    private final String[] CAM_IDS = new String[]{"0", "2", "52"};

    private int previewIndex;

    private Context context;

    private TextureView textureView;

    private DocumentFile pickedDir;

    private Camera2APISingleS20FE[] camera2APISingleS20FEArray;

    private Camera2APISingleS20FE[] camera2APISingleS20FEArraySingle;

    MediaActionSound sound;

    public Camera2APIManagerS20FE(Context context, TextureView textureView) {

        this.context = context;
        this.textureView = textureView;
        this.previewIndex = 0;

        sound = new MediaActionSound();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        camera2APISingleS20FEArray = new Camera2APISingleS20FE[CAM_IDS.length];

        for (int i = 0; i<camera2APISingleS20FEArray.length; i++){
            camera2APISingleS20FEArray[i] = new Camera2APISingleS20FE(context, mainHandler,
                    CAM_IDS[i], N_BURST, cameraManager);
        }


        camera2APISingleS20FEArraySingle = new Camera2APISingleS20FE[CAM_IDS.length * N_BURST];
        for (int i = 0; i<camera2APISingleS20FEArraySingle.length; i++){
            camera2APISingleS20FEArraySingle[i] = new Camera2APISingleS20FE(context, mainHandler,
                    CAM_IDS[i%CAM_IDS.length], 1, cameraManager);
        }
    }

    private void prepareTexture() {

        for (int i = 0; i < camera2APISingleS20FEArray.length; i++) {
            camera2APISingleS20FEArray[i].prepareTexture(textureView.getSurfaceTexture());
        }

        for (int i = 0; i < camera2APISingleS20FEArraySingle.length; i++) {
            camera2APISingleS20FEArraySingle[i].prepareTexture(textureView.getSurfaceTexture());
        }

        openPreview();
    }

    private void openPreview() {
        camera2APISingleS20FEArray[previewIndex].openPreview();
    }

    public void init() {

        if (textureView.isAvailable()) {
            prepareTexture();
        }
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                Log.e(TAG, "onSurfaceTextureAvailable");
                prepareTexture();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                prepareTexture();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                for (int i = 0; i< camera2APISingleS20FEArray.length; i++){
                    camera2APISingleS20FEArray[i].closeCamera();
                }

                for (int i = 0; i< camera2APISingleS20FEArraySingle.length; i++){
                    camera2APISingleS20FEArraySingle[i].closeCamera();
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            }
        });
    }


    @Override
    public void captureBurst(OnCaptureBurstFinished onCaptureBurstFinished) {
        Date date = new Date();
        DocumentFile documentFolder = pickedDir.createDirectory(new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(date));
        DocumentFile documentFolder2 = pickedDir.createDirectory(new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(date)+"_single");

        ExecutorService executor = Executors.newSingleThreadExecutor();

        if (previewIndex != camera2APISingleS20FEArray.length-1){
            camera2APISingleS20FEArray[previewIndex].closeCamera();
        }


        executor.execute(() -> {

            ((Activity) context).runOnUiThread(() -> {
                sound.play(MediaActionSound.START_VIDEO_RECORDING);
            });


            //for (int i = 0; i< camera2APISingleS20FEArray.length; i++){
            for (int i = camera2APISingleS20FEArray.length-1; i>=0; i--){
                camera2APISingleS20FEArray[i].captureBurst(documentFolder);
                camera2APISingleS20FEArray[i].closeCamera();
            }


//            ((Activity) context).runOnUiThread(() -> {
//                sound.play(MediaActionSound.START_VIDEO_RECORDING);
//            });
//
//
//            for (int i = 0; i< camera2APISingleS20FEArraySingle.length; i++){
//                camera2APISingleS20FEArraySingle[i].captureBurst(documentFolder2);
//                camera2APISingleS20FEArraySingle[i].closeCamera();
//            }

            ((Activity) context).runOnUiThread(() -> {
                sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
                Toast.makeText(context, "Saving figures...", Toast.LENGTH_LONG).show();
            });


            for (int i = 0; i< camera2APISingleS20FEArray.length; i++){
                camera2APISingleS20FEArray[i].save();
            }
//
//            for (int i = 0; i< camera2APISingleS20FEArraySingle.length; i++){
//                camera2APISingleS20FEArraySingle[i].save();
//            }

            ((Activity) context).runOnUiThread(() -> {
                Toast.makeText(context, "Saved...", Toast.LENGTH_LONG).show();
            });
        });
    }

    public void setSaveDir(DocumentFile pickedDir) {
        this.pickedDir = pickedDir;
    }

    private void calibrate() {
        camera2APISingleS20FEArray[previewIndex].closeCamera();

        for (int i=previewIndex; i<camera2APISingleS20FEArraySingle.length; i+=CAM_IDS.length){
            camera2APISingleS20FEArraySingle[i].setLastResult(camera2APISingleS20FEArray[previewIndex].getLastResult());
        }

        previewIndex = (previewIndex + 1) % camera2APISingleS20FEArray.length;
        openPreview();
    }

    @Override
    public void save() {

    }

    @Override
    public void close() {

    }
}