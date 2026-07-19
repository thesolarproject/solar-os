package com.solar.launcher.stem;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Activity–style rings — kept for reversal / A-B vs puck face.
 * Prefer {@link StemFaceView} for the shipping Stem Player UI (2026-07-18).
 * Layman: four coloured arcs — brighter when that stem is loud or selected.
 * Technical: Canvas arcs; gains[0..3] drive sweep; activeZone draws focus halo.
 * 2026-07-18
 */
public class StemRingsView extends View {
    /** Vocals / Drums / Bass / Melody — Activity Move / Exercise / Stand / orange. */
    private static final int[] RING_COLORS = {
            0xFFFA114F, // vocals — Move pink-red
            0xFF92E82A, // drums — Exercise green
            0xFF00C7BE, // bass — Stand teal
            0xFFFF9F0A, // melody — warm amber
    };

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private final float[] gains = new float[] { 1f, 1f, 1f, 1f };
    private int activeZone = 0;
    private boolean loading;

    public StemRingsView(Context context) {
        super(context);
        init();
    }

    public StemRingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(0x33FFFFFF);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setStrokeCap(Paint.Cap.ROUND);
        haloPaint.setColor(0x66FFFFFF);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    /** Set stem volumes 0..1 and which zone hardware focuses. */
    public void setState(float[] g, int zone, boolean isLoading) {
        if (g != null) {
            for (int i = 0; i < 4 && i < g.length; i++) {
                float v = g[i];
                if (v < 0f) v = 0f;
                if (v > 1f) v = 1f;
                gains[i] = v;
            }
        }
        activeZone = zone;
        loading = isLoading;
        invalidate();
    }

    public void setActiveZone(int zone) {
        activeZone = zone;
        invalidate();
    }

    public void setGain(int zone, float gain) {
        if (zone < 0 || zone >= 4) return;
        if (gain < 0f) gain = 0f;
        if (gain > 1f) gain = 1f;
        gains[zone] = gain;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float maxR = Math.min(w, h) * 0.46f;
        float stroke = Math.max(8f, maxR * 0.11f);
        float gap = stroke * 1.35f;

        for (int i = 0; i < 4; i++) {
            float r = maxR - i * gap;
            oval.set(cx - r, cy - r, cx + r, cy + r);
            trackPaint.setStrokeWidth(stroke);
            canvas.drawArc(oval, -90f, 360f, false, trackPaint);

            float sweep = 20f + gains[i] * 320f;
            if (loading) {
                // Soft pulse while Lalal works.
                float pulse = (float) (0.35 + 0.2 * Math.sin(System.currentTimeMillis() / 280.0 + i));
                sweep = 40f + pulse * 200f;
            }
            arcPaint.setColor(RING_COLORS[i]);
            arcPaint.setStrokeWidth(stroke);
            arcPaint.setAlpha(i == activeZone ? 255 : 180);
            canvas.drawArc(oval, -90f, sweep, false, arcPaint);

            if (i == activeZone) {
                haloPaint.setStrokeWidth(stroke + 4f);
                canvas.drawArc(oval, -90f, sweep, false, haloPaint);
            }

            // Stem Player–style corner dots (compass: N=vocals, W=drums, E=bass, S=melody).
            float dotR = stroke * 0.55f;
            float dx = 0f;
            float dy = 0f;
            if (i == 0) dy = -maxR - stroke; // top
            else if (i == 1) dx = -maxR - stroke; // left
            else if (i == 2) dx = maxR + stroke; // right
            else dy = maxR + stroke; // bottom
            // Keep dots inside view — use ring endpoints instead for outer ring only.
            if (i == 0) {
                float ang = (float) Math.toRadians(-90 + sweep);
                float px = cx + (float) Math.cos(ang) * r;
                float py = cy + (float) Math.sin(ang) * r;
                dotPaint.setColor(RING_COLORS[i]);
                canvas.drawCircle(px, py, i == activeZone ? dotR * 1.2f : dotR, dotPaint);
            }
        }
        if (loading) {
            postInvalidateDelayed(50);
        }
    }
}
