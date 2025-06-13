package com.reactlibrary;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

// En OverlayView.java (solo Overlay y láser visual, no animación)
public class OverlayView extends View {
    private RectF frameRect;
    private float cornerRadius = 36f;
    private Paint backgroundPaint;
    private Paint clearPaint;
    private Paint framePaint;
    private Paint laserPaint;
    private float laserPos = 0f; // Posición Y relativa dentro del frame
    private LinearGradient laserGradient;

    public OverlayView(Context context) { super(context); init(); }
    public OverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(0xCC000000); // Fondo negro translúcido
        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);
        framePaint = new Paint();
        framePaint.setColor(0xFFFF2D55);
        framePaint.setStrokeWidth(6f);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setAntiAlias(true);
        laserPaint = new Paint();
        laserPaint.setStrokeWidth(8f);
        laserPaint.setAntiAlias(true);
        laserPaint.setAlpha(180);
    }

    public void setFrame(RectF rect, float radius) {
        this.frameRect = rect;
        this.cornerRadius = radius;
        updateLaserGradient();
        if (rect != null) this.laserPos = rect.top + 12;
        invalidate();
    }

    public void setLaserPos(float y) {
        this.laserPos = y;
        invalidate();
    }

    private void updateLaserGradient() {
        if (frameRect != null) {
            laserGradient = new LinearGradient(
                frameRect.left, 0, frameRect.right, 0,
                new int[]{0xFF2D55FF, 0xFF7D30D7},
                null, Shader.TileMode.CLAMP
            );
            laserPaint.setShader(laserGradient);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int sc = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        if (frameRect != null) {
            // Ventana transparente
            canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint);
            // Marco exterior
            canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint);
            // Línea láser animada (si está en el rango)
            if (laserPos >= frameRect.top && laserPos <= frameRect.bottom) {
                canvas.drawLine(frameRect.left + 16, laserPos, frameRect.right - 16, laserPos, laserPaint);
            }
        }
        canvas.restoreToCount(sc);
    }
}
