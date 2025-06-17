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
    private Shader laserShader;


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
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dpToPx(3f));
        framePaint.setColor(0xFFFF2D55);


        laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        laserPaint.setStyle(Paint.Style.STROKE);
        laserPaint.setStrokeWidth(dpToPx(2f));
        laserPaint.setStrokeCap(Paint.Cap.ROUND);
        laserPaint.setAlpha(225);

    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        laserShader = new LinearGradient(
            0, 0, w, 0,
            new int[]{0x00FF2D55, 0xFFFF2D55, 0x00FF2D55},
            new float[]{0f, 0.5f, 1f},
            Shader.TileMode.CLAMP
        );
        laserPaint.setShader(laserShader);
    }

    public void setFrame(RectF rect) {
        this.frameRect = rect;
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
        if (frameRect == null) return;

        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, clearPaint);


        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, framePaint);

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
