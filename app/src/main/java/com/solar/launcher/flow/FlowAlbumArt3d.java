package com.solar.launcher.flow;

import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Shared Camera.rotateY album cover draw — Flow carousel, handoff flyer, Now Playing.
 * ponytail: static helpers only; one bitmap in, no duplicate decode.
 */
public final class FlowAlbumArt3d {

    /**
     * 2026-07-11 — Classipod NP tilt that stays inside the padded album slot.
     * Was 26° oversize (silly / clipped bars); then 16° still filled the tall match_parent slot.
     * Reversal: 26f + 1.12× oversize, or 14f flush-fill.
     */
    public static final float PLAYER_ROT_Y_DEG = 18f;
    /**
     * 2026-07-11 — Cover content-cell height (parallel to info 235×225). Reflection is extra below.
     * Reversal: softCap viewH*0.62 / frac-shrink that made ~136 covers.
     */
    public static final float PLAYER_CONTENT_H = 225f;
    /**
     * 2026-07-11 — Empty px above the cover inside the art slot so perspective near-edge can grow
     * without a flat crop (slot uses matching negative marginTop). Reversal: top=0 flush clip.
     */
    public static final float PLAYER_TOP_OVERSHOOT = 14f;
    /**
     * 2026-07-11 — Only used when the slot is shorter than content+overshoot (tight fallback).
     * Tall slots keep a full 225 cover; floor uses leftover px via {@link #playerReflectHeight}.
     * Reversal: always size = viewH*(1-frac) which undersized the cover.
     */
    public static final float PLAYER_REFLECT_FRAC = 0.12f;
    /** Idle carousel reflection peak — glossy floor reflection. */
    public static final float REFLECT_PEAK_FRAC = 0.55f;

    private static final Camera camera = new Camera();
    private static final Matrix matrix = new Matrix();
    private static final RectF bitmapSrc = new RectF();
    private static final Matrix reflectionFlipMatrix = new Matrix();
    private static final Matrix reflectionMatrix = new Matrix();
    private static final RectF reflectionRowRect = new RectF();
    private static final RectF reflectionParentClipRect = new RectF();

    private FlowAlbumArt3d() {}

    /** View-local draw rect + rotation — single source for NP and handoff landing. */
    public static final class AlbumArtPose {
        public final RectF drawRect;
        public final float rotationYDeg;

        AlbumArtPose(RectF drawRect, float rotationYDeg) {
            this.drawRect = drawRect;
            this.rotationYDeg = rotationYDeg;
        }
    }

    /** Handoff / 3D card bounds — square prism, not south-encroached back-face rect. */
    public static RectF squareBounds(RectF rect) {
        if (rect == null) return new RectF();
        float size = Math.min(rect.width(), rect.height());
        if (size <= 0f) return new RectF(rect);
        float cx = rect.centerX();
        float cy = rect.centerY();
        return new RectF(cx - size * 0.5f, cy - size * 0.5f, cx + size * 0.5f, cy + size * 0.5f);
    }

    /** Screen rect of a Flow center slot — always square transform bounds. */
    public static RectF screenRectFromSlot(View view, FlowEngine.SlotTransform t) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float half = t.width * 0.5f;
        return new RectF(
                loc[0] + t.centerX - half,
                loc[1] + t.centerY - half,
                loc[0] + t.centerX + half,
                loc[1] + t.centerY + half);
    }

    /**
     * 2026-07-11 — Cover square fills the parallel 235×225 cell; floor gloss uses leftover slot.
     * {@link #PLAYER_TOP_OVERSHOOT} pads above so tilt isn't cropped; handoff uses same geometry.
     * Reversal: softCap / 0.98× shrink / top=0 flush (clipped near-edge).
     */
    public static AlbumArtPose playerPose(float viewW, float viewH) {
        if (viewW <= 0f || viewH <= 0f) {
            return new AlbumArtPose(new RectF(), PLAYER_ROT_Y_DEG);
        }
        // Prefer 14px headroom + 225 cover when the slot is tall enough (281).
        float top = 0f;
        float maxCover = PLAYER_CONTENT_H;
        if (viewH >= PLAYER_CONTENT_H + PLAYER_TOP_OVERSHOOT + 28f) {
            top = PLAYER_TOP_OVERSHOOT;
        } else if (viewH < PLAYER_CONTENT_H) {
            maxCover = viewH * (1f - PLAYER_REFLECT_FRAC);
        }
        float size = Math.min(viewW, maxCover);
        if (size <= 0f) {
            return new AlbumArtPose(new RectF(), PLAYER_ROT_Y_DEG);
        }
        // Cover top aligns with info (slot marginTop cancels overshoot); gloss below.
        return new AlbumArtPose(
                new RectF(0f, top, size, top + size),
                PLAYER_ROT_Y_DEG);
    }

    /**
     * 2026-07-11 — NP + flyer: one Camera.rotateY wraps cover and floor so gloss kisses the cover
     * edge (same as Flow in-slot). Was rotate-cover-only + flat floor → visible gap under art.
     * Softer cam distance (−18 vs default −8) reduces near-edge blowout into the status band.
     * Reversal: drawCover(rotY) then flat drawReflectionFloor; default camera location.
     */
    public static void drawPlayerCoverWithReflection(Canvas canvas, Bitmap bmp, RectF rect,
            float rotationYDeg, float coverAlpha, float reflectAlpha, float reflectHeight,
            int[] reflectTable, Paint coverPaint, Paint reflectionPaint) {
        if (canvas == null || bmp == null || bmp.isRecycled() || rect == null) return;
        RectF square = squareBounds(rect);
        if (square.width() <= 0f) return;

        canvas.save();
        float pivotX = CoverFlowLayout.pivotXForCoverFlow(rotationYDeg,
                square.left, square.right);
        float pivotY = square.centerY();
        camera.save();
        // Farther eye = gentler perspective (less top/bottom flare on the near edge).
        camera.setLocation(0f, 0f, -18f);
        camera.rotateY(rotationYDeg);
        matrix.reset();
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-pivotX, -pivotY);
        matrix.postTranslate(pivotX, pivotY);
        canvas.concat(matrix);

        // Local space after shared tilt — floor starts at square.bottom (gapless).
        drawCover(canvas, bmp, square, 0f, coverAlpha, coverPaint);
        if (reflectHeight > 1f && reflectionPaint != null) {
            drawReflectionFloor(canvas, bmp, square, reflectAlpha, reflectHeight,
                    reflectTable, reflectionPaint, null);
        }
        canvas.restore();
    }

    /**
     * 2026-07-11 — Floor band under a morphing cover (Flow→NP flyer).
     * Scales with cover size so the gloss rides with the art instead of vanishing mid-flight.
     * Reversal: flyer drawCover only (no floor).
     */
    public static float handoffReflectHeight(float coverSize, float morphT, float fromReflectH,
            float toReflectH) {
        if (coverSize <= 0f) return 0f;
        float t = Math.max(0f, Math.min(1f, morphT));
        float lerped = fromReflectH + (toReflectH - fromReflectH) * t;
        if (lerped > 0f) return Math.min(lerped, coverSize * 0.55f);
        // Fail-open: ~half cover height like CoverFlowLayout reflect/display ratio.
        return Math.min(coverSize * 0.45f, coverSize * 0.55f);
    }

    /**
     * 2026-07-11 — Reflection band under NP cover (remaining floor in slot, capped ~38% of cover).
     */
    public static float playerReflectHeight(float viewH, RectF drawRect) {
        if (drawRect == null || viewH <= 0f) return 0f;
        float room = Math.max(0f, viewH - drawRect.bottom);
        float cap = drawRect.height() * 0.38f;
        return Math.min(room, cap);
    }

    /** @deprecated use {@link #playerPose(float, float)} */
    public static RectF playerAlbumDrawRect(float viewW, float viewH) {
        return playerPose(viewW, viewH).drawRect;
    }

    /** Screen rect for handoff landing — same geometry as resting Now Playing 3D slot. */
    public static RectF playerScreenRect(View container) {
        if (container == null) return new RectF();
        int[] loc = new int[2];
        container.getLocationOnScreen(loc);
        float w = container.getWidth();
        float h = container.getHeight();
        AlbumArtPose pose = playerPose(w, h);
        RectF r = pose.drawRect;
        if (r.width() <= 0f) return new RectF();
        return new RectF(loc[0] + r.left, loc[1] + r.top, loc[0] + r.right, loc[1] + r.bottom);
    }

    /** @deprecated use {@link #playerScreenRect(View)} */
    public static RectF playerAlbumScreenRect(View container) {
        return playerScreenRect(container);
    }

    public static void drawCover(Canvas canvas, Bitmap bmp, RectF rect, float rotationYDeg,
            float alpha, Paint paint) {
        if (canvas == null || bmp == null || bmp.isRecycled() || rect == null || paint == null) return;
        RectF square = squareBounds(rect);
        if (square.width() <= 0f) return;

        canvas.save();
        float pivotX = CoverFlowLayout.pivotXForCoverFlow(rotationYDeg,
                square.left, square.right);
        float pivotY = square.centerY();
        camera.save();
        camera.rotateY(rotationYDeg);
        matrix.reset();
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-pivotX, -pivotY);
        matrix.postTranslate(pivotX, pivotY);
        canvas.concat(matrix);

        int oldAlpha = paint.getAlpha();
        paint.setAlpha((int) (oldAlpha * alpha));
        canvas.drawBitmap(bmp, null, square, paint);
        paint.setAlpha(oldAlpha);
        canvas.restore();
    }

    /**
     * Cover + floor reflection (Flow carousel gloss).
     *
     * @param reflectHeight px below square bottom; 0 skips reflection
     */
    public static void drawCoverWithReflection(Canvas canvas, Bitmap bmp, RectF rect,
            float rotationYDeg, float alpha, float reflectHeight, int[] reflectTable,
            Paint coverPaint, Paint reflectionPaint) {
        if (canvas == null || bmp == null || bmp.isRecycled() || rect == null) return;
        RectF square = squareBounds(rect);
        drawCover(canvas, bmp, square, rotationYDeg, alpha, coverPaint);
        drawReflectionFloor(canvas, bmp, square, alpha, reflectHeight, reflectTable, reflectionPaint, null);
    }

    public static void drawReflectionFloor(Canvas canvas, Bitmap bmp, RectF squareRect,
            float alpha, float reflectHeight, int[] reflectTable, Paint reflectionPaint, Matrix slotMatrix) {
        drawReflectionFloorInternal(canvas, bmp, squareRect, alpha, reflectHeight, reflectTable,
                reflectionPaint, slotMatrix, 0);
    }

    public static void drawReflectionFloorCoarse(Canvas canvas, Bitmap bmp, RectF squareRect,
            float alpha, float reflectHeight, int[] reflectTable, Paint reflectionPaint,
            Matrix slotMatrix, int maxBands) {
        drawReflectionFloorInternal(canvas, bmp, squareRect, alpha, reflectHeight, reflectTable,
                reflectionPaint, slotMatrix, Math.max(1, maxBands));
    }

    private static void drawReflectionFloorInternal(Canvas canvas, Bitmap bmp, RectF squareRect,
            float alpha, float reflectHeight, int[] reflectTable, Paint reflectionPaint,
            Matrix slotMatrix, int maxBands) {
        if (canvas == null || bmp == null || bmp.isRecycled() || squareRect == null) return;
        if (reflectHeight <= 0f || reflectionPaint == null) return;
        RectF square = squareBounds(squareRect);

        int rows = reflectTable != null && reflectTable.length > 0
                ? reflectTable.length : Math.max(1, Math.round(reflectHeight));
        float rowH = reflectHeight / rows;
        int bands = maxBands > 0 ? Math.min(rows, maxBands) : rows;

        bitmapSrc.set(0f, 0f, bmp.getWidth(), bmp.getHeight());

        reflectionFlipMatrix.reset();
        reflectionFlipMatrix.setRectToRect(bitmapSrc, square, Matrix.ScaleToFit.FILL);
        reflectionFlipMatrix.postScale(1f, -1f, square.centerX(), square.bottom);

        reflectionMatrix.set(reflectionFlipMatrix);
        if (slotMatrix != null) {
            reflectionMatrix.postConcat(slotMatrix);
        }

        int oldAlpha = reflectionPaint.getAlpha();
        reflectionPaint.setShader(null);

        for (int band = 0; band < bands; band++) {
            int rowStart = band * rows / bands;
            int rowEnd = (band + 1) * rows / bands;
            if (rowEnd <= rowStart) rowEnd = rowStart + 1;
            int sampleRow = (rowStart + rowEnd - 1) / 2;
            int tableA = reflectTable != null && sampleRow < reflectTable.length
                    ? reflectTable[sampleRow] : (768 * (rows - sampleRow) / rows);
            int peakAlpha = (tableA * Math.round(alpha * 256) + 129) >> 8;
            peakAlpha = Math.min(255, Math.max(0, peakAlpha * 255 / 768));
            peakAlpha = Math.round(peakAlpha * REFLECT_PEAK_FRAC);
            if (peakAlpha <= 0) continue;

            float y0 = square.bottom + rowStart * rowH;
            float y1 = (band == bands - 1) ? (square.bottom + reflectHeight)
                    : (square.bottom + rowEnd * rowH);

            reflectionRowRect.set(square.left, y0, square.right, y1);

            canvas.save();
            if (slotMatrix != null) {
                slotMatrix.mapRect(reflectionParentClipRect, reflectionRowRect);
                canvas.clipRect(reflectionParentClipRect);
            } else {
                canvas.clipRect(reflectionRowRect);
            }

            reflectionPaint.setAlpha(peakAlpha);
            canvas.drawBitmap(bmp, reflectionMatrix, reflectionPaint);
            canvas.restore();
        }
        reflectionPaint.setAlpha(oldAlpha);
    }
}
