package com.reactlibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.*;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 10;
    /** Número de lecturas antes de decidir el código definitivo */
    private static final int MAX_READINGS     = 15;
    /** Cuántos frames pueden “fallar” sin alterar el majority vote */
    private static final int GLITCH_TOLERANCE = 2;

    private PreviewView previewView;
    private OverlayView overlayView;
    private FrameLayout scanFrame;
    private ProgressBar progressBar;
    private TextView feedbackText;
    private Vibrator vibrator;
    private CameraControl cameraControl;

    private final Handler laserHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Deque<String>> barcodeHistories = new HashMap<>();
    private boolean scanned = false;

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
        progressBar  = findViewById(R.id.progressBar);
        feedbackText = findViewById(R.id.feedbackText);
        vibrator     = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        progressBar.setMax(MAX_READINGS);
        progressBar.setProgress(0);

        setupFlashButton();
        setupCloseButton();
        setupFocusOnTap();

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

    private void setupFlashButton() {
        ImageButton flashButton = findViewById(R.id.flash_button);
        flashButton.setOnClickListener(v -> {
            if (cameraControl == null) return;
            boolean enable = !Boolean.TRUE.equals(flashButton.getTag());
            cameraControl.enableTorch(enable);
            flashButton.setTag(enable);
            flashButton.setImageResource(
                enable ? R.drawable.ic_flash_off : R.drawable.ic_flash_on
            );
        });
    }

    private void setupCloseButton() {
        findViewById(R.id.close_button)
            .setOnClickListener(v -> finish());
    }

    private void setupFocusOnTap() {
        previewView.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && cameraControl != null) {
                MeteringPoint point = previewView.getMeteringPointFactory()
                    .createPoint(ev.getX(), ev.getY());
                cameraControl.startFocusAndMetering(
                    new FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()
                );
            }
            return false;
        });
    }

    private void setAdaptiveFrame() {
        int w = Resources.getSystem().getDisplayMetrics().widthPixels;
        int h = Resources.getSystem().getDisplayMetrics().heightPixels;
        float frameW = w * 0.8f;
        float frameH = frameW * 0.4f;
        float left   = (w - frameW) / 2f;
        float top    = (h - frameH) / 2f;

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
                if (scanned) return;
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

        ImageAnalysis analysis = new ImageAnalysis.Builder()
            .setTargetResolution(new Size(1920, 1080))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();
        analysis.setAnalyzer(getExecutor(), this::processImageProxy);

        provider.unbindAll();
        Camera camera = provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        );
        cameraControl = camera.getCameraControl();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Enfoque inicial al centro
        previewView.postDelayed(() -> {
            MeteringPoint center = previewView.getMeteringPointFactory()
                .createPoint(previewView.getWidth()/2f, previewView.getHeight()/2f);
            cameraControl.startFocusAndMetering(
                new FocusMeteringAction.Builder(center)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
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
             for (Barcode barcode : barcodes) {
                 String raw = barcode.getRawValue();
                 if (raw == null) continue;  // aceptamos cualquier longitud
                 Deque<String> history = barcodeHistories
                     .computeIfAbsent("pdf417", k -> new ArrayDeque<>());
                 history.addLast(raw);
                 if (history.size() > MAX_READINGS) {
                     history.removeFirst();
                 }
                 String candidate = buildMajorityString(history);
                 progressBar.setProgress(history.size());
                 feedbackText.setText("Leyendo: " + candidate);
                 if (history.size() >= MAX_READINGS) {
                     onBarcodeScanned(candidate);
                     break;
                 }
             }
         })
         .addOnFailureListener(Throwable::printStackTrace)
         .addOnCompleteListener(t -> imageProxy.close());
    }

    private String buildMajorityString(Deque<String> history) {
        int len = history.peekFirst().length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            Map<Character, Integer> count = new HashMap<>();
            for (String s : history) {
                if (i < s.length()) {
                    char c = s.charAt(i);
                    count.put(c, count.getOrDefault(c, 0) + 1);
                }
            }
            char best = history.peekLast().charAt(i);
            for (Map.Entry<Character,Integer> e : count.entrySet()) {
                if (e.getValue() > history.size() - GLITCH_TOLERANCE) {
                    best = e.getKey();
                    break;
                }
            }
            sb.append(best);
        }
        return sb.toString();
    }

    private void onBarcodeScanned(String code) {
        scanned = true;
        // vibración fuerte
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            );
        } else {
            vibrator.vibrate(300);
        }
        Toast.makeText(this, "Código escaneado", Toast.LENGTH_SHORT).show();

        // Devuelvo el resultado a la actividad padre
        Intent data = new Intent();
        data.putExtra("scanned_code", code);
        setResult(Activity.RESULT_OK, data);
        finish();
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
