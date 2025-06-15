package com.reactlibrary;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class OverlayView extends View {
    private RectF frameRect;
    private float cornerRadiusPx;
    private float laserY;
    private float laserPaddingPx;

    private Paint backgroundPaint;
    private Paint clearPaint;
    private Paint framePaint;
    private Paint laserPaint;

    public OverlayView(Context context) {
        super(context);
        init(context);
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context ctx) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        cornerRadiusPx = dpToPx(ctx, 36f);
        laserPaddingPx = dpToPx(ctx, 16f);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(0xCC000000);

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        framePaint.setColor(0xFFFF2D55);
        framePaint.setStrokeWidth(dpToPx(ctx, 4f));
        framePaint.setStyle(Paint.Style.STROKE);

        laserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        laserPaint.setStrokeWidth(dpToPx(ctx, 4f));
        laserPaint.setAlpha(180);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        laserPaint.setShader(new LinearGradient(
            0, 0, w, 0,
            0xFF2D55FF, 0xFF7D30D7,
            Shader.TileMode.CLAMP
        ));
    }

    public void setFrame(RectF rect, float cornerRadiusDp) {
        this.frameRect = rect;
        this.cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            cornerRadiusDp,
            getResources().getDisplayMetrics()
        );
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
        if (frameRect == null) return;

        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);

        canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, clearPaint);
        canvas.drawRoundRect(frameRect, cornerRadiusPx, cornerRadiusPx, framePaint);
        canvas.drawLine(
            frameRect.left + laserPaddingPx, laserY,
            frameRect.right - laserPaddingPx, laserY,
            laserPaint
        );

        canvas.restoreToCount(saveCount);
    }

    private static float dpToPx(Context ctx, float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            ctx.getResources().getDisplayMetrics()
        );
    }
}
