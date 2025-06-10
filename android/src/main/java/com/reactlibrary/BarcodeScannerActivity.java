package com.reactlibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import android.graphics.Color;

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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private PreviewView previewView;
    private boolean scanned = false;
    private View laser;
    private View scanFrame;
    private Handler laserHandler = new Handler(Looper.getMainLooper());
    private boolean laserUp = true;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate overlay layout
        FrameLayout root = new FrameLayout(this);
        View overlay = getLayoutInflater().inflate(getResources().getIdentifier("overlay_scanner", "layout", getPackageName()), null);

        previewView = overlay.findViewById(getResources().getIdentifier("previewView", "id", getPackageName()));
        scanFrame = overlay.findViewById(getResources().getIdentifier("scan_frame", "id", getPackageName()));
        laser = overlay.findViewById(getResources().getIdentifier("laser", "id", getPackageName()));
        root.addView(overlay);
        setContentView(root);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        startLaserAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void startLaserAnimation() {
        laserHandler.post(new Runnable() {
            @Override
            public void run() {
                if (scanned) return; // stop animating if scanned
                int frameHeight = scanFrame.getHeight();
                int startY = scanFrame.getTop();
                int endY = scanFrame.getBottom() - laser.getHeight();
                int step = 7; // px per frame

                int currY = (int) laser.getY();
                if (laserUp) {
                    currY += step;
                    if (currY >= endY) {
                        laserUp = false;
                    }
                } else {
                    currY -= step;
                    if (currY <= startY) {
                        laserUp = true;
                    }
                }
                laser.setY(currY);
                laserHandler.postDelayed(this, 15);
            }
        });
    }

    private void setLaserColor(int color) {
        laser.setBackgroundColor(color);
        scanFrame.setBackgroundColor(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)));
    }

    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreviewAndAnalyzer(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    private void bindPreviewAndAnalyzer(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .build();

        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_PDF417)
                .build();
        BarcodeScanner scanner = BarcodeScanning.getClient(options);

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(getExecutor(), imageProxy -> {
            if (scanned) {
                imageProxy.close();
                return;
            }
            processImageProxy(scanner, imageProxy);
        });

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(
                (LifecycleOwner) this, cameraSelector, preview, imageAnalysis);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
    }

    private void processImageProxy(BarcodeScanner scanner, ImageProxy imageProxy) {
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image =
                InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!scanned && barcodes != null && !barcodes.isEmpty()) {
                        Barcode barcode = barcodes.get(0);
                        scanned = true;

                        setLaserColor(Color.RED);

                        // Vibración corta
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                vibrator.vibrate(100);
                            }
                        }

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("barcode", barcode.getRawValue());
                        setResult(Activity.RESULT_OK, resultIntent);

                        // Dale 400ms para mostrar láser rojo + vibrar
                        new Handler(getMainLooper()).postDelayed(this::finish, 400);
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
