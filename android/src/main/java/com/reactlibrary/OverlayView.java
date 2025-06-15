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

    public OverlayView(Context context) {
        this(context, null);
    }
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // radio fijo 24dp, padding láser 16dp
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
    }

    public void setFrame(RectF rect) {
        this.frameRect = rect;
        invalidate();
    }

    public RectF getFrameRect() {
        return frameRect;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (frameRect == null) return;
        int sc = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);

        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, clearPaint);

        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, framePaint);

        canvas.restoreToCount(sc);
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}
