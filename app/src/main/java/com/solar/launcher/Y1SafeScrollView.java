package com.solar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

/**
 * API 17 MT6572 crashes in View.onDrawScrollBars / ScrollBarDrawable.setAlpha.
 * ponytail: draw content only on Jelly Bean; full draw on API 19+.
 * 2026-07-11 — Edge-only requestChildRectangleOnScreen so focus can walk the viewport
 * (iPod / STB); was platform scroll-on-focus locking the highlight. Reversal: super.*.
 * 2026-07-11 — Hard-clip trigger only (no pad pre-scroll); pad used when aligning after clip.
 * 2026-07-11 — Instant scrollTo ticker (no smoothScrollTo) so focus chrome does not shunt.
 * FocusScrollHelper.ensureChildVisible is the primary menu path; this covers platform focus.
 */
public class Y1SafeScrollView extends ScrollView {

    private static final float EDGE_PAD_DP = 2f;
    private final Rect edgeRect = new Rect();

    public Y1SafeScrollView(Context context) {
        super(context);
        init();
    }

    public Y1SafeScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Y1SafeScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setScrollbarFadingEnabled(false);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
    }

    /**
     * 2026-07-11 — Scroll only when focused child rect clips past top/bottom.
     * Layman: move the highlight across the screen before the page moves.
     * Technical: blocks ScrollView focus-scroll that re-pinned every requestFocus.
     */
    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        if (child == null || rect == null) return false;
        int viewport = getHeight();
        if (viewport <= 0) return false;
        float density = getResources().getDisplayMetrics().density;
        int pad = Math.max(1, (int) (EDGE_PAD_DP * density));
        edgeRect.set(rect);
        offsetDescendantRectToMyCoords(child, edgeRect);
        // Content coords (same space as getScrollY()).
        int scrollY = getScrollY();
        int top = edgeRect.top;
        int bottom = edgeRect.bottom;
        int screenTop = scrollY;
        int screenBottom = scrollY + viewport;
        if (top >= screenTop && bottom <= screenBottom) {
            return false;
        }
        int targetY = scrollY;
        if (top < screenTop) {
            targetY = Math.max(0, top - pad);
        } else if (bottom > screenBottom) {
            targetY = bottom - viewport + pad;
        }
        View content = getChildCount() > 0 ? getChildAt(0) : null;
        int maxScroll = content != null ? Math.max(0, content.getHeight() - viewport) : targetY;
        if (targetY < 0) targetY = 0;
        if (targetY > maxScroll) targetY = maxScroll;
        if (targetY == scrollY) return false;
        // 2026-07-11 — Instant ticker; was immediate?scrollTo:smoothScrollTo (highlight shunt).
        scrollTo(0, targetY);
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            super.draw(canvas);
        } catch (Throwable t) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.save();
            canvas.clipRect(scrollX, scrollY, scrollX + getWidth(), scrollY + getHeight());
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }
}
