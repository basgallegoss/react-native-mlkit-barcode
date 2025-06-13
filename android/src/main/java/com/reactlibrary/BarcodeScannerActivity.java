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

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final int PDF417_MIN_LENGTH = 80;
    private static final int PDF417_MAX_LENGTH = 150;
    private static final int MAX_READINGS = 30;
    private static final int GLITCH_TOLERANCE = 2;

    private PreviewView previewView;
    private boolean scanned = false;
    private View laser;
    private View scanFrame;
    private Handler laserHandler = new Handler(Looper.getMainLooper());
    private boolean laserUp = true;
    private Vibrator vibrator;
    private CameraControl cameraControl;
    private boolean flashEnabled = false;
    private ProgressBar progressBar;
    private TextView feedbackText;

    private Map<String, List<String>> barcodeHistories = new HashMap<>();
    private long lastReadingTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        View overlay = getLayoutInflater().inflate(getResources().getIdentifier("overlay_scanner", "layout", getPackageName()), null);

        previewView = overlay.findViewById(getResources().getIdentifier("previewView", "id", getPackageName()));
        scanFrame = overlay.findViewById(getResources().getIdentifier("scan_frame", "id", getPackageName()));
        laser = overlay.findViewById(getResources().getIdentifier("laser", "id", getPackageName()));
        Button flashButton = overlay.findViewById(getResources().getIdentifier("flash_button", "id", getPackageName()));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 18);
        params.setMargins(60, 80, 60, 0);
        progressBar.setLayoutParams(params);
        progressBar.setMax(100);

        feedbackText = new TextView(this);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        textParams.topMargin = 120;
        feedbackText.setLayoutParams(textParams);
        feedbackText.setTextColor(Color.WHITE);
        feedbackText.setTextSize(16);

        ((FrameLayout) overlay).addView(progressBar);
        ((FrameLayout) overlay).addView(feedbackText);
        root.addView(overlay);
        setContentView(root);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        flashButton.setOnClickListener(v -> {
            flashEnabled = !flashEnabled;
            if (cameraControl != null) {
                cameraControl.enableTorch(flashEnabled);
                flashButton.setText(flashEnabled ? "Flash ON" : "Flash OFF");
            }
        });

        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && cameraControl != null) {
                MeteringPointFactory factory = previewView.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
                cameraControl.startFocusAndMetering(action);
            }
            return false;
        });

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
                if (scanned) return;
                int frameHeight = scanFrame.getHeight();
                int startY = scanFrame.getTop();
                int endY = scanFrame.getBottom() - laser.getHeight();
                int step = 10;
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
        scanFrame.setBackgroundColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)));
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
        Camera camera = cameraProvider.bindToLifecycle(
                (LifecycleOwner) this, cameraSelector, preview, imageAnalysis);

        cameraControl = camera.getCameraControl();
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
                    if (barcodes != null && !barcodes.isEmpty()) {
                        long now = System.currentTimeMillis();
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && raw.length() >= 10) {
                                String key = findSimilarKey(raw);
                                if (!barcodeHistories.containsKey(key))
                                    barcodeHistories.put(key, new ArrayList<>());
                                List<String> history = barcodeHistories.get(key);
                                if (history.size() >= MAX_READINGS) history.remove(0);
                                history.add(raw);
                            }
                        }

                        // Selecciona el candidato más largo entre todos los keys válidos
                        String bestCandidate = "";
                        int bestProgress = 0;
                        for (List<String> history : barcodeHistories.values()) {
                            String reconstructed = majorityVote(history);
                            int progress = progressPercent(reconstructed);
                            if (progress > bestProgress) {
                                bestCandidate = reconstructed;
                                bestProgress = progress;
                            }
                        }

                        // Actualiza feedback visual
                        progressBar.setProgress(bestProgress);
                        if (bestProgress < 60) {
                            setLaserColor(Color.YELLOW);
                            feedbackText.setText("Leyendo código… " + bestProgress + "%");
                        } else if (bestProgress < 100) {
                            setLaserColor(Color.GREEN);
                            feedbackText.setText("¡Casi listo! " + bestProgress + "%");
                        } else {
                            setLaserColor(Color.BLUE);
                            feedbackText.setText("¡Código leído!");
                        }

                        // Si es válido, finalizar
                        if (isLikelyValidPdf417(bestCandidate)) {
                            scanned = true;
                            setLaserColor(Color.CYAN);
                            feedbackText.setText("¡Lectura completada!");

                            if (vibrator != null && vibrator.hasVibrator()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(500, 255));
                                } else {
                                   vibrator.vibrate(500);
                                }
                            }

                            Intent resultIntent = new Intent();
                            resultIntent.putExtra("barcode", bestCandidate);
                            setResult(Activity.RESULT_OK, resultIntent);

                            new Handler(getMainLooper()).postDelayed(this::finish, 400);
                        } else {
                            // Si pasan más de 4 segundos sin avance, resetea histories
                            if (now - lastReadingTimestamp > 4000) {
                                barcodeHistories.clear();
                                progressBar.setProgress(0);
                                feedbackText.setText("No se pudo leer, intenta enfocar mejor");
                                setLaserColor(Color.RED);
                                lastReadingTimestamp = now;
                            } else {
                                lastReadingTimestamp = now;
                            }
                        }
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    // Busca si ya existe una key "parecida"
    private String findSimilarKey(String candidate) {
        for (String key : barcodeHistories.keySet()) {
            if (distance(candidate, key) <= GLITCH_TOLERANCE) return key;
        }
        return candidate;
    }

    // Reconstrucción por “majority vote” por posición
    private String majorityVote(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) return "";
        int maxLen = 0;
        for (String s : fragments) maxLen = Math.max(maxLen, s.length());
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            Map<Character, Integer> count = new HashMap<>();
            for (String frag : fragments) {
                if (frag.length() > i) {
                    char c = frag.charAt(i);
                    count.put(c, count.getOrDefault(c, 0) + 1);
                }
            }
            char best = ' ';
            int freq = 0;
            for (Map.Entry<Character, Integer> e : count.entrySet()) {
                if (e.getValue() > freq) {
                    best = e.getKey();
                    freq = e.getValue();
                }
            }
            result.append(best);
        }
        return result.toString().trim();
    }

    // Distancia de Levenshtein simplificada (hasta GLITCH_TOLERANCE)
    private int distance(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1))
                    dp[i][j] = dp[i - 1][j - 1];
                else
                    dp[i][j] = 1 + Math.min(Math.min(dp[i - 1][j], dp[i][j - 1]), dp[i - 1][j - 1]);
            }
        }
        return dp[la][lb];
    }

    private boolean isLikelyValidPdf417(String value) {
        if (value == null) return false;
        int len = value.length();
        return len >= PDF417_MIN_LENGTH && len <= PDF417_MAX_LENGTH;
    }

    private int progressPercent(String code) {
        if (code == null) return 0;
        return Math.min(100, 100 * code.length() / PDF417_MAX_LENGTH);
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
