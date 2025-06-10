package com.reactlibrary;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;

import java.io.IOException;
import java.util.List;

public class BarcodeScannerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera camera;
    private SurfaceView surfaceView;
    private BarcodeScanner scanner;
    private boolean scanned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        surfaceView = new SurfaceView(this);
        setContentView(surfaceView);

        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);

        BarcodeScannerOptions options =
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_PDF417)
                        .build();
        scanner = BarcodeScanning.getClient(options);
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open();
        try {
            Camera.Parameters params = camera.getParameters();


            Camera.Size optimalSize = getOptimalPreviewSize(
                params.getSupportedPreviewSizes(),
                surfaceView.getWidth(), surfaceView.getHeight()
            );
            if (optimalSize != null) {
                params.setPreviewSize(optimalSize.width, optimalSize.height);
            }


            if (params.isZoomSupported()) {
                int maxZoom = params.getMaxZoom();
                int zoom = maxZoom / 2;
                params.setZoom(zoom);
            }

            camera.setParameters(params);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (scanned) return;
        Camera.Parameters parameters = camera.getParameters();
        Camera.Size size = parameters.getPreviewSize();
        int width = size.width;
        int height = size.height;

        InputImage image = InputImage.fromByteArray(
                data,
                width,
                height,
                0,
                InputImage.IMAGE_FORMAT_NV21
        );

        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (!scanned && barcodes.size() > 0) {
                        scanned = true;
                        Barcode barcode = barcodes.get(0);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("barcode", barcode.getRawValue());
                        setResult(Activity.RESULT_OK, resultIntent);
                        if (camera != null) {
                            camera.stopPreview();
                            camera.release();
                        }
                        finish();
                    }
                })
                .addOnFailureListener(e -> {

                });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }
}
