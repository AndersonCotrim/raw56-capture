package com.samsung.android.scan3d.camera.s20fe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.samsung.android.scan3d.utils.Utils;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Camera2APISingleS20FE {

    interface OnCameraReadyListener {
        void onCameraReady();
    }

    interface OnSessionReadyListener {
        void onSessionReady();
    }

    private static final String TAG = "Camera2APISingle";

    private Context context;

    private Handler mainHandler;

    private String camId;

    private int nBusrst;

    private CameraManager cameraManager;

    private CameraDevice cameraDevice;

    private CameraCharacteristics characteristics;

    private ImageReader imageReader;

    private CameraCaptureSession session;

    private CaptureResult lastResult;

    private AtomicInteger burstId;

    private DocumentFile documentFolder;

    private Size imageDimension;

    private TotalCaptureResult[] totalCaptureResult;

    private Date[] captureDates;

    private Surface textureSurface;

    private List<OutputConfiguration> outputConfigsPrepared;

    private final ReentrantLock lock =  new ReentrantLock();
    private final Condition condition =  lock.newCondition();

    private final float zoomRatio;

    public Camera2APISingleS20FE(Context context, Handler mainHandler,
                                 String camId, int nBusrst,
                                 CameraManager cameraManager) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.camId = camId;
        this.nBusrst = nBusrst;
        this.cameraManager = cameraManager;
        this.burstId = new AtomicInteger(0);
        this.totalCaptureResult = new TotalCaptureResult[nBusrst];
        this.captureDates = new Date[nBusrst];

        if (camId.equals("2")) {
            zoomRatio = 5.8f;
        } else if(camId.equals("0")) {
            zoomRatio = 3.9f;
        } else {
            zoomRatio = 1.0f;
        }


        try {
            characteristics = cameraManager.getCameraCharacteristics(camId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size logicalSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)), new Utils.CompareSizesByArea());
            imageReader = ImageReader.newInstance(logicalSize.getWidth(), logicalSize.getHeight(), ImageFormat.RAW_SENSOR, nBusrst);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }

    }

    private void notifyCaptureCompleted(){
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    private void waitCaptureCompleted() throws InterruptedException {
        lock.lock();
        try {
            if (!condition.await(10, TimeUnit.SECONDS)) {
                throw new InterruptedException("Timeout");
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressLint("MissingPermission")
    public void openCamera(OnCameraReadyListener onCameraReadyListener) {
        if (cameraDevice!=null){
            onCameraReadyListener.onCameraReady();
        } else {
            try {
                cameraManager.openCamera(camId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.i(TAG, "onOpened " + camId);
                        cameraDevice = camera;
                        onCameraReadyListener.onCameraReady();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.i(TAG, "onDisconnected " + camId);
                        cameraDevice = camera;
                        closeCamera();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.i(TAG, "onError " + camId + " " + error);
                        cameraDevice = camera;
                        closeCamera();
                    }
                }, mainHandler);

            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void openSession(OnSessionReadyListener onSessionReadyListener) {
        if (session!=null && session.getDevice().equals(cameraDevice)){
            onSessionReadyListener.onSessionReady();
        } else {
            try {
                SessionConfiguration sessionConfig = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                        outputConfigsPrepared, Executors.newSingleThreadExecutor(), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        Camera2APISingleS20FE.this.session = session;
                        onSessionReadyListener.onSessionReady();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.i(TAG, "onConfigureFailed " + camId);
                        openSession(onSessionReadyListener);
                    }
                });
                cameraDevice.createCaptureSession(sessionConfig);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void createSessionAndPreviewRequest() {

        try {

            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(textureSurface);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);

//            if (camId.equals("0")){
//                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
//                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 10f);
//            }

            final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    lastResult = result;

//                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
//                    Log.e(TAG, "Capture completed " + camId + " " + afState + " " + result.get(CaptureResult.LENS_FOCUS_DISTANCE));
                }
            };

            openSession(() -> {
                try {
                    session.setRepeatingRequest(builder.build(), captureCallback, mainHandler);
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            });

        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }


    }

    private void createCaptureBurstRequest(){
        CaptureRequest.Builder requestBuilder;
        try {

            requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            requestBuilder.addTarget(imageReader.getSurface());
            requestBuilder.addTarget(textureSurface);

            requestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomRatio);

            // Set the parameters from the previous capture
//            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, lastResult.get(CaptureRequest.CONTROL_AF_MODE));
//            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, lastResult.get(CaptureRequest.CONTROL_AE_MODE));
//            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, lastResult.get(CaptureRequest.CONTROL_AWB_MODE));

            // Lock the focus, ISO and exposure time to the values of the last frame
            //requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, lastResult.get(CaptureResult.LENS_FOCUS_DISTANCE));
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            requestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, lastResult.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION));
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
            requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, lastResult.get(CaptureResult.SENSOR_SENSITIVITY));
            requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, lastResult.get(CaptureResult.SENSOR_EXPOSURE_TIME));

            List<CaptureRequest> burstList = new ArrayList<>();
            for (int i = 0; i < nBusrst; i++)
                burstList.add(requestBuilder.build());

            openSession(() -> {
                try {
                    session.captureBurst(burstList, captureBurstCallback, mainHandler);
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private final CameraCaptureSession.CaptureCallback captureBurstCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            int i = burstId.getAndIncrement();
            totalCaptureResult[i] = result;
            captureDates[i] = new Date();

            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            Log.e(TAG, "Capture completed " + i + " " + camId + " " + afState + " " + result.get(CaptureResult.LENS_FOCUS_DISTANCE));
        }
        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            Log.i(TAG, "Capture sequence completed");
            notifyCaptureCompleted();

        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.e(TAG, "Capture failed");
        }
    };

    public void save(){
        final int burstSize = burstId.get();

        for (int j = 0; j < burstSize; j++) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(captureDates[j]);
            TotalCaptureResult result = totalCaptureResult[j];

            try (Image image = imageReader.acquireNextImage()) {

                DocumentFile documentFile = documentFolder.createFile("image/dng", "IMG_" + timeStamp + "_"  + camId + "_" + j + ".dng");

                try (OutputStream output = context.getContentResolver().openOutputStream(documentFile.getUri())) {
                    DngCreator dngCreator = new DngCreator(characteristics, result);
                    dngCreator.writeImage(output, image);
                } catch (Exception e) {
                    new RuntimeException(e);
                }

            }
        }

    }

    public void prepareTexture(SurfaceTexture texture){

        assert texture != null;
        texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
        textureSurface = new Surface(texture);

        outputConfigsPrepared = new ArrayList<>();
        outputConfigsPrepared.add(new OutputConfiguration(imageReader.getSurface()));
        outputConfigsPrepared.add(new OutputConfiguration(textureSurface));
    }

    public void openPreview() {
        openCamera(this::createSessionAndPreviewRequest);
    }

    public void captureBurst(DocumentFile documentFolder)  {
        Log.i(TAG, "captureBurst " + camId);
        this.documentFolder = documentFolder;
        this.burstId.set(0);
        openCamera(this::createCaptureBurstRequest);

        try {
            waitCaptureCompleted();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeCamera(){
        if (session != null) {
            session.close();
            session = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    public CaptureResult getLastResult() {
        return lastResult;
    }

    public void setLastResult(CaptureResult lastResult) {
        Log.i(TAG, "setLastResult " + camId + " " + lastResult.get(CaptureResult.LENS_FOCUS_DISTANCE));
        this.lastResult = lastResult;
    }
}
