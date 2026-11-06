package com.samsung.android.scan3d.camera;

import static com.samsung.android.scan3d.utils.Utils.calculateZoomRegion;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
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
import android.media.MediaActionSound;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.samsung.android.scan3d.utils.ProgressDialog;
import com.samsung.android.scan3d.utils.Utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

// 0.6 1.0 1.1 10.3 3.0

public class Camera2APIManager implements Camera2API {

    private static final String TAG = "Camera2APIManager";

    private final String CAM_ID = "0";

    private final int PRE_CAPTURES = 3;

    private final int MAX_IMAGES = 56;

    private int LENS_STAB = CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF;

//    private final String[] PHY_CAM_IDS = new String[]{"2", "5", "6", "7"};
//    private Float[] ZOOM_RATIOS_C = new Float[]{12f, 12.5f, 12f, null};
//    private final int[][] GROUP_CAMS = new int[][]{new int[]{0, 2, 3}, new int[]{1}};
//    private final int PREVIEW_PRE_CAPTURE = 3;

    private final String[] PHY_CAM_IDS = new String[]{"2", "6", "7"};
    private Float[] ZOOM_RATIOS_C = new Float[]{12f, 12f, null};
    private final int[][] GROUP_CAMS = new int[][]{new int[]{0, 1, 2}};
    private final int PREVIEW_PRE_CAPTURE = 2;
    private final int IMAGE_FORMAT = ImageFormat.RAW_SENSOR;

    private final int N_REQUESTS = MAX_IMAGES * GROUP_CAMS.length;

    private CameraManager cameraManager;

    private CameraDevice cameraDevice;

    private CameraCharacteristics characteristics;

    private CameraCharacteristics[] physicalCharacteristics;

    private ImageReader[] physicalImageReaders;

    private Rect[] zoomRects;

    private Size imageDimension;

    private CaptureResult[] calibratedResult;

    private AtomicInteger requestIndex;

    private CameraCaptureSession session;

    private TextureView textureView;

    private Context context;

    private Date captureDate;

    private Handler mainHandler;

    private MediaActionSound sound;

    private TotalCaptureResult[] totalCaptureResult;

    private DocumentFile pickedDir;

    private Surface textureSurface;

    private int previewIndex;

    private Utils.WaitLock waitFocusExposure = new Utils.WaitLock(10);

    private OnCaptureBurstFinished onCaptureBurstFinished;

    private ProgressDialog progressDialog;

    private boolean playedSound = false;

    public Camera2APIManager(Context context, TextureView textureView) {

        this.context = context;
        this.textureView = textureView;

        totalCaptureResult = new TotalCaptureResult[N_REQUESTS];
        requestIndex = new AtomicInteger(0);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        sound = new MediaActionSound();
        mainHandler = new Handler(Looper.getMainLooper());
        previewIndex = PREVIEW_PRE_CAPTURE;
        calibratedResult = new CaptureResult[PHY_CAM_IDS.length];
        zoomRects = new Rect[PHY_CAM_IDS.length];
        progressDialog = new ProgressDialog(context, N_REQUESTS);

        try {

            characteristics = cameraManager.getCameraCharacteristics(CAM_ID);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            physicalImageReaders = new ImageReader[PHY_CAM_IDS.length];
            physicalCharacteristics = new CameraCharacteristics[PHY_CAM_IDS.length];


            for (int i = 0; i < PHY_CAM_IDS.length; i++) {
                if (ZOOM_RATIOS_C[i]!=null){
                    zoomRects[i] = calculateZoomRegion(characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE),
                            ZOOM_RATIOS_C[i], 0, 0);
                } else {
                    zoomRects[i] = null;
                }

                physicalCharacteristics[i] = cameraManager.getCameraCharacteristics(PHY_CAM_IDS[i]);
            }


        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void recreateReaders() throws CameraAccessException {
        for (ImageReader physicalImageReader : physicalImageReaders) {
            if (physicalImageReader != null)
                physicalImageReader.close();
        }

        for (int i = 0; i < PHY_CAM_IDS.length; i++) {
            StreamConfigurationMap map = physicalCharacteristics[i].get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            Size physicalSize = Collections.max(Arrays.asList(map.getOutputSizes(IMAGE_FORMAT)), new Utils.CompareSizesByArea());
            physicalImageReaders[i] = ImageReader.newInstance(physicalSize.getWidth(),
                    physicalSize.getHeight(), IMAGE_FORMAT,
                    MAX_IMAGES*Utils.countOccurrences(GROUP_CAMS, i));
        }
    }

    @Override
    public void init() {

        if (textureView.isAvailable()) {
            openCameras();
        }
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                openCameras();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                close();
                openCameras();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                close();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
//                close();
//                openCameras();
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void openCameras() {
        try {
            recreateReaders();
            if (cameraDevice==null) {
                cameraManager.openCamera(CAM_ID, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        Log.i(TAG, "onOpened");
                        cameraDevice = camera;
                        createSession();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        Log.i(TAG, "cameraDevice disconnected");
                        camera.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        Log.e(TAG, "cameraDevice onError " + error);
                        camera.close();
                        cameraDevice = null;
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }


    final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);

            String physicalCamId = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
            int index = Arrays.asList(PHY_CAM_IDS).indexOf(physicalCamId);
            calibratedResult[index] = result;

            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

            boolean exposureConverged = aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED;
            boolean focusConverged = afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
            if (physicalCamId.equals("2")){
                focusConverged = true;
            }

            if (exposureConverged && focusConverged){
                waitFocusExposure.notifySignal();
            }
        }
    };


    private CaptureRequest.Builder createPreviewRequestBuilder() throws CameraAccessException {
        CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW, new HashSet<>(Arrays.asList(new String[]{PHY_CAM_IDS[previewIndex]})));
        builder.addTarget(textureSurface);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, LENS_STAB);

        if (zoomRects[previewIndex]!=null) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, zoomRects[previewIndex]);
        }

        return builder;
    }

    private void createSession(){

        try {

            recreateReaders();
            List<OutputConfiguration> outputConfigs = new ArrayList<>();

            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            textureSurface = new Surface(texture);
            OutputConfiguration previewConfig = new OutputConfiguration(textureSurface);
            previewConfig.setPhysicalCameraId(PHY_CAM_IDS[previewIndex]);
            outputConfigs.add(previewConfig);

            for (int i = 0; i< PHY_CAM_IDS.length; i++){
                OutputConfiguration physicalConfig = new OutputConfiguration(physicalImageReaders[i].getSurface());
                physicalConfig.setPhysicalCameraId(PHY_CAM_IDS[i]);
                outputConfigs.add(physicalConfig);
            }

            CaptureRequest.Builder builder = createPreviewRequestBuilder();

            SessionConfiguration sessionConfig = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                    outputConfigs, Executors.newFixedThreadPool(10), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        Camera2APIManager.this.session = session;
                        session.setRepeatingRequest(builder.build(), captureCallback, mainHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            });

            cameraDevice.createCaptureSession(sessionConfig);

        } catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private void callCaptureBurstFinished(boolean success, String message){
        ((Activity) context).runOnUiThread(() -> {
            onCaptureBurstFinished.onCaptureBurstFinished(success, message);
        });
    }

    final CameraCaptureSession.CaptureCallback captureCallbackBurst = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (Objects.equals(request.getTag(), "CAPTURE") && !playedSound){
                playedSound = true;
                sound.play(MediaActionSound.START_VIDEO_RECORDING);
            }
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            if (Objects.equals(request.getTag(), "CAPTURE")){
                Log.i(TAG, "Capture completed");
                int i = requestIndex.getAndIncrement();
                totalCaptureResult[i] = result;
            } else {

                for (ImageReader physicalImageReader : physicalImageReaders) {
                    Image image = physicalImageReader.acquireNextImage();
                    if (image!=null)
                        image.close();
                    Log.i(TAG, "Pre Capture image acquired "+image);
                }
                Log.i(TAG, "Pre Capture");

            }
        }
        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            Log.i(TAG, "Capture sequence completed");
            sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
            callCaptureBurstFinished(true, "");
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.e(TAG, "Capture failed");
            callCaptureBurstFinished(false, "Capture failed: "+failure.getReason());
        }
    };

    public void save(){

        Executors.newSingleThreadExecutor().execute(() -> {
            DocumentFile documentFolder = pickedDir.createDirectory(new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(captureDate));

            ((Activity) context).runOnUiThread(() -> {
                progressDialog.show();
            });

            for (int j = 0; j < N_REQUESTS; j++) {

                int finalJ = j;
                ((Activity) context).runOnUiThread(() -> {
                    progressDialog.setProgress(finalJ);
                });

                TotalCaptureResult result = totalCaptureResult[j];

                Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                long captureTimeMillis = SystemClock.elapsedRealtime() - timestamp / 1000000;
                Date captureDate = new Date(System.currentTimeMillis() - captureTimeMillis);
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(captureDate);

                int[] selectedGroup = GROUP_CAMS[j % GROUP_CAMS.length];

                for (int i : selectedGroup) {
                    ImageReader physicalImageReader = physicalImageReaders[i];
                    try (Image image = physicalImageReader.acquireNextImage()) {

                        if (image != null) {

                            if (physicalImageReader.getImageFormat() == ImageFormat.RAW_SENSOR) {
                                DocumentFile documentFile = documentFolder.createFile("image/dng", "IMG_" + timeStamp + "_" + PHY_CAM_IDS[i] + "_" + j + ".dng");
                                try (OutputStream output = context.getContentResolver().openOutputStream(documentFile.getUri())) {
                                    DngCreator dngCreator = new DngCreator(physicalCharacteristics[i], Objects.requireNonNull(result.getPhysicalCameraTotalResults().get(PHY_CAM_IDS[i])));
                                    dngCreator.writeImage(output, image);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {

                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[buffer.capacity()];
                                buffer.get(bytes);
                                DocumentFile documentFile = documentFolder.createFile("image/png", "IMG_" + timeStamp + "_" + PHY_CAM_IDS[i] + "_" + j + ".png");
                                try (OutputStream output = context.getContentResolver().openOutputStream(documentFile.getUri())) {
                                    output.write(bytes);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                            }
                        }
                    }
                }
            }

            ((Activity) context).runOnUiThread(() -> {
                progressDialog.dismiss();
                Camera2APIManager.this.close();
                Camera2APIManager.this.init();
            });


        });
    }


    //

    @Override
    public void captureBurst(OnCaptureBurstFinished onCaptureBurstFinished){

        this.onCaptureBurstFinished = onCaptureBurstFinished;
        playedSound = false;

        this.calibrate(success -> {
            if (!success){
                callCaptureBurstFinished(false, "Calibration failed");
            }
            else {
                try {
                    requestIndex.set(0);
                    captureDate = new Date();

                    CaptureRequest.Builder[] requestBuilders = new CaptureRequest.Builder[GROUP_CAMS.length];
                    for (int i = 0; i < requestBuilders.length; i++) {
                        requestBuilders[i] = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE,
                                Utils.convertToSet(Utils.getIndexedValues(PHY_CAM_IDS, GROUP_CAMS[i])));

                        requestBuilders[i].set(CaptureRequest.CONTROL_AE_LOCK, true);
                        requestBuilders[i].set(CaptureRequest.CONTROL_AWB_LOCK, true);
                        requestBuilders[i].set(CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY);
                        requestBuilders[i].set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, LENS_STAB);


                        for (int camIndex: GROUP_CAMS[i]) {
                            requestBuilders[i].addTarget(physicalImageReaders[camIndex].getSurface());
                            if (camIndex==PREVIEW_PRE_CAPTURE){
                                requestBuilders[i].addTarget(textureSurface);
                                if (zoomRects[camIndex]!=null) {
                                    requestBuilders[i].setPhysicalCameraKey(CaptureRequest.SCALER_CROP_REGION,
                                            zoomRects[camIndex], PHY_CAM_IDS[camIndex]);
                                }
                            }

                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF, PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.LENS_FOCUS_DISTANCE, calibratedResult[camIndex].get(CaptureResult.LENS_FOCUS_DISTANCE), PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF, PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                                    calibratedResult[camIndex].get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION), PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AE_LOCK, true,  PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF, PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.CONTROL_AWB_LOCK, true,  PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.SENSOR_SENSITIVITY, calibratedResult[camIndex].get(CaptureResult.SENSOR_SENSITIVITY), PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.SENSOR_EXPOSURE_TIME, calibratedResult[camIndex].get(CaptureResult.SENSOR_EXPOSURE_TIME), PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.DISTORTION_CORRECTION_MODE, CameraMetadata.DISTORTION_CORRECTION_MODE_HIGH_QUALITY, PHY_CAM_IDS[camIndex]);
                            requestBuilders[i].setPhysicalCameraKey(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, LENS_STAB, PHY_CAM_IDS[camIndex]);

                        }
                    }

                    List<CaptureRequest> burstList = new ArrayList<>();
                    for (int i = 0; i < PRE_CAPTURES + MAX_IMAGES; i++) {
                        for (int j = 0; j < requestBuilders.length; j++) {
                            requestBuilders[j].setTag(i < PRE_CAPTURES? "PRE_CAPTURE" : "CAPTURE");
                            burstList.add(requestBuilders[j].build());
                        }
                    }

                    session.captureBurst(burstList, captureCallbackBurst, mainHandler);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        });





    }

    @Override
    public void setSaveDir(DocumentFile pickedDir) {
        this.pickedDir = pickedDir;
    }

    @Override
    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void calibrate(OnCalibrateResult onCalibrateResult) {

        Arrays.fill(calibratedResult, null);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            boolean calibrated = true;

            try {


                for (int i=0; i<PHY_CAM_IDS.length; i++){
                    try {
                        session.stopRepeating();
                        session.close();
                        session = null;
                        previewIndex = i;
                        createSession();
                        Thread.sleep(1000);
                        waitFocusExposure.waitSignal();
                    } catch (CameraAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                session.stopRepeating();


//                for (int i=0; i<calibratedResult.length; i++) {
//                    CaptureResult result = calibratedResult[i];
//                    if (result == null) {
//                        calibrated = false;
//                    } else {
//
//                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
//
//                        boolean exposureConverged = aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED;
//                        boolean focusConverged = afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED;
//                        if (PHY_CAM_IDS[i].equals("2")){
//                            focusConverged = true;
//                        }
//
//                        if ((!exposureConverged) || (!focusConverged)){
//                            calibrated = false;
//                        }
//                    }
//                }

            } catch (Exception e) {
                Log.e(TAG, "Error: ", e);
                calibrated = false;
            } finally {
                previewIndex = PREVIEW_PRE_CAPTURE;
                createSession();
            }

            onCalibrateResult.onCalibrateResult(calibrated);

        });
        executor.shutdown();
    }

}