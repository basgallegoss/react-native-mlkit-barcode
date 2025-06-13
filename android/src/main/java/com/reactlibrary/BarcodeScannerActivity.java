package com.reactlibrary;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.RectF;
import android.os.*;
import android.util.Size;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class BarcodeScannerActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 10;
    private static final int PDF417_MIN_LENGTH = 80;
    private static final int PDF417_MAX_LENGTH = 150;
    private static final int MAX_READINGS = 30;
    private static final int GLITCH_TOLERANCE = 2;

    private PreviewView previewView;
    private OverlayView overlayView;
    private FrameLayout scanFrame;
    private View laser;
    private Handler laserHandler = new Handler(Looper.getMainLooper());
    private boolean laserDown = true;
    private boolean scanned = false;
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

        setContentView(getLayoutInflater().inflate(
            getResources().getIdentifier("overlay_scanner", "layout", getPackageName()), null));

        previewView = findViewById(getResources().getIdentifier("previewView", "id", getPackageName()));
        overlayView = findViewById(getResources().getIdentifier("overlayView", "id", getPackageName()));
        scanFrame = findViewById(getResources().getIdentifier("scan_frame", "id", getPackageName()));
        laser = findViewById(getResources().getIdentifier("laser", "id", getPackageName()));
        progressBar = findViewById(getResources().getIdentifier("progressBar", "id", getPackageName()));
        feedbackText = findViewById(getResources().getIdentifier("feedbackText", "id", getPackageName()));

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        // Botón Flash
        ImageButton flashButton = findViewById(getResources().getIdentifier("flash_button", "id", getPackageName()));
        flashButton.setOnClickListener(v -> {
            flashEnabled = !flashEnabled;
            if (cameraControl != null) {
                cameraControl.enableTorch(flashEnabled);
                flashButton.setImageResource(flashEnabled ? R.drawable.ic_flash_off : R.drawable.ic_flash_on);
            }
        });

        // Botón cerrar
        ImageButton closeButton = findViewById(getResources().getIdentifier("close_button", "id", getPackageName()));
        closeButton.setOnClickListener(v -> finish());

        // Enfoque por toque
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && cameraControl != null) {
                MeteringPointFactory factory = previewView.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
                cameraControl.startFocusAndMetering(action);
            }
            return false;
        });

        // Configura overlay y marco adaptativo
        scanFrame.post(this::setAdaptiveFrame);

        startLaserAnimation();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    // Calcula marco adaptativo a cualquier pantalla/orientación
    private void setAdaptiveFrame() {
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        // Ajusta según orientación y tamaño de pantalla
        float frameWidth = screenWidth * (screenWidth > screenHeight ? 0.5f : 0.8f);
        float aspectRatio = 1.62f; // PDF417 típico
        float frameHeight = frameWidth / aspectRatio;
        if (frameHeight > screenHeight * 0.65f) {
            frameHeight = screenHeight * 0.65f;
            frameWidth = frameHeight * aspectRatio;
        }

        float left = (screenWidth - frameWidth) / 2f;
        float top = (screenHeight - frameHeight) / 2f;
        float right = left + frameWidth;
        float bottom = top + frameHeight;
        RectF frameRect = new RectF(left, top, right, bottom);

        // Actualiza tamaño y posición del scan_frame
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) scanFrame.getLayoutParams();
        params.width = (int) frameWidth;
        params.height = (int) frameHeight;
        params.leftMargin = (int) left;
        params.topMargin = (int) top;
        scanFrame.setLayoutParams(params);

        // Radio: 8% del ancho marco para esquinas redondeadas
        overlayView.setFrame(frameRect, frameWidth * 0.08f);
    }

    private void startLaserAnimation() {
        laserHandler.post(new Runnable() {
            @Override
            public void run() {
                if (scanned) return;
                int frameHeight = scanFrame.getHeight();
                int laserHeight = laser.getHeight();
                int minY = 0;
                int maxY = frameHeight - laserHeight;
                float currY = laser.getY();
                float step = 6f;
                if (laserDown) {
                    currY += step;
                    if (currY >= maxY) laserDown = false;
                } else {
                    currY -= step;
                    if (currY <= minY) laserDown = true;
                }
                laser.setY(currY);
                laserHandler.postDelayed(this, 10);
            }
        });
    }

    private void setLaserColor(int color) {
        laser.setBackgroundColor(color);
        scanFrame.setBackgroundColor(android.graphics.Color.argb(18,
                android.graphics.Color.red(color),
                android.graphics.Color.green(color),
                android.graphics.Color.blue(color)));
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
        // Enfoca al centro al iniciar
        previewView.postDelayed(() -> {
            MeteringPointFactory factory = previewView.getMeteringPointFactory();
            MeteringPoint point = factory.createPoint(previewView.getWidth()/2f, previewView.getHeight()/2f);
            FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
            cameraControl.startFocusAndMetering(action);
        }, 500);

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
    }

    private void processImageProxy(BarcodeScanner scanner, ImageProxy imageProxy) {
        if (scanned) {
            imageProxy.close();
            return;
        }
        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        InputImage image =
                InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (scanned) return;
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
                    progressBar.setProgress(bestProgress);
                    if (bestProgress < 60) {
                        setLaserColor(0xFFFFCC00); // Amarillo
                        feedbackText.setText("Leyendo… " + bestProgress + "%");
                    } else if (bestProgress < 100) {
                        setLaserColor(0xFF00FF00); // Verde
                        feedbackText.setText("¡Casi listo! " + bestProgress + "%");
                    } else {
                        setLaserColor(0xFF2D55FF); // Azul
                        feedbackText.setText("¡Código leído!");
                    }
                    if (isLikelyValidPdf417(bestCandidate)) {
                        scanned = true;
                        setLaserColor(0xFF2D55FF); // Azul
                        feedbackText.setText("¡Lectura completa!");
                        vibrateSuccess();

                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("barcode", bestCandidate);
                        setResult(Activity.RESULT_OK, resultIntent);
                        new Handler(getMainLooper()).postDelayed(this::finish, 400);
                    } else {
                        if (now - lastReadingTimestamp > 3500) {
                            barcodeHistories.clear();
                            progressBar.setProgress(0);
                            feedbackText.setText("No se pudo leer, intenta enfocar mejor");
                            setLaserColor(0xFFFF4444); // Rojo
                            vibrateError();
                            lastReadingTimestamp = now;
                        } else {
                            lastReadingTimestamp = now;
                        }
                    }
                })
                .addOnFailureListener(Throwable::printStackTrace)
                .addOnCompleteListener(task -> imageProxy.close());
    }

    // Vibración fuerte: éxito
    private void vibrateSuccess() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(500);
            }
        }
    }

    // Vibración patrón: error
    private void vibrateError() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] pattern = {0, 150, 100, 150, 100, 200};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(new long[]{0, 150, 100, 150, 100, 200}, -1);
            }
        }
    }

    private String findSimilarKey(String candidate) {
        for (String key : barcodeHistories.keySet()) {
            if (distance(candidate, key) <= GLITCH_TOLERANCE) return key;
        }
        return candidate;
    }

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
