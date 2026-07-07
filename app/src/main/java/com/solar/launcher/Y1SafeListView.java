package com.solar.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * API 17 MT6572 crashes in View.onDrawScrollBars / ScrollBarDrawable.setAlpha on ListView draw.
 * ponytail: same workaround as {@link Y1SafeScrollView} — dispatchDraw only on Jelly Bean.
 */
public class Y1SafeListView extends ListView {

    public Y1SafeListView(Context context) {
        super(context);
        init();
    }

    public Y1SafeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Y1SafeListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        Y1ScrollIndicators.applyVerticalListView(this);
        // 2026-07-05: API 17 MT6572 NPE in View.onDrawScrollBars when smooth scrollbars draw.
        setSmoothScrollbarEnabled(false);
    }

    /** 2026-07-05: Every adapter row must use AbsListView.LayoutParams — wrap to coerce at source. */
    @Override
    public void setAdapter(ListAdapter adapter) {
        if (adapter != null && !(adapter instanceof AbsListViewRowParamsWrapper)) {
            adapter = new AbsListViewRowParamsWrapper(adapter);
        }
        super.setAdapter(adapter);
    }

    /** 2026-07-05: Header rows from createListButton also need ListView-safe params. */
    @Override
    public void addHeaderView(View v, Object data, boolean isSelectable) {
        ListViewRowParams.ensure(v);
        super.addHeaderView(v, data, isSelectable);
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            super.draw(canvas);
        } catch (Throwable t) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("sdk", android.os.Build.VERSION.SDK_INT);
                d.put("w", getWidth());
                d.put("h", getHeight());
                DebugLibraryMenuLog.logError("Y1SafeListView.draw", "draw fallback", "H1", t, d);
            } catch (Exception ignored) {}
            // #endregion
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.save();
            canvas.clipRect(scrollX, scrollY, scrollX + getWidth(), scrollY + getHeight());
            super.dispatchDraw(canvas);
            canvas.restore();
        }
    }
}
