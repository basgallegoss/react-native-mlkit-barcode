package com.reactlibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.*;
import android.util.Size;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.*;
import java.util.concurrent.Executor;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final int PDF417_MIN_LENGTH        = 80;
    private static final int PDF417_MAX_LENGTH        = 150;
    private static final int MAX_READINGS             = 30;
    private static final int GLITCH_TOLERANCE         = 2;

    private PreviewView previewView;
    private OverlayView overlayView;
    private FrameLayout scanFrame;
    private View laser;
    private ProgressBar progressBar;
    private TextView feedbackText;

    private Vibrator vibrator;
    private CameraControl cameraControl;
    private final Handler laserHandler = new Handler(Looper.getMainLooper());
    private final Map<String, List<String>> barcodeHistories = new HashMap<>();
    private boolean scanned = false;
    private long lastReadingTimestamp = 0;
    private boolean flashEnabled = false;

    private float laserStepPx;
    private long  laserDelayMs;
    private float laserPaddingPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        laserStepPx    = dpToPx(4f);
        laserDelayMs   = 12;
        laserPaddingPx = dpToPx(16f);

        setContentView(R.layout.overlay_scanner);

        previewView  = findViewById(R.id.previewView);
        overlayView  = findViewById(R.id.overlayView);
        scanFrame    = findViewById(R.id.scan_frame);
        laser        = findViewById(R.id.laser);
        progressBar  = findViewById(R.id.progressBar);
        feedbackText = findViewById(R.id.feedbackText);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        ImageButton flashButton = findViewById(R.id.flash_button);
        if (flashButton != null) {
            flashButton.setOnClickListener(v -> {
                flashEnabled = !flashEnabled;
                if (cameraControl != null) {
                    cameraControl.enableTorch(flashEnabled);
                    flashButton.setImageResource(
                        flashEnabled ? R.drawable.ic_flash_off : R.drawable.ic_flash_on
                    );
                }
            });
        }

        ImageButton closeButton = findViewById(R.id.close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> finish());
        }

        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && cameraControl != null) {
                MeteringPointFactory factory = previewView.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                cameraControl.startFocusAndMetering(
                    new FocusMeteringAction.Builder(point).build()
                );
            }
            return false;
        });

        scanFrame.post(this::setAdaptiveFrame);
        startLaserAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{ Manifest.permission.CAMERA },
                CAMERA_PERMISSION_REQUEST
            );
        }
    }

    private void setAdaptiveFrame() {
        int w = Resources.getSystem().getDisplayMetrics().widthPixels;
        int h = Resources.getSystem().getDisplayMetrics().heightPixels;
        float frameW = w * 0.8f;
        float frameH = frameW * 0.4f;
        float left = (w - frameW) / 2f;
        float top  = (h - frameH) / 2f;

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
            (int) frameW, (int) frameH
        );
        p.leftMargin = (int) left;
        p.topMargin  = (int) top;
        scanFrame.setLayoutParams(p);

        overlayView.setFrame(new RectF(left, top, left + frameW, top + frameH));
    }

    private void startLaserAnimation() {
        laserHandler.post(new Runnable() {
            float y = 0;
            boolean down = true;
            @Override
            public void run() {
                if (scanned || overlayView == null) return;
                RectF frame = overlayView.getFrameRect();
                if (frame == null) {
                    laserHandler.postDelayed(this, laserDelayMs);
                    return;
                }
                float minY = frame.top + laserPaddingPx;
                float maxY = frame.bottom - laserPaddingPx;
                if (y == 0) y = minY;
                y += down ? laserStepPx : -laserStepPx;
                if (y >= maxY) down = false;
                if (y <= minY) down = true;
                overlayView.setLaserPos(y);
                laserHandler.postDelayed(this, laserDelayMs);
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                bindPreviewAndAnalyzer(future.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private void bindPreviewAndAnalyzer(@NonNull ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder()
            .setTargetResolution(new Size(1920, 1080))
            .build();

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
        BarcodeScanner scanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build()
        );

        ImageAnalysis analysis = new ImageAnalysis.Builder()
            .setTargetResolution(new Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();
        analysis.setAnalyzer(getExecutor(), this::processImageProxy);

        provider.unbindAll();
        Camera camera = provider.bindToLifecycle(
            (LifecycleOwner) this,
            selector,
            preview,
            analysis
        );
        cameraControl = camera.getCameraControl();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        previewView.postDelayed(() -> {
            MeteringPointFactory factory = previewView.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(
                previewView.getWidth()/2f,
                previewView.getHeight()/2f
            );
            cameraControl.startFocusAndMetering(
                new FocusMeteringAction.Builder(point).build()
            );
        }, 500);
    }

    private void processImageProxy(ImageProxy imageProxy) {
        if (scanned || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage img = InputImage.fromMediaImage(
            imageProxy.getImage(),
            imageProxy.getImageInfo().getRotationDegrees()
        );
        BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build()
        ).process(img)
         .addOnSuccessListener(barcodes -> {
             // lógica de majority-vote aquí
         })
         .addOnFailureListener(Throwable::printStackTrace)
         .addOnCompleteListener(t -> imageProxy.close());
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this,
                    "Permiso de cámara denegado",
                    Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}
