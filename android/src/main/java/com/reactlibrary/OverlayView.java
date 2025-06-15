package com.reactlibrary;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class OverlayView extends View {
    private RectF frameRect;
    private final float cornerRadiusPx;
    private final float laserPaddingPx;

    private final Paint backgroundPaint;
    private final Paint clearPaint;
    private final Paint framePaint;
    private final Paint laserPaint;

    // Posición actual del láser
    private float laserY = 0f;

    public OverlayView(Context context) {
        this(context, null);
    }
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        cornerRadiusPx = dpToPx(24f);
        laserPaddingPx = dpToPx(16f);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(0x99000000);

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(0xFFFF2D55);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dpToPx(3f));

        laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        laserPaint.setStrokeWidth(dpToPx(2f));
        laserPaint.setAlpha(200);
        // shader inicializado en onSizeChanged
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Degradado horizontal para el láser
        laserPaint.setShader(new LinearGradient(
            0, 0, w, 0,
            Color.TRANSPARENT, 0xFF2D55FF,
            Shader.TileMode.CLAMP
        ));
    }

    /**
     * Actualiza el rectángulo de escaneo.
     */
    public void setFrame(RectF rect) {
        this.frameRect = rect;
        invalidate();
    }

    /**
     * Devuelve el rectángulo de escaneo actual.
     */
    public RectF getFrameRect() {
        return frameRect;
    }

    /**
     * Posición vertical (en coordenadas absolutas de la pantalla) de la línea láser.
     */
    public void setLaserPos(float y) {
        this.laserY = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (frameRect == null) return;

        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        // 1) Sombreado de fondo
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        // 2) Borra la ventana de escaneo
        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, clearPaint);

        // 3) Dibuja el marco rojo
        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, framePaint);

        // 4) Dibuja la línea láser dentro del marco
        float startX = frameRect.left + laserPaddingPx;
        float endX   = frameRect.right - laserPaddingPx;
        canvas.drawLine(startX, laserY, endX, laserY, laserPaint);

        canvas.restoreToCount(saveCount);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}
