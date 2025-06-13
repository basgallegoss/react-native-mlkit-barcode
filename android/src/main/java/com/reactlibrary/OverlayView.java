package com.reactlibrary;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class OverlayView extends View {
    private RectF frameRect;
    private float cornerRadius = 36f;
    private float laserY = 0f;

    private Paint backgroundPaint;
    private Paint clearPaint;
    private Paint framePaint;
    private Paint laserPaint;

    public OverlayView(Context context) { super(context); init(); }
    public OverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(0xCC000000);
        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);
        framePaint = new Paint();
        framePaint.setColor(0xFFFF2D55);
        framePaint.setStrokeWidth(6f);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setAntiAlias(true);
        laserPaint = new Paint();
        laserPaint.setShader(new LinearGradient(0, 0, getWidth(), 0, 0xFF2D55FF, 0xFF7D30D7, Shader.TileMode.CLAMP));
        laserPaint.setStrokeWidth(8f);
        laserPaint.setAntiAlias(true);
        laserPaint.setAlpha(180);
    }

    public void setFrame(RectF rect, float radius) {
        this.frameRect = rect;
        this.cornerRadius = radius;
        invalidate();
    }

    public RectF getFrameRect() {
        return frameRect;
    }

    public void setLaserPos(float y) {
        this.laserY = y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int sc = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        if (frameRect != null) {
            canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, clearPaint);
            canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint);
            canvas.drawLine(frameRect.left + 16, laserY, frameRect.right - 16, laserY, laserPaint);
        }
        canvas.restoreToCount(sc);
    }
}
