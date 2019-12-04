/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mushroom_classifier.myapp;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;


import com.mushroom_classifier.myapp.env.ImageUtils;
import com.mushroom_classifier.myapp.env.Logger;
import com.mushroom_classifier.myapp.tflite.Classifier.Device;
import com.mushroom_classifier.myapp.tflite.Classifier.Model;
import com.mushroom_classifier.myapp.tflite.Classifier.Recognition;
import com.mushroom_classifier.myapp.LibraryHelper;
import com.mushroom_classifier.myapp.SelfDialog;

public abstract class CameraActivity extends AppCompatActivity
        implements OnImageAvailableListener,
        Camera.PreviewCallback,
        View.OnClickListener,
        View.OnLongClickListener,
        AdapterView.OnItemSelectedListener {
    private static final Logger LOGGER = new Logger();

    private static final int PERMISSIONS_REQUEST = 1;

    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    protected int previewWidth = 0;
    protected int previewHeight = 0;
    private Handler handler;
    private HandlerThread handlerThread;
    private boolean useCamera2API;
    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private LinearLayout bottomSheetLayout;
    private LinearLayout gestureLayout;
    private BottomSheetBehavior sheetBehavior;
    protected TextView recognitionTextView,
            recognition1TextView,
            recognition2TextView,
            recognition3TextView,
            recognition4TextView,
            recognitionValueTextView,
            recognition1ValueTextView,
            recognition2ValueTextView,
            recognition3ValueTextView,
            recognition4ValueTextView;
    protected  TextView[] recognitionTextViews;
    protected  TextView[] recognitionValueTextViews;
/*    protected TextView frameValueTextView,
            cropValueTextView,
            cameraResolutionTextView,
            rotationTextView,
            inferenceTimeTextView;*/
    protected ImageView bottomSheetArrowImageView;
    //private ImageView plusImageView, minusImageView;
    private Spinner modelSpinner;
    //private Spinner deviceSpinner;
    //private TextView threadsTextView;
    private Button startButton;

    private Model model = Model.QUANTIZED;
    private Device device = Device.CPU;
    private int numThreads = -1;
    private java.util.TreeMap<String, Float> observedMushrooms;
    private boolean isStart = false;
    private int frameCounter = 0;
    private LibraryHelper library;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        LOGGER.d("onCreate " + this);
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);
        // Toolbar toolbar = findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);
        // getSupportActionBar().setDisplayShowTitleEnabled(false);

        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission();
        }

        isStart = false;

        //threadsTextView = findViewById(R.id.threads);
        //plusImageView = findViewById(R.id.plus);
        //minusImageView = findViewById(R.id.minus);
        modelSpinner = findViewById(R.id.model_spinner);
        //deviceSpinner = findViewById(R.id.device_spinner);
        bottomSheetLayout = findViewById(R.id.bottom_sheet_layout);
        gestureLayout = findViewById(R.id.gesture_layout);
        sheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetArrowImageView = findViewById(R.id.bottom_sheet_arrow);
        startButton = findViewById(R.id.start_button);

        ViewTreeObserver vto = gestureLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                            gestureLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                        } else {
                            gestureLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                        //                int width = bottomSheetLayout.getMeasuredWidth();
                        int height = gestureLayout.getMeasuredHeight();

                        sheetBehavior.setPeekHeight(height);
                    }
                });
        sheetBehavior.setHideable(false);

        sheetBehavior.setBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        switch (newState) {
                            case BottomSheetBehavior.STATE_HIDDEN:
                                break;
                            case BottomSheetBehavior.STATE_EXPANDED:
                            {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_down);
                            }
                            break;
                            case BottomSheetBehavior.STATE_COLLAPSED:
                            {
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                            }
                            break;
                            case BottomSheetBehavior.STATE_DRAGGING:
                                break;
                            case BottomSheetBehavior.STATE_SETTLING:
                                bottomSheetArrowImageView.setImageResource(R.drawable.icn_chevron_up);
                                break;
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
                });

/*
        recognitionTextView = findViewById(R.id.detected_item);
        recognitionValueTextView = findViewById(R.id.detected_item_value);
        recognition1TextView = findViewById(R.id.detected_item1);
        recognition1ValueTextView = findViewById(R.id.detected_item1_value);
        recognition2TextView = findViewById(R.id.detected_item2);
        recognition2ValueTextView = findViewById(R.id.detected_item2_value);
        recognition3TextView = findViewById(R.id.detected_item3);
        recognition3ValueTextView = findViewById(R.id.detected_item3_value);
        recognition4TextView = findViewById(R.id.detected_item4);
        recognition4ValueTextView = findViewById(R.id.detected_item4_value);
*/
        recognitionTextViews = new TextView[5];
        recognitionValueTextViews = new TextView[5];
        recognitionTextViews[0] = findViewById(R.id.detected_item);
        recognitionValueTextViews[0] = findViewById(R.id.detected_item_value);
        recognitionTextViews[1] = findViewById(R.id.detected_item1);
        recognitionValueTextViews[1] = findViewById(R.id.detected_item1_value);
        recognitionTextViews[2] = findViewById(R.id.detected_item2);
        recognitionValueTextViews[2] = findViewById(R.id.detected_item2_value);
        recognitionTextViews[3] = findViewById(R.id.detected_item3);
        recognitionValueTextViews[3] = findViewById(R.id.detected_item3_value);
        recognitionTextViews[4] = findViewById(R.id.detected_item4);
        recognitionValueTextViews[4] = findViewById(R.id.detected_item4_value);

        for (int idx = 0; idx < 5; ++idx) {
            recognitionTextViews[idx].setOnLongClickListener(this);
        }

        //recognition1TextView.setOnLongClickListener(this);

        //frameValueTextView = findViewById(R.id.frame_info);
        //cropValueTextView = findViewById(R.id.crop_info);
        //cameraResolutionTextView = findViewById(R.id.view_info);
        //rotationTextView = findViewById(R.id.rotation_info);
        //inferenceTimeTextView = findViewById(R.id.inference_info);

        modelSpinner.setOnItemSelectedListener(this);
        //deviceSpinner.setOnItemSelectedListener(this);

        //plusImageView.setOnClickListener(this);
        //minusImageView.setOnClickListener(this);
        startButton.setOnClickListener(this);


        model = Model.valueOf(modelSpinner.getSelectedItem().toString().toUpperCase());
        //device = Device.valueOf(deviceSpinner.getSelectedItem().toString());

        library = new LibraryHelper(this);
        library.openDatabase();
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }


    /** Callback for android.hardware.Camera API */
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if (isProcessingFrame) {
            LOGGER.w("Dropping frame!");
            return;
        }

        try {
            // Initialize the storage bitmaps once when the resolution is known.
            if (rgbBytes == null) {
                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                previewHeight = previewSize.height;
                previewWidth = previewSize.width;
                rgbBytes = new int[previewWidth * previewHeight];
                onPreviewSizeChosen(new Size(previewSize.width, previewSize.height), 90);
            }
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes[0] = bytes;
        yRowStride = previewWidth;

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420SPToARGB8888(bytes, previewWidth, previewHeight, rgbBytes);
                    }
                };

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };
        processImage();
    }

    /** Callback for Camera2 API */
    @Override
    public void onImageAvailable(final ImageReader reader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }
        try {
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
            Trace.beginSection("imageAvailable");
            final Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

    @Override
    public synchronized void onStart() {
        LOGGER.d("onStart " + this);
        super.onStart();
    }

    @Override
    public synchronized void onResume() {
        LOGGER.d("onResume " + this);
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        LOGGER.d("onPause " + this);

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            LOGGER.e(e, "Exception!");
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop() {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy() {
        LOGGER.d("onDestroy " + this);
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode, final String[] permissions, final int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                setFragment();
            } else {
                requestPermission();
            }
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                        CameraActivity.this,
                        "Camera permission is required for this demo",
                        Toast.LENGTH_LONG)
                        .show();
            }
            requestPermissions(new String[] {PERMISSION_CAMERA}, PERMISSIONS_REQUEST);
        }
    }

    // Returns true if the device supports the required hardware level, or better.
    private boolean isHardwareLevelSupported(
            CameraCharacteristics characteristics, int requiredLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return requiredLevel == deviceLevel;
        }
        // deviceLevel is not LEGACY, can use numerical sort
        return requiredLevel <= deviceLevel;
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map =
                        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                // Fallback to camera1 API for internal cameras that don't have full support.
                // This should help with legacy situations where using the camera2 API causes
                // distorted or otherwise broken previews.
                useCamera2API =
                        (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                || isHardwareLevelSupported(
                                characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e(e, "Not allowed to access camera");
        }

        return null;
    }

    protected void setFragment() {
        String cameraId = chooseCamera();

        Fragment fragment;
        if (useCamera2API) {
            CameraConnectionFragment camera2Fragment =
                    CameraConnectionFragment.newInstance(
                            new CameraConnectionFragment.ConnectionCallback() {
                                @Override
                                public void onPreviewSizeChosen(final Size size, final int rotation) {
                                    previewHeight = size.getHeight();
                                    previewWidth = size.getWidth();
                                    CameraActivity.this.onPreviewSizeChosen(size, rotation);
                                }
                            },
                            this,
                            getLayoutId(),
                            getDesiredPreviewFrameSize());

            camera2Fragment.setCamera(cameraId);
            fragment = camera2Fragment;
        } else {
            fragment =
                    new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
        }

        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
    }

    protected void fillBytes(final Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    @UiThread void updateResultsInBottomSheet(List<Recognition> results) {
        if (results != null && results.size() >= 5) {
            for (int idx = 0; idx < 5; ++idx) {
                Recognition recognition = results.get(idx);
                if (recognition.getTitle() != null) recognitionTextViews[idx].setText(recognition.getTitle());
                if (recognition.getConfidence() != null) {
                    recognitionValueTextViews[idx].setText(
                            String.format("%d", (int)(100 * recognition.getConfidence())));
                }
                if (recognition.getTitle() != null && recognition.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition.getTitle()) ? observedMushrooms.get(recognition.getTitle()) : 0;
                    score += recognition.getConfidence();
                    observedMushrooms.put(recognition.getTitle(), score);
                }
            }
            /*
            Recognition recognition = results.get(0);
            if (recognition != null) {
                if (recognition.getTitle() != null) recognitionTextView.setText(recognition.getTitle());
                if (recognition.getConfidence() != null) {
                    recognitionValueTextView.setText(
                            String.format("%d", (int)(100 * recognition.getConfidence())));
                }
                if (recognition.getTitle() != null && recognition.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition.getTitle()) ? observedMushrooms.get(recognition.getTitle()) : 0;
                    score += recognition.getConfidence();
                    observedMushrooms.put(recognition.getTitle(), score);
                }
            }

            Recognition recognition1 = results.get(1);
            if (recognition1 != null) {
                if (recognition1.getTitle() != null) recognition1TextView.setText(recognition1.getTitle());
                if (recognition1.getConfidence() != null)
                    recognition1ValueTextView.setText(
                            String.format("%d", (int)(100 * recognition1.getConfidence())));
                if (recognition1.getTitle() != null && recognition1.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition1.getTitle()) ? observedMushrooms.get(recognition1.getTitle()) : 0;
                    score += recognition1.getConfidence();
                    observedMushrooms.put(recognition1.getTitle(), score);
                }
            }

            Recognition recognition2 = results.get(2);
            if (recognition2 != null) {
                if (recognition2.getTitle() != null) recognition2TextView.setText(recognition2.getTitle());
                if (recognition2.getConfidence() != null)
                    recognition2ValueTextView.setText(
                            String.format("%d", (int)(100 * recognition2.getConfidence())));
                if (recognition2.getTitle() != null && recognition2.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition2.getTitle()) ? observedMushrooms.get(recognition2.getTitle()) : 0;
                    score += recognition2.getConfidence();
                    observedMushrooms.put(recognition2.getTitle(), score);
                }
            }

            Recognition recognition3 = results.get(3);
            if (recognition3 != null) {
                if (recognition3.getTitle() != null) recognition3TextView.setText(recognition3.getTitle());
                if (recognition3.getConfidence() != null)
                    recognition3ValueTextView.setText(
                            String.format("%d", (int)(100 * recognition3.getConfidence())));
                if (recognition3.getTitle() != null && recognition3.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition3.getTitle()) ? observedMushrooms.get(recognition3.getTitle()) : 0;
                    score += recognition3.getConfidence();
                    observedMushrooms.put(recognition3.getTitle(), score);
                }
            }

            Recognition recognition4 = results.get(4);
            if (recognition4 != null) {
                if (recognition4.getTitle() != null)
                    recognition4TextView.setText(recognition4.getTitle());
                if (recognition4.getConfidence() != null)
                    recognition4ValueTextView.setText(
                            String.format("%d", (int)(100 * recognition4.getConfidence())));
                if (recognition4.getTitle() != null && recognition4.getConfidence() != null) {
                    float score = observedMushrooms.containsKey(recognition4.getTitle()) ? observedMushrooms.get(recognition4.getTitle()) : 0;
                    score += recognition4.getConfidence();
                    observedMushrooms.put(recognition4.getTitle(), score);
                }
            }
            */
        }
    }

    @UiThread
    protected void showResultsInBottomSheet(List<Recognition> results) {
         if (isStart) {
             updateResultsInBottomSheet(results);
         }
    }

    protected void showFrameInfo(String frameInfo) {
        // frameValueTextView.setText(frameInfo);
    }

    protected void showCropInfo(String cropInfo) {
        // cropValueTextView.setText(cropInfo);
    }

    protected void showCameraResolution(String cameraInfo) {
        // cameraResolutionTextView.setText(cameraInfo);
    }

    protected void showRotationInfo(String rotation) {
        // rotationTextView.setText(rotation);
    }

    protected void showInference(String inferenceTime) {
        // inferenceTimeTextView.setText(inferenceTime);
    }

    protected Model getModel() {
        return model;
    }

    private void setModel(Model model) {
        if (this.model != model) {
            LOGGER.d("Updating  model: " + model);
            this.model = model;
            onInferenceConfigurationChanged();
        }
    }

    protected Device getDevice() {
        return device;
    }

    private void setDevice(Device device) {

        if (this.device != device) {
            LOGGER.d("Updating  device: " + device);
            this.device = device;
            final boolean threadsEnabled = device == Device.CPU;
            //plusImageView.setEnabled(threadsEnabled);
            //minusImageView.setEnabled(threadsEnabled);
            //threadsTextView.setText(threadsEnabled ? String.valueOf(numThreads) : "N/A");
            onInferenceConfigurationChanged();
        }
    }

    protected int getNumThreads() {
        return numThreads;
    }

    private void setNumThreads(int numThreads) {
        if (this.numThreads != numThreads) {
            LOGGER.d("Updating  numThreads: " + numThreads);
            this.numThreads = numThreads;
            onInferenceConfigurationChanged();
        }
    }

    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void onInferenceConfigurationChanged();

    @Override
    public void onClick(View v) {
        /*
        if (v.getId() == R.id.plus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads >= 9) return;
            setNumThreads(++numThreads);
            threadsTextView.setText(String.valueOf(numThreads));
        } else if (v.getId() == R.id.minus) {
            String threads = threadsTextView.getText().toString().trim();
            int numThreads = Integer.parseInt(threads);
            if (numThreads == 1) {
                return;
            }
            setNumThreads(--numThreads);
            threadsTextView.setText(String.valueOf(numThreads));
        } else */
        if (v.getId() == R.id.start_button) {
            if (isStart) {  // stop is pressed
                isStart = false;
                startButton.setText(R.string.start);
                PriorityQueue<Recognition> pq =
                        new PriorityQueue<>(
                                5,
                                new Comparator<Recognition>() {
                                    @Override
                                    public int compare(Recognition lhs, Recognition rhs) {
                                        // Intentionally reversed to put high confidence at the head of the queue.
                                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                                    }
                                });

                for (Map.Entry<String, Float> entry : observedMushrooms.entrySet()) {
                    pq.add(new Recognition("" + entry.getKey(), entry.getKey(), entry.getValue(), null));
                }

                final ArrayList<Recognition> recognitions = new ArrayList<>();
                int recognitionsSize = Math.min(pq.size(), 5);
                for (int i = 0; i < recognitionsSize; ++i) {
                    recognitions.add(pq.poll());
                }
                updateResultsInBottomSheet(recognitions);
            } else {    // start is pressed
                isStart = true;
                observedMushrooms = new java.util.TreeMap<>();
                startButton.setText((R.string.stop));
            }

        }
    }

    private int getTextViewIdx(View v) {
        int idx = -1;
        switch (v.getId()) {
            case R.id.detected_item:{
                idx = 0;
                break;
            }
            case R.id.detected_item1:{
                idx = 1;
                break;
            }
            case R.id.detected_item2:{
                idx = 2;
                break;
            }
            case R.id.detected_item3:{
                idx = 3;
                break;
            }
            case R.id.detected_item4:{
                idx = 4;
                break;
            }
            default: {
                idx = -1;
                break;
            }
        }
        return idx;
    }

    @Override
    public boolean onLongClick(View v) {
        int textViewIdx = getTextViewIdx(v);
        String cat_name = recognitionTextViews[textViewIdx].getText().toString();
        CategoryInfo cat_info = library.gainData(cat_name);
        SelfDialog info_dialog = new SelfDialog(this);

        info_dialog.setTitle(cat_name);
        info_dialog.setMessage(cat_info.info);

        info_dialog.setCancelable(true);
        info_dialog.setReturnOnclickListener("Return", new SelfDialog.onYesOnclickListener() {
            @Override
            public void onYesClick() {
                info_dialog.dismiss();
            }
        });
        info_dialog.show();
        info_dialog.setImage(cat_info.image);
        return true;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        if (parent == modelSpinner) {
            setModel(Model.valueOf(parent.getItemAtPosition(pos).toString().toUpperCase()));
        }
/*        else if (parent == deviceSpinner) {
            setDevice(Device.valueOf(parent.getItemAtPosition(pos).toString()));
        }*/
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing.
    }
}
