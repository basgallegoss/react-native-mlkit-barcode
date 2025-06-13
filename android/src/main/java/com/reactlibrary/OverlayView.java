package com.reactlibrary;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {
    private RectF frameRect;
    private float cornerRadius = 36f;
    private Paint backgroundPaint;
    private Paint clearPaint;

    public OverlayView(Context context) {
        super(context);
        init();
    }
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(0xCC000000); // Fondo negro translúcido
        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);
    }

    /**
     * Llama a este método para configurar el rectángulo del marco central.
     */
    public void setFrame(RectF rect, float radius) {
        this.frameRect = rect;
        this.cornerRadius = radius;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int sc = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        if (frameRect != null) {
            canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint);
        }
        canvas.restoreToCount(sc);
    }
}
