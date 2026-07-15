package com.solar.launcher.flow;

/**
 * Cover Flow scroll engine — time-based ease per album step (Classipod ~300ms),
 * Apple/Classipod pose layout via {@link CoverFlowLayout}.
 * 2026-07-14 — Touch free-scroll / fling parallel path; key {@link #scrollBy} unchanged.
 */
public final class FlowEngine {

    public static final int VISIBLE_RADIUS = CoverFlowLayout.SIDE_SLIDES;
    /** One wheel detent — frame-rate independent on MT6572. */
    public static final long SCROLL_MS = 290L;
    /** 2026-07-14 — Short snap after finger fling settles (iPhone CoverFlow detent). */
    public static final long FREE_SNAP_MS = 220L;
    /** Fling friction — albums/s²; coast then snap. */
    private static final float FLING_DECEL_ALBUMS = 9.5f;
    /** Stop coast when slower than this (albums/s). */
    private static final float FLING_STOP_ALBUMS = 0.35f;

    private int itemCount;
    private int centerIndex;
    private int targetIndex;
    private int step;
    private int slideFrameInt;
    private int fade;

    /** Time-based scroll segment — restarts when target queues mid-animation. */
    private long scrollAnimStartMs;
    private int scrollAnimFromFixed;
    private int scrollAnimToFixed;
    private boolean scrollAnimPendingStart;
    /** Duration for current ease segment (wheel SCROLL_MS or free-snap FREE_SNAP_MS). */
    private long scrollAnimDurationMs = SCROLL_MS;

    /** 2026-07-14 — Finger-follow / fling (touch only); keys use scrollBy. */
    private boolean freeScrollActive;
    private boolean flingActive;
    private float flingVelocityAlbumsPerSec;
    private long flingLastMs;

    private final CoverFlowLayout.SlidePose centerSlide = new CoverFlowLayout.SlidePose();
    private final CoverFlowLayout.SlidePose[] leftSlides =
            new CoverFlowLayout.SlidePose[CoverFlowLayout.SIDE_SLIDES];
    private final CoverFlowLayout.SlidePose[] rightSlides =
            new CoverFlowLayout.SlidePose[CoverFlowLayout.SIDE_SLIDES];
    /** 2026-07-05 — Pooled draw scratch; one transform reused per slotTransform call. */
    private final SlotTransform pooledSlotTransform = new SlotTransform();
    private final SlotTransform pooledEmptySlotTransform = new SlotTransform();
    private final CoverFlowLayout.SlidePose pooledPose = new CoverFlowLayout.SlidePose();
    /** O(1) matchKey lookup — set at catalog bind for 30k racks. */
    private java.util.Map<String, Integer> matchKeyIndex;

    private float viewportW = 480f;
    private float viewportH = 360f;
    private CoverFlowLayout.Metrics viewportMetrics;

    public FlowEngine() {
        for (int i = 0; i < CoverFlowLayout.SIDE_SLIDES; i++) {
            leftSlides[i] = new CoverFlowLayout.SlidePose();
            rightSlides[i] = new CoverFlowLayout.SlidePose();
        }
        resetSlides(null);
    }

    public void setItemCount(int count) {
        itemCount = Math.max(0, count);
        if (centerIndex >= itemCount) {
            centerIndex = Math.max(0, itemCount - 1);
            targetIndex = centerIndex;
            slideFrameInt = centerIndex << 16;
            step = 0;
            resetSlides(null);
        }
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getFocusIndex() {
        return centerIndex;
    }

    /**
     * Album visually nearest carousel center — may differ from {@link #getFocusIndex()} mid-scroll.
     * OK / flip must use this so fast wheel + select flips the cover on screen, not the target index.
     */
    public int getVisualCenterIndex() {
        if (itemCount <= 0) return 0;
        float visual = getVisualOffset();
        int lo = clamp((int) Math.floor(visual), 0, itemCount - 1);
        int hi = clamp(lo + 1, 0, itemCount - 1);
        if (hi > lo) {
            return (visual - lo) < 0.5f ? lo : hi;
        }
        return lo;
    }

    /** End scroll animation on the cover the user sees at center (wheel OK during motion). */
    public void snapToVisualCenter() {
        // 2026-07-14 — Also clears free/fling so Center OK lands on the visible cover.
        freeScrollActive = false;
        flingActive = false;
        flingVelocityAlbumsPerSec = 0f;
        flingLastMs = 0L;
        if (step == 0 && Math.abs(getVisualOffset() - getVisualCenterIndex()) < 0.02f) {
            return;
        }
        finishScrollAtTargetHard(getVisualCenterIndex());
    }

    public void setViewport(float w, float h) {
        if (w > 0f) viewportW = w;
        if (h > 0f) viewportH = h;
    }

    /** Cached metrics from {@link FlowView} — avoids rebuilding reflect_table each tick. */
    public void setViewportMetrics(CoverFlowLayout.Metrics metrics, float w, float h) {
        viewportMetrics = metrics;
        setViewport(w, h);
    }

    /** 2026-07-05 — Inject catalog index map; rollback: leave null for linear scan. */
    public void setMatchKeyIndex(java.util.Map<String, Integer> index) {
        matchKeyIndex = index;
    }

    private CoverFlowLayout.Metrics metrics() {
        if (viewportMetrics != null) {
            return viewportMetrics;
        }
        return CoverFlowLayout.metricsForViewport(viewportW, viewportH);
    }

    public float getVisualOffset() {
        return slideFrameInt / 65536f;
    }

    float getVisualOffsetAt(long nowMs) {
        return getVisualOffset();
    }

    public void setFocusIndex(int index) {
        freeScrollActive = false;
        flingActive = false;
        flingVelocityAlbumsPerSec = 0f;
        flingLastMs = 0L;
        if (itemCount <= 0) {
            centerIndex = 0;
            targetIndex = 0;
            slideFrameInt = 0;
            step = 0;
            resetSlides(null);
            return;
        }
        centerIndex = clamp(index, 0, itemCount - 1);
        targetIndex = centerIndex;
        slideFrameInt = centerIndex << 16;
        step = 0;
        resetSlides(null);
    }

    public void scrollBy(int delta) {
        // 2026-07-14 — Wheel/key owns stepped path; abandon free/fling first.
        cancelFreeScrollForKeyInput();
        if (itemCount <= 0 || delta == 0) return;
        if (delta > 0) showNextSlide();
        else showPreviousSlide();
    }

    /** True when wheel input started or continued scroll animation. */
    public boolean scrollByReturningMoved(int delta) {
        if (itemCount <= 0 || delta == 0) return false;
        int before = centerIndex;
        boolean animBefore = step != 0 || freeScrollActive || flingActive;
        scrollBy(delta);
        return step != 0 || centerIndex != before || animBefore || freeScrollActive || flingActive;
    }

    /**
     * 2026-07-14 — Start finger-follow Cover Flow (cancels stepped ease mid-flight).
     * Layman: grab the rack under your finger. Tech: stick slideFrameInt; clear step/fling.
     * Reversal: omit; touch only uses scrollBy flick steps again.
     */
    public void beginFreeScroll() {
        if (itemCount <= 0) return;
        if (step != 0) {
            float visual = getVisualOffset();
            slideFrameInt = Math.round(visual * 65536f);
            step = 0;
            scrollAnimStartMs = 0L;
            scrollAnimPendingStart = false;
            scrollAnimDurationMs = SCROLL_MS;
        }
        freeScrollActive = true;
        flingActive = false;
        flingVelocityAlbumsPerSec = 0f;
        flingLastMs = 0L;
        int nearest = getVisualCenterIndex();
        centerIndex = nearest;
        targetIndex = nearest;
    }

    /**
     * 2026-07-14 — Drag albums by finger motion (px → fractional offset).
     * Layman: swipe sideways and covers stick to your finger.
     * Finger left (dx negative) advances next album (positive offset).
     */
    public void dragByPixels(float dxPx) {
        if (itemCount <= 0 || !freeScrollActive) return;
        float pxPer = pixelsPerAlbum();
        if (pxPer < 1f) pxPer = 1f;
        offsetFreeByAlbums(-dxPx / pxPer);
    }

    /**
     * 2026-07-14 — Coast with deceleration then snap to nearest album.
     * Layman: flick and covers keep spinning a bit like iPhone Cover Flow.
     * velocityPxPerSec: positive = finger right; maps to previous albums.
     */
    public void fling(float velocityPxPerSec) {
        if (itemCount <= 0) return;
        freeScrollActive = false;
        float pxPer = pixelsPerAlbum();
        if (pxPer < 1f) pxPer = 1f;
        flingVelocityAlbumsPerSec = -velocityPxPerSec / pxPer;
        if (Math.abs(flingVelocityAlbumsPerSec) < FLING_STOP_ALBUMS) {
            snapNearestAlbum();
            return;
        }
        flingActive = true;
        flingLastMs = 0L;
        step = 0;
    }

    /**
     * 2026-07-14 — Ease into nearest album detent after free-scroll/fling.
     * Layman: covers settle on the one in the middle.
     */
    public void snapNearestAlbum() {
        if (itemCount <= 0) {
            freeScrollActive = false;
            flingActive = false;
            return;
        }
        freeScrollActive = false;
        flingActive = false;
        flingVelocityAlbumsPerSec = 0f;
        flingLastMs = 0L;
        int nearest = getVisualCenterIndex();
        float visual = getVisualOffset();
        if (Math.abs(visual - nearest) < 0.02f) {
            finishScrollAtTargetHard(nearest);
            return;
        }
        targetIndex = nearest;
        scrollAnimFromFixed = slideFrameInt;
        scrollAnimToFixed = nearest << 16;
        scrollAnimDurationMs = FREE_SNAP_MS;
        scrollAnimStartMs = 0L;
        scrollAnimPendingStart = true;
        step = scrollAnimToFixed >= scrollAnimFromFixed ? 1 : -1;
        if (scrollAnimToFixed == scrollAnimFromFixed) {
            finishScrollAtTargetHard(nearest);
        }
    }

    /**
     * 2026-07-14 — Wheel/key arrived: hard-snap free/fling then resume stepped scrollBy.
     * Layman: buttons always win if you rotate the wheel while dragging.
     * Reversal: leave free mode running under wheel (modes fight).
     */
    public void cancelFreeScrollForKeyInput() {
        if (!freeScrollActive && !flingActive) return;
        freeScrollActive = false;
        flingActive = false;
        flingVelocityAlbumsPerSec = 0f;
        flingLastMs = 0L;
        finishScrollAtTargetHard(getVisualCenterIndex());
    }

    /** True while finger drag or fling coast owns the carousel. */
    public boolean isFreeScrolling() {
        return freeScrollActive || flingActive;
    }

    /** Approx px for one album step — neighbor fan spacing. */
    public float pixelsPerAlbum() {
        CoverFlowLayout.Metrics m = metrics();
        float spacing = CoverFlowLayout.cxForSideRank(1, m);
        if (spacing < 40f) {
            spacing = m.displaySize * CoverFlowLayout.NEIGHBOR_CX_FRAC;
        }
        if (spacing < 40f) spacing = Math.max(40f, viewportW * 0.35f);
        return spacing;
    }

    private void offsetFreeByAlbums(float deltaAlbums) {
        if (itemCount <= 0 || deltaAlbums == 0f) return;
        float visual = getVisualOffset() + deltaAlbums;
        float max = Math.max(0, itemCount - 1);
        if (visual < 0f) visual = 0f;
        if (visual > max) visual = max;
        slideFrameInt = Math.round(visual * 65536f);
        int nearest = Math.round(visual);
        if (nearest < 0) nearest = 0;
        if (nearest > itemCount - 1) nearest = itemCount - 1;
        centerIndex = nearest;
        targetIndex = nearest;
        fade = 256;
    }

    private void finishScrollAtTargetHard(int index) {
        if (itemCount <= 0) {
            centerIndex = 0;
            targetIndex = 0;
            slideFrameInt = 0;
        } else {
            centerIndex = clamp(index, 0, itemCount - 1);
            targetIndex = centerIndex;
            slideFrameInt = centerIndex << 16;
        }
        step = 0;
        fade = 256;
        scrollAnimStartMs = 0L;
        scrollAnimPendingStart = false;
        scrollAnimDurationMs = SCROLL_MS;
        resetSlides(metrics());
    }

    public void tick(long nowMs) {
        if (flingActive) {
            updateFling(nowMs);
            return;
        }
        if (step == 0) return;
        if (scrollAnimPendingStart) {
            scrollAnimStartMs = nowMs;
            scrollAnimPendingStart = false;
        }
        updateScrollAnimation(nowMs);
    }

    public boolean isAnimating() {
        return step != 0 || freeScrollActive || flingActive;
    }

    /** True while carousel scroll animation is running (step != 0 or free/fling). */
    public boolean isCarouselScrolling() {
        return step != 0 || freeScrollActive || flingActive;
    }

    public int getScrollStep() {
        return step;
    }

    /** Queued carousel destination — visible to flow unit tests. */
    int getTargetIndex() {
        return targetIndex;
    }

    public int visibleSlotMin() {
        return centerIndex - VISIBLE_RADIUS - 1;
    }

    public int visibleSlotMax() {
        return centerIndex + VISIBLE_RADIUS + 1;
    }

    public static final class SlotTransform {
        public float centerX;
        public float centerY;
        public float width;
        public float height;
        public float alpha;
        public float rotationYDeg;
        public float zDepth;
        public float depthOrder;
        /** 0 = bright front rack, 1 = deep shadow at carousel edge. */
        public float shadowStrength;
        /** 0 = rotate on left edge, 1 = right edge — Classipod inner-edge pivot. */
        public float pivotXFrac = 0.5f;
    }

    /**
     * 2026-07-05 — Write transform into caller buffer (pooled pose scratch, no alloc).
     */
    public void slotTransformInto(SlotTransform out, int itemIndex, float viewW, float viewH,
            float ignoredVisual) {
        if (out == null) return;
        if (itemIndex < 0 || itemIndex >= itemCount) {
            out.alpha = 0f;
            return;
        }
        float rel = itemIndex - getVisualOffset();
        CoverFlowLayout.poseFromRelative(rel, metrics(), pooledPose);
        pooledPose.itemIndex = itemIndex;
        CoverFlowLayout.toSlotTransform(pooledPose, metrics(), out);
    }

    /** Returns a copy — safe for tests holding multiple transforms at once. */
    public SlotTransform slotTransform(int itemIndex, float viewW, float viewH, float ignoredVisual) {
        slotTransformInto(pooledSlotTransform, itemIndex, viewW, viewH, ignoredVisual);
        SlotTransform copy = new SlotTransform();
        copySlotTransform(copy, pooledSlotTransform);
        return copy;
    }

    private static void copySlotTransform(SlotTransform dst, SlotTransform src) {
        dst.centerX = src.centerX;
        dst.centerY = src.centerY;
        dst.width = src.width;
        dst.height = src.height;
        dst.alpha = src.alpha;
        dst.rotationYDeg = src.rotationYDeg;
        dst.zDepth = src.zDepth;
        dst.depthOrder = src.depthOrder;
        dst.shadowStrength = src.shadowStrength;
        dst.pivotXFrac = src.pivotXFrac;
    }

    public SlotTransform centerSlotTransform(float viewW, float viewH) {
        return slotTransform(getVisualCenterIndex(), viewW, viewH, 0f);
    }

    public int findIndexForKey(java.util.List<FlowItem> items, String focusKey) {
        if (focusKey == null || focusKey.isEmpty() || items == null) return -1;
        if (matchKeyIndex != null) {
            Integer exact = matchKeyIndex.get(focusKey);
            if (exact != null) return exact;
        }
        for (int i = 0; i < items.size(); i++) {
            FlowItem item = items.get(i);
            if (focusKey.equals(item.matchKey)) return i;
        }
        for (int i = 0; i < items.size(); i++) {
            FlowItem item = items.get(i);
            if (focusKey.equalsIgnoreCase(item.title)) return i;
            if (item.matchKey == null) continue;
            int pipe = item.matchKey.indexOf('|');
            if (pipe <= 0) continue;
            String itemAlbum = item.matchKey.substring(0, pipe);
            // Album-only key or playing artist differs from catalog primary artist.
            if (focusKey.equals(itemAlbum)) return i;
            int focusPipe = focusKey.indexOf('|');
            if (focusPipe > 0 && focusKey.substring(0, focusPipe).equals(itemAlbum)) return i;
        }
        return -1;
    }

    private void showNextSlide() {
        if (step == 0) {
            if (centerIndex < itemCount - 1) {
                targetIndex = centerIndex + 1;
                startAnimation();
            }
        } else if (step < 0) {
            targetIndex = centerIndex;
            step = 1;
            restartScrollSegment();
        } else {
            // Queue one album ahead of active target — never skip using stale centerIndex.
            int next = Math.min(targetIndex + 1, itemCount - 1);
            if (next != targetIndex) {
                targetIndex = next;
                restartScrollSegment();
            }
        }
    }

    private void showPreviousSlide() {
        if (step == 0) {
            if (centerIndex > 0) {
                targetIndex = centerIndex - 1;
                startAnimation();
            }
        } else if (step > 0) {
            targetIndex = centerIndex;
            step = -1;
            restartScrollSegment();
        } else {
            int next = Math.max(targetIndex - 1, 0);
            if (next != targetIndex) {
                targetIndex = next;
                restartScrollSegment();
            }
        }
    }

    private void startAnimation() {
        step = targetIndex > (slideFrameInt >> 16) ? 1 : -1;
        restartScrollSegment();
    }

    /** New ease segment from current frame toward queued targetIndex. */
    private void restartScrollSegment() {
        restartScrollSegment(0L);
    }

    private void restartScrollSegment(long nowMs) {
        scrollAnimFromFixed = slideFrameInt;
        scrollAnimToFixed = targetIndex << 16;
        scrollAnimDurationMs = SCROLL_MS;
        if (nowMs > 0L) {
            scrollAnimStartMs = nowMs;
            scrollAnimPendingStart = false;
        } else {
            scrollAnimStartMs = 0L;
            scrollAnimPendingStart = true;
        }
        step = scrollAnimToFixed >= scrollAnimFromFixed ? 1 : -1;
        if (scrollAnimToFixed == scrollAnimFromFixed) step = 0;
    }

    private void resetSlides(CoverFlowLayout.Metrics m) {
        if (m == null) m = metrics();
        centerSlide.itemIndex = centerIndex;
        centerSlide.cx = 0f;
        centerSlide.cy = 0f;
        centerSlide.angle = 0;
        centerSlide.alpha = 256;
        centerSlide.drawBucket = 3;
        centerSlide.sideRank = 0;

        for (int i = 0; i < CoverFlowLayout.SIDE_SLIDES; i++) {
            CoverFlowLayout.SlidePose si = leftSlides[i];
            si.itemIndex = centerIndex - 1 - i;
            si.angle = CoverFlowLayout.ITILT;
            si.cx = -CoverFlowLayout.cxForSideRank(i + 1, m);
            si.cy = m.offsetY;
            si.alpha = 256;
            si.drawBucket = 0;
            si.sideRank = i;

            si = rightSlides[i];
            si.itemIndex = centerIndex + 1 + i;
            si.angle = -CoverFlowLayout.ITILT;
            si.cx = CoverFlowLayout.cxForSideRank(i + 1, m);
            si.cy = m.offsetY;
            si.alpha = 256;
            si.drawBucket = 1;
            si.sideRank = i;
        }
    }

    private void updateScrollAnimation(long nowMs) {
        if (step == 0) return;

        long duration = scrollAnimDurationMs > 0L ? scrollAnimDurationMs : SCROLL_MS;
        long elapsed = nowMs - scrollAnimStartMs;
        float t = Math.min(1f, elapsed / (float) duration);
        float eased = easeOutCubic(t);
        slideFrameInt = scrollAnimFromFixed
                + Math.round((scrollAnimToFixed - scrollAnimFromFixed) * eased);

        // Rockbox edge fade hint during scroll.
        int pos = slideFrameInt & 0xffff;
        fade = pos / 256;

        if (t >= 1f) {
            finishScrollAtTarget();
            return;
        }

        // Retarget direction if user reversed queue mid-segment.
        if (targetIndex < (slideFrameInt >> 16) && step > 0) {
            step = -1;
            restartScrollSegment(nowMs);
        } else if (targetIndex > (slideFrameInt >> 16) && step < 0) {
            step = 1;
            restartScrollSegment(nowMs);
        }
    }

    /** 2026-07-14 — Integrate fling velocity with friction; snap when slow. */
    private void updateFling(long nowMs) {
        if (!flingActive) return;
        if (flingLastMs <= 0L) {
            flingLastMs = nowMs;
            return;
        }
        float dt = (nowMs - flingLastMs) / 1000f;
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f;
        flingLastMs = nowMs;
        float v = flingVelocityAlbumsPerSec;
        float sign = v >= 0f ? 1f : -1f;
        float mag = Math.abs(v) - FLING_DECEL_ALBUMS * dt;
        if (mag <= FLING_STOP_ALBUMS) {
            flingActive = false;
            snapNearestAlbum();
            return;
        }
        flingVelocityAlbumsPerSec = mag * sign;
        offsetFreeByAlbums(flingVelocityAlbumsPerSec * dt);
        float visual = getVisualOffset();
        if (visual <= 0.001f || visual >= itemCount - 1.001f) {
            flingActive = false;
            snapNearestAlbum();
        }
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private void finishScrollAtTarget() {
        centerIndex = targetIndex;
        slideFrameInt = targetIndex << 16;
        step = 0;
        fade = 256;
        scrollAnimStartMs = 0L;
        scrollAnimPendingStart = false;
        scrollAnimDurationMs = SCROLL_MS;
        resetSlides(metrics());
    }

    public int fillDrawOrder(int[] out) {
        return fillDrawOrder(out, null);
    }

    /** Fills draw list sorted back-to-front by {@link CoverFlowLayout} depthOrder. */
    public int fillDrawOrder(int[] out, float[] depthOut) {
        int raw = fillDrawOrderUnsorted(out);
        if (raw <= 1 || depthOut == null) return raw;
        CoverFlowLayout.Metrics m = metrics();
        for (int i = 0; i < raw; i++) {
            float rel = out[i] - getVisualOffset();
            CoverFlowLayout.poseFromRelative(rel, m, pooledPose);
            pooledPose.itemIndex = out[i];
            depthOut[i] = CoverFlowLayout.depthOrderFromPose(pooledPose, m);
        }
        for (int i = 1; i < raw; i++) {
            int idx = out[i];
            float depth = depthOut[i];
            int j = i;
            while (j > 0 && depthOut[j - 1] > depth) {
                out[j] = out[j - 1];
                depthOut[j] = depthOut[j - 1];
                j--;
            }
            out[j] = idx;
            depthOut[j] = depth;
        }
        return raw;
    }

    private int fillDrawOrderUnsorted(int[] out) {
        if (itemCount <= 0) return 0;
        float visual = getVisualOffset();
        int lo = Math.max(0, (int) Math.floor(visual) - CoverFlowLayout.SIDE_SLIDES - 1);
        int hi = Math.min(itemCount - 1, (int) Math.ceil(visual) + CoverFlowLayout.SIDE_SLIDES + 1);
        CoverFlowLayout.Metrics m = metrics();
        int n = 0;
        for (int idx = lo; idx <= hi && n < out.length; idx++) {
            CoverFlowLayout.poseFromRelative(idx - visual, m, pooledPose);
            if (pooledPose.alpha > 0) out[n++] = idx;
        }
        return n;
    }

    private CoverFlowLayout.SlidePose poseForItem(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) {
            CoverFlowLayout.SlidePose empty = new CoverFlowLayout.SlidePose();
            empty.itemIndex = -1;
            empty.alpha = 0;
            return empty;
        }
        CoverFlowLayout.poseFromRelative(itemIndex - getVisualOffset(), metrics(), pooledPose);
        pooledPose.itemIndex = itemIndex;
        return pooledPose;
    }

    /** Exposed for flip fan-out in {@link FlowView}. */
    CoverFlowLayout.SlidePose poseForItemPublic(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) return null;
        return poseForItem(itemIndex);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
