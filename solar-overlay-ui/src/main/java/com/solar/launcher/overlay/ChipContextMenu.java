package com.solar.launcher.overlay;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 2026-07-08 — Slim chip+list chrome for companion system overlay (not full ThemedContextMenu).
 * Layman: Back + icon chips + list rows that look like Solar’s quick menu.
 * Technical: OverlayTheme paint; resource IDs from this library’s R (merged into companion).
 * Was: companion TextView-only shell. Now: real chip bar for POWER / APP_MENU.
 * Reversal: delete; restore TextView paintInteractiveTier path only.
 */
public final class ChipContextMenu {

    /** Focus strip: back chip, horizontal quick icons, vertical list, media slider. */
    public enum FocusZone { BACK, QUICK_BAR, LIST, SLIDER }

    /** One quick-bar icon — visibility gate for Y1/Y2 / rooted / playing. */
    public static final class QuickItem {
        public final int iconResId;
        public final String label;
        public final boolean visible;

        public QuickItem(int iconResId, String label, boolean visible) {
            this.iconResId = iconResId;
            this.label = label != null ? label : "";
            this.visible = visible;
        }
    }

    /** List row pick. */
    public interface Listener {
        void onSelected(int index);
    }

    /** Quick chip / Back chip picks. */
    public interface QuickBarListener {
        void onQuickSelected(int index);

        void onBackActivated();
    }

    private final Context context;
    private ViewGroup root;
    private FrameLayout overlay;
    private LinearLayout panel;
    private LinearLayout titleRow;
    private FrameLayout backChip;
    private ImageView backIcon;
    private HorizontalScrollView quickBarScroll;
    private LinearLayout quickBarHost;
    private ScrollView itemsScroll;
    private LinearLayout itemsHost;
    private LinearLayout sliderRow;
    private TextView sliderLabel;
    private ProgressBar sliderBar;
    private TextView detailView;

    private QuickItem[] quickItems = new QuickItem[0];
    private String[] labels = new String[0];
    private boolean[] rowVisible;
    private boolean[] rowHeaders;
    private String[] stateTexts;
    private Listener listener;
    private QuickBarListener quickListener;
    private FocusZone focusZone = FocusZone.LIST;
    private int focusIndex;
    private int quickFocusIndex;
    private int quickReturnIndex = -1;
    private int rowHeightPx;
    private int panelWidthPx;
    private int panelBgColor;
    private boolean dialogStyle;
    private boolean showBack = true;
    private boolean showing;
    private boolean volumeQuickActive;
    private boolean brightnessQuickActive;
    private int volumeQuickIndex = -1;
    private int brightnessQuickIndex = -1;
    private int sliderMax = 15;
    private int sliderCur;

    public ChipContextMenu(Context context) {
        this.context = context.getApplicationContext();
    }

    /** True when panel is attached. */
    public boolean isShowing() {
        return showing && overlay != null && overlay.getParent() != null;
    }

    public FocusZone focusZone() {
        return focusZone;
    }

    public int quickFocusIndex() {
        return quickFocusIndex;
    }

    public int listFocusIndex() {
        return focusIndex;
    }

    /** Remember which chip opened the current list tier (Back returns here). */
    public void setQuickReturnIndex(int index) {
        quickReturnIndex = index;
    }

    public void setMediaSliderQuickIndices(int volumeIndex, int brightnessIndex) {
        volumeQuickIndex = volumeIndex;
        brightnessQuickIndex = brightnessIndex;
    }

    /** Drop panel from root; animate when transitions on (unless {@code animated} false). */
    public void dismiss() {
        dismiss(true);
    }

    /**
     * 2026-07-18 — Instant detach for re-show; animated for user Back/close.
     * Was: always instant removeView. Reversal: single dismiss() without animated flag.
     */
    public void dismiss(boolean animated) {
        final ViewGroup attach = root;
        final FrameLayout scrim = overlay;
        final View panelView = panel;
        Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (attach != null && scrim != null) {
                    try {
                        attach.removeView(scrim);
                    } catch (Exception ignored) {}
                }
                overlay = null;
                panel = null;
                titleRow = null;
                backChip = null;
                quickBarHost = null;
                itemsHost = null;
                sliderRow = null;
                showing = false;
                listener = null;
                quickListener = null;
            }
        };
        if (animated && panelView != null && OverlayModalTransition.enabled(context)) {
            OverlayModalTransition.animateDismissPanelOnly(panelView, finish);
        } else {
            // Cancel in-flight present/dismiss so re-show is clean.
            if (panelView != null) {
                try {
                    panelView.animate().cancel();
                } catch (Throwable ignored) {}
            }
            finish.run();
        }
    }

    /**
     * 2026-07-08 — Paint full chip chrome (quick bar + optional list).
     * dialogStyle skips quick bar — detail + buttons only (native ANR / USB).
     */
    public void show(ViewGroup attachRoot, String title, String detail, String[] itemLabels,
            boolean[] headers, boolean[] visible, Listener listListener,
            QuickItem[] quickBar, QuickBarListener qListener, boolean withQuickBar,
            boolean dialogMode) {
        dismiss(false);
        if (attachRoot == null) return;
        this.root = attachRoot;
        this.labels = itemLabels != null ? itemLabels : new String[0];
        this.rowHeaders = headers;
        this.rowVisible = visible;
        this.listener = listListener;
        this.quickListener = qListener;
        this.quickItems = quickBar != null ? quickBar : new QuickItem[0];
        this.dialogStyle = dialogMode;
        this.showBack = withQuickBar && !dialogMode;
        float density = context.getResources().getDisplayMetrics().density;
        rowHeightPx = (int) context.getResources().getDimension(R.dimen.y1_menu_item_height);
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int margin = (int) (10 * density);
        panelWidthPx = screenW > margin * 2 ? screenW - margin * 2 : screenW;
        // 2026-07-11 — Narrow/portrait overlay: ~90% width so panel is less landscape-wide.
        int shortSide = Math.min(screenW, context.getResources().getDisplayMetrics().heightPixels);
        if (shortSide <= 280) {
            int narrow = (int) (screenW * 0.9f);
            if (narrow > 0) panelWidthPx = narrow;
        }
        panelBgColor = OverlayThemeProvider.get().getContextMenuPanelColor();
        focusIndex = firstVisibleIndex();
        quickFocusIndex = firstVisibleQuickIndex();
        focusZone = labels.length == 0 ? FocusZone.QUICK_BAR : FocusZone.LIST;

        overlay = new FrameLayout(context);
        overlay.setBackgroundColor(0x00000000);
        overlay.setClickable(false);
        overlay.setFocusable(false);

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (8 * density);
        panel.setPadding(padH, (int) (8 * density), padH, (int) (6 * density));
        panel.setBackground(OverlayThemeProvider.get().buildContextMenuPanelDrawable(context));

        if (!dialogStyle) {
            titleRow = new LinearLayout(context);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            populateTitleRow(withQuickBar, density);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleLp.bottomMargin = labels.length > 0 ? (int) (4 * density) : 0;
            panel.addView(titleRow, titleLp);
        } else if (title != null && title.length() > 0) {
            TextView tit = new TextView(context);
            tit.setText(title);
            tit.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    context.getResources().getDimension(R.dimen.y1_menu_text_size) * 0.95f);
            OverlayThemeProvider.get().applyThemedTextStyle(tit,
                    OverlayThemeProvider.get().ensureReadableOnBackground(
                            OverlayThemeProvider.get().getDialogTextColor(), panelBgColor));
            tit.setPadding(0, 0, 0, (int) (4 * density));
            panel.addView(tit);
        }

        if (detail != null && detail.length() > 0) {
            detailView = new TextView(context);
            detailView.setText(detail);
            detailView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    context.getResources().getDimension(R.dimen.y1_menu_text_size) * 0.7f);
            OverlayThemeProvider.get().applyThemedTextStyle(detailView,
                    OverlayThemeProvider.get().contextMenuMutedText(
                            OverlayThemeProvider.get().getHintTextColor()));
            detailView.setPadding(0, 0, 0, (int) (6 * density));
            panel.addView(detailView);
        }

        itemsScroll = new ScrollView(context);
        itemsScroll.setFillViewport(false);
        itemsHost = new LinearLayout(context);
        itemsHost.setOrientation(LinearLayout.VERTICAL);
        rebuildListRows();
        itemsScroll.addView(itemsHost, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int maxH = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.42f);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.addView(itemsScroll, scrollLp);
        itemsScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(maxH, Math.max(rowHeightPx, labels.length * (rowHeightPx + 2)))));

        addSliderRow(density);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                panelWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.CENTER;
        overlay.addView(panel, panelLp);
        attachRoot.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        OverlayModalTransition.prepareModalPresentPanelOnly(panel);
        OverlayModalTransition.animatePresentPanelOnly(panel, null);
        showing = true;
        refreshChrome();
    }

    /** Swap list rows in place (Wi‑Fi/BT/Power drill, app-menu refresh). */
    public void replaceListContent(String[] itemLabels, boolean[] headers, boolean[] visible,
            String[] states, Listener listListener, boolean resetFocus) {
        this.labels = itemLabels != null ? itemLabels : new String[0];
        this.rowHeaders = headers;
        this.rowVisible = visible;
        this.stateTexts = states;
        this.listener = listListener;
        if (resetFocus) {
            focusIndex = firstVisibleIndex();
            focusZone = labels.length > 0 ? FocusZone.LIST : FocusZone.QUICK_BAR;
        } else if (focusIndex >= labels.length) {
            focusIndex = firstVisibleIndex();
        }
        rebuildListRows();
        refreshChrome();
    }

    /** Show volume/brightness slider under chips. */
    public void showSlider(String label, int max, int cur, boolean volumeMode) {
        volumeQuickActive = volumeMode;
        brightnessQuickActive = !volumeMode;
        sliderMax = Math.max(1, max);
        sliderCur = Math.max(0, Math.min(cur, sliderMax));
        if (sliderRow != null) {
            sliderRow.setVisibility(View.VISIBLE);
            if (sliderLabel != null) sliderLabel.setText(label != null ? label : "");
            if (sliderBar != null) {
                sliderBar.setMax(sliderMax);
                sliderBar.setProgress(sliderCur);
            }
        }
        focusZone = FocusZone.SLIDER;
        refreshChrome();
    }

    public void hideSlider() {
        volumeQuickActive = false;
        brightnessQuickActive = false;
        if (sliderRow != null) sliderRow.setVisibility(View.GONE);
        if (focusZone == FocusZone.SLIDER) {
            focusZone = labels.length > 0 ? FocusZone.LIST : FocusZone.QUICK_BAR;
        }
        refreshChrome();
    }

    public void updateSlider(int cur, int max) {
        sliderMax = Math.max(1, max);
        sliderCur = Math.max(0, Math.min(cur, sliderMax));
        if (sliderBar != null) {
            sliderBar.setMax(sliderMax);
            sliderBar.setProgress(sliderCur);
        }
    }

    public int sliderValue() {
        return sliderCur;
    }

    public void focusList() {
        if (labels.length == 0) return;
        focusZone = FocusZone.LIST;
        focusIndex = firstVisibleIndex();
        refreshChrome();
    }

    /**
     * 2026-07-14 — Focus a specific list row (touch first-tap / CoverFlow-style highlight).
     * Layman: highlight this menu line before OK.
     * Reversal: call focusList() only.
     */
    public void focusListAt(int index) {
        if (labels.length == 0) return;
        if (!isRowVisible(index) || isHeader(index)) {
            index = firstVisibleIndex();
        }
        focusZone = FocusZone.LIST;
        focusIndex = index;
        refreshChrome();
        scrollListIntoView();
    }

    public void focusQuick(int index) {
        if (!isVisibleQuick(index)) index = firstVisibleQuickIndex();
        quickFocusIndex = index;
        focusZone = FocusZone.QUICK_BAR;
        refreshChrome();
        scrollQuickIntoView();
    }

    /**
     * 2026-07-08 — Wheel / DPAD / confirm / back for companion key gate.
     * @return true when consumed
     */
    public boolean handleKeyDown(int keyCode) {
        if (!isShowing()) return false;
        if (isWheelUp(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return moveFocus(-1);
        }
        if (isWheelDown(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return moveFocus(1);
        }
        if (isSideLeft(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return moveHorizontal(-1);
        }
        if (isSideRight(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return moveHorizontal(1);
        }
        if (isConfirmKey(keyCode) || isBackKey(keyCode)) {
            return true;
        }
        return false;
    }

    /** Center/Back on KEY_UP — host applies OverlayCenterGrace before activate. */
    public boolean handleKeyUp(int keyCode) {
        if (!isShowing()) return false;
        if (isConfirmKey(keyCode)) {
            activateFocused();
            return true;
        }
        if (isBackKey(keyCode)) {
            handleBack();
            return true;
        }
        return false;
    }

    /** Confirm focused chip or list row. */
    public void activateFocused() {
        if (focusZone == FocusZone.BACK) {
            if (quickListener != null) quickListener.onBackActivated();
            return;
        }
        if (focusZone == FocusZone.QUICK_BAR) {
            if (quickListener != null && isVisibleQuick(quickFocusIndex)) {
                quickListener.onQuickSelected(quickFocusIndex);
            }
            return;
        }
        if (focusZone == FocusZone.SLIDER) {
            // Sliders adjust on wheel; center returns to quick chip that opened them.
            if (volumeQuickActive && volumeQuickIndex >= 0) {
                focusQuick(volumeQuickIndex);
            } else if (brightnessQuickActive && brightnessQuickIndex >= 0) {
                focusQuick(brightnessQuickIndex);
            }
            return;
        }
        if (focusZone == FocusZone.LIST && isRowVisible(focusIndex) && listener != null) {
            listener.onSelected(focusIndex);
        }
    }

    private void handleBack() {
        // 2026-07-14 — Slider sub-tier only: retreat to the chip that opened it.
        // Was: LIST→quick first (felt like Back did nothing). Now: top-level Back dismisses.
        // Reversal: restore LIST+quickItems branch that called focusQuick(ret).
        if (focusZone == FocusZone.SLIDER) {
            hideSlider();
            int ret = quickReturnIndex >= 0 ? quickReturnIndex : lastVisibleQuickIndex();
            if (ret >= 0) {
                focusQuick(ret);
                return;
            }
        }
        if (quickListener != null) {
            quickListener.onBackActivated();
        }
    }

    private boolean moveFocus(int delta) {
        if (focusZone == FocusZone.SLIDER) {
            nudgeSlider(delta);
            return true;
        }
        if (focusZone == FocusZone.QUICK_BAR || focusZone == FocusZone.BACK) {
            // Vertical: leave chips into list (down) or stay.
            if (delta > 0 && labels.length > 0) {
                focusList();
                return true;
            }
            if (delta < 0 && focusZone == FocusZone.QUICK_BAR && showBack) {
                focusZone = FocusZone.BACK;
                refreshChrome();
                return true;
            }
            return true;
        }
        if (focusZone == FocusZone.LIST) {
            if (delta < 0 && focusIndex == firstVisibleIndex() && quickItems.length > 0) {
                focusQuick(lastVisibleQuickIndex());
                return true;
            }
            int n = labels.length;
            if (n == 0) return true;
            int i = focusIndex;
            for (int step = 0; step < n; step++) {
                i = (i + delta + n) % n;
                if (isRowVisible(i) && !isHeader(i)) {
                    focusIndex = i;
                    refreshChrome();
                    scrollListIntoView();
                    return true;
                }
            }
        }
        return true;
    }

    private boolean moveHorizontal(int delta) {
        if (focusZone == FocusZone.SLIDER) {
            nudgeSlider(delta);
            return true;
        }
        if (focusZone == FocusZone.BACK) {
            if (delta > 0 && quickItems.length > 0) {
                focusQuick(firstVisibleQuickIndex());
            }
            return true;
        }
        if (focusZone == FocusZone.QUICK_BAR) {
            int next = stepQuick(quickFocusIndex, delta);
            if (next < 0 && delta < 0 && showBack) {
                focusZone = FocusZone.BACK;
                refreshChrome();
                return true;
            }
            if (next >= 0) {
                quickFocusIndex = next;
                refreshChrome();
                scrollQuickIntoView();
            }
            return true;
        }
        return false;
    }

    private void nudgeSlider(int delta) {
        int step = delta < 0 ? -1 : 1;
        sliderCur = Math.max(0, Math.min(sliderMax, sliderCur + step));
        if (sliderBar != null) sliderBar.setProgress(sliderCur);
    }

    private void populateTitleRow(boolean withQuick, float density) {
        if (showBack) {
            backChip = new FrameLayout(context);
            backIcon = new ImageView(context);
            backIcon.setImageResource(R.drawable.ic_back);
            backIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = (int) (rowHeightPx * 0.62f);
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconLp.gravity = Gravity.CENTER;
            backChip.addView(backIcon, iconLp);
            backChip.setContentDescription(context.getString(R.string.context_back));
            backChip.setMinimumWidth((int) (rowHeightPx * 1.05f));
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    withQuick ? LinearLayout.LayoutParams.WRAP_CONTENT
                            : LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeightPx, 0f);
            if (withQuick) chipLp.rightMargin = (int) (2 * density);
            titleRow.addView(backChip, chipLp);
            // 2026-07-14 — Touch: focus Back then confirm (same two-tap as list rows).
            attachTouchFocusConfirm(backChip, new Runnable() {
                @Override
                public void run() {
                    focusZone = FocusZone.BACK;
                    refreshChrome();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    activateFocused();
                }
            }, new Predicate() {
                @Override
                public boolean test() {
                    return focusZone == FocusZone.BACK;
                }
            });
        }
        if (withQuick) {
            // 2026-07-11 — Narrow viewports: two chip rows instead of one scroll strip.
            java.util.ArrayList<Integer> visibleIdx = new java.util.ArrayList<Integer>();
            for (int i = 0; i < quickItems.length; i++) {
                if (quickItems[i].visible) visibleIdx.add(Integer.valueOf(i));
            }
            int width = context.getResources().getDisplayMetrics().widthPixels;
            int height = context.getResources().getDisplayMetrics().heightPixels;
            boolean isPortrait = height > width;
            int shortSide = Math.min(width, height);
            boolean twoRow = isPortrait && shortSide <= 280 && visibleIdx.size() > 1;
            if (twoRow) {
                LinearLayout chipRows = new LinearLayout(context);
                chipRows.setOrientation(LinearLayout.VERTICAL);
                chipRows.setGravity(Gravity.CENTER_HORIZONTAL);
                int mid = (visibleIdx.size() + 1) / 2;
                for (int row = 0; row < 2; row++) {
                    LinearLayout chipRow = new LinearLayout(context);
                    chipRow.setOrientation(LinearLayout.HORIZONTAL);
                    chipRow.setGravity(Gravity.CENTER);
                    int start = row == 0 ? 0 : mid;
                    int end = row == 0 ? mid : visibleIdx.size();
                    for (int j = start; j < end; j++) {
                        int qi = visibleIdx.get(j).intValue();
                        chipRow.addView(createQuickChip(quickItems[qi], qi));
                    }
                    chipRows.addView(chipRow, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx));
                }
                quickBarHost = chipRows;
                quickBarScroll = null;
                LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                        showBack ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                        rowHeightPx * 2, showBack ? 1f : 0f);
                titleRow.addView(chipRows, qLp);
            } else {
                quickBarScroll = new HorizontalScrollView(context);
                quickBarScroll.setHorizontalScrollBarEnabled(false);
                quickBarScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
                quickBarHost = new LinearLayout(context);
                quickBarHost.setOrientation(LinearLayout.HORIZONTAL);
                quickBarHost.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                for (int i = 0; i < quickItems.length; i++) {
                    if (!quickItems[i].visible) continue;
                    quickBarHost.addView(createQuickChip(quickItems[i], i));
                }
                quickBarScroll.addView(quickBarHost, new HorizontalScrollView.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, rowHeightPx));
                LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(
                        showBack ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                        rowHeightPx, showBack ? 1f : 0f);
                titleRow.addView(quickBarScroll, qLp);
            }
        }
    }

    private FrameLayout createQuickChip(QuickItem item, int index) {
        float density = context.getResources().getDisplayMetrics().density;
        int iconSize = (int) (rowHeightPx * 0.62f);
        int chipW = (int) (rowHeightPx * 1.05f);
        FrameLayout chip = new FrameLayout(context);
        chip.setTag(Integer.valueOf(index));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipW, rowHeightPx);
        lp.setMargins((int) (1 * density), 0, (int) (1 * density), 0);
        chip.setLayoutParams(lp);
        ImageView icon = new ImageView(context);
        icon.setTag("quick_icon");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (item.iconResId != 0) icon.setImageResource(item.iconResId);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER;
        chip.addView(icon, iconLp);
        chip.setContentDescription(item.label);
        final int qi = index;
        // 2026-07-14 — Touch chip: first tap focuses, second activates (was key-only).
        attachTouchFocusConfirm(chip, new Runnable() {
            @Override
            public void run() {
                focusQuick(qi);
            }
        }, new Runnable() {
            @Override
            public void run() {
                activateFocused();
            }
        }, new Predicate() {
            @Override
            public boolean test() {
                return focusZone == FocusZone.QUICK_BAR && quickFocusIndex == qi;
            }
        });
        return chip;
    }

    private void addSliderRow(float density) {
        int sliderH = (int) (24 * density);
        sliderRow = new LinearLayout(context);
        sliderRow.setOrientation(LinearLayout.VERTICAL);
        sliderRow.setVisibility(View.GONE);
        sliderLabel = new TextView(context);
        sliderLabel.setGravity(Gravity.CENTER);
        sliderLabel.setSingleLine(true);
        sliderLabel.setEllipsize(TextUtils.TruncateAt.END);
        OverlayThemeProvider.get().applyThemedTextStyle(sliderLabel,
                OverlayThemeProvider.get().ensureReadableOnBackground(
                        OverlayThemeProvider.get().getDialogTextColor(), panelBgColor));
        sliderRow.addView(sliderLabel);
        sliderBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        sliderBar.setMax(sliderMax);
        sliderRow.addView(sliderBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, sliderH));
        panel.addView(sliderRow);
    }

    private void rebuildListRows() {
        if (itemsHost == null) return;
        itemsHost.removeAllViews();
        float menuTextPx = context.getResources().getDimension(R.dimen.y1_menu_text_size);
        int textPad = (int) context.getResources().getDimension(R.dimen.y1_menu_text_pad_left);
        for (int i = 0; i < labels.length; i++) {
            if (!isRowVisible(i)) continue;
            TextView row = new TextView(context);
            row.setText(labels[i]);
            row.setTag(Integer.valueOf(i));
            row.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    isHeader(i) ? menuTextPx * 0.85f : menuTextPx);
            row.setPadding(textPad, textPad, textPad, textPad);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            row.setMarqueeRepeatLimit(-1);
            if (stateTexts != null && i < stateTexts.length && stateTexts[i] != null
                    && stateTexts[i].length() > 0) {
                row.setText(labels[i] + "  " + stateTexts[i]);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, rowHeightPx);
            lp.setMargins(0, 1, 0, 1);
            itemsHost.addView(row, lp);
            if (!isHeader(i)) {
                final int rowIndex = i;
                // 2026-07-14 — Touch list: focus then confirm (match Solar ThemedContextMenu).
                attachTouchFocusConfirm(row, new Runnable() {
                    @Override
                    public void run() {
                        focusListAt(rowIndex);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        activateFocused();
                    }
                }, new Predicate() {
                    @Override
                    public boolean test() {
                        return focusZone == FocusZone.LIST && focusIndex == rowIndex;
                    }
                });
            }
        }
    }

    /**
     * 2026-07-14 — True on touchscreen devices (A5); companion APK has no DeviceFeatures.
     * Layman: finger menus need two-tap; wheel-only boxes skip.
     */
    private boolean touchConfirmEnabled() {
        try {
            return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        } catch (Exception e) {
            return false;
        }
    }

    /** Simple predicate without Java 8 deps (API 17). */
    private interface Predicate {
        boolean test();
    }

    /**
     * 2026-07-14 — First tap focuses; second tap runs activate.
     * Was: no finger listeners on companion chrome. Reversal: leave views key-only.
     */
    private void attachTouchFocusConfirm(final View view, final Runnable focus,
            final Runnable activate, final Predicate alreadyFocused) {
        if (view == null || !touchConfirmEnabled()) return;
        view.setClickable(true);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (alreadyFocused != null && alreadyFocused.test()) {
                    if (activate != null) activate.run();
                } else {
                    if (focus != null) focus.run();
                }
            }
        });
    }

    private void refreshChrome() {
        refreshBackChip();
        refreshQuickBar();
        refreshRows();
    }

    private void refreshBackChip() {
        if (backChip == null) return;
        boolean focused = focusZone == FocusZone.BACK;
        if (focused) {
            int w = backChip.getWidth() > 0 ? backChip.getWidth() : (int) (rowHeightPx * 1.05f);
            backChip.setBackground(OverlayThemeProvider.get().getMenuRowBackgroundScaled(
                    context.getResources(), true, w, rowHeightPx));
        } else {
            backChip.setBackground(null);
        }
        if (backIcon != null && backIcon.getDrawable() != null) {
            int c = focused ? textSelected() : textNormal();
            backIcon.setColorFilter(c, PorterDuff.Mode.SRC_ATOP);
        }
    }

    private void refreshQuickBar() {
        if (quickBarHost == null) return;
        java.util.ArrayList<View> chips = new java.util.ArrayList<View>();
        collectQuickChips(quickBarHost, chips);
        for (int c = 0; c < chips.size(); c++) {
            View chip = chips.get(c);
            Object tag = chip.getTag();
            if (!(tag instanceof Integer)) continue;
            int idx = ((Integer) tag).intValue();
            boolean focused = focusZone == FocusZone.QUICK_BAR && idx == quickFocusIndex;
            if (focused) {
                int w = chip.getWidth() > 0 ? chip.getWidth() : (int) (rowHeightPx * 1.05f);
                chip.setBackground(OverlayThemeProvider.get().getMenuRowBackgroundScaled(
                        context.getResources(), true, w, rowHeightPx));
            } else {
                chip.setBackground(null);
            }
            ImageView icon = (ImageView) chip.findViewWithTag("quick_icon");
            if (icon != null && icon.getDrawable() != null) {
                icon.setColorFilter(focused ? textSelected() : textNormal(),
                        PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    /** 2026-07-11 — Walk nested 2-row chip hosts. */
    private void collectQuickChips(ViewGroup host, java.util.ArrayList<View> out) {
        if (host == null || out == null) return;
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child == null) continue;
            if (child.getTag() instanceof Integer) {
                out.add(child);
            } else if (child instanceof ViewGroup) {
                collectQuickChips((ViewGroup) child, out);
            }
        }
    }

    private void refreshRows() {
        if (itemsHost == null) return;
        for (int c = 0; c < itemsHost.getChildCount(); c++) {
            View v = itemsHost.getChildAt(c);
            if (!(v instanceof TextView)) continue;
            Object tag = v.getTag();
            int idx = tag instanceof Integer ? ((Integer) tag).intValue() : -1;
            TextView tv = (TextView) v;
            boolean focused = focusZone == FocusZone.LIST && idx == focusIndex;
            boolean header = isHeader(idx);
            if (header) {
                OverlayThemeProvider.get().applyThemedTextStyle(tv,
                        OverlayThemeProvider.get().ensureReadableOnBackground(
                                OverlayThemeProvider.get().getSectionHeaderTextColor(),
                                panelBgColor));
                tv.setBackground(null);
                continue;
            }
            int w = panelWidthPx > 0 ? panelWidthPx : tv.getWidth();
            Drawable bg = OverlayThemeProvider.get().getMenuRowBackgroundScaled(
                    context.getResources(), focused, w, rowHeightPx);
            tv.setBackground(bg);
            OverlayThemeProvider.get().applyThemedTextStyle(tv,
                    focused ? textSelected() : textNormal());
            tv.setSelected(focused);
        }
    }

    private int textNormal() {
        return OverlayThemeProvider.get().contextMenuTextNormal(
                OverlayThemeProvider.get().getSettingMenuTextColorNormal(),
                OverlayThemeProvider.get().getSettingMenuTextColorSelected(),
                panelBgColor, true);
    }

    private int textSelected() {
        return OverlayThemeProvider.get().contextMenuTextSelected(
                OverlayThemeProvider.get().getSettingMenuTextColorSelected(), true);
    }

    /**
     * 2026-07-11 — Edge-only list scroll (iPod/Android). Was: always top-align focused row.
     * Mirror of FocusScrollHelper.computeEnsureVisibleScrollY (overlay lib cannot depend on app).
     * Hard-clip + instant scrollTo ticker (no smoothScrollTo highlight shunt).
     * Reversal: restore smoothScrollTo(0, v.getTop()) every focus move.
     */
    private void scrollListIntoView() {
        if (itemsScroll == null || itemsHost == null) return;
        int viewport = itemsScroll.getHeight();
        if (viewport <= 0) return;
        float density = itemsScroll.getResources().getDisplayMetrics().density;
        int pad = Math.max(1, (int) (2f * density));
        int scrollY = itemsScroll.getScrollY();
        int contentH = itemsHost.getHeight();
        int maxScroll = Math.max(0, contentH - viewport);
        for (int c = 0; c < itemsHost.getChildCount(); c++) {
            View v = itemsHost.getChildAt(c);
            Object tag = v.getTag();
            if (!(tag instanceof Integer) || ((Integer) tag).intValue() != focusIndex) continue;
            int childTop = v.getTop();
            int childBottom = v.getBottom();
            // 2026-07-11 — Hard clip only (match FocusScrollHelper); was pad-inset pre-scroll.
            int targetY = scrollY;
            if (childTop < scrollY) {
                targetY = Math.max(0, childTop - pad);
            } else if (childBottom > scrollY + viewport) {
                targetY = childBottom - viewport + pad;
            }
            if (targetY < 0) targetY = 0;
            if (targetY > maxScroll) targetY = maxScroll;
            if (targetY != scrollY) {
                // 2026-07-11 — Instant ticker; was smoothScrollTo / rapid gate.
                itemsScroll.scrollTo(0, targetY);
            }
            return;
        }
    }

    private void scrollQuickIntoView() {
        if (quickBarScroll == null || quickBarHost == null) return;
        for (int i = 0; i < quickBarHost.getChildCount(); i++) {
            View chip = quickBarHost.getChildAt(i);
            Object tag = chip.getTag();
            if (tag instanceof Integer && ((Integer) tag).intValue() == quickFocusIndex) {
                final View target = chip;
                quickBarScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        int x = target.getLeft() - quickBarScroll.getWidth() / 3;
                        quickBarScroll.smoothScrollTo(Math.max(0, x), 0);
                    }
                });
                return;
            }
        }
    }

    private int firstVisibleIndex() {
        for (int i = 0; i < labels.length; i++) {
            if (isRowVisible(i) && !isHeader(i)) return i;
        }
        for (int i = 0; i < labels.length; i++) {
            if (isRowVisible(i)) return i;
        }
        return 0;
    }

    private boolean isRowVisible(int i) {
        if (i < 0 || i >= labels.length) return false;
        if (rowVisible == null) return true;
        return i < rowVisible.length && rowVisible[i];
    }

    private boolean isHeader(int i) {
        return rowHeaders != null && i >= 0 && i < rowHeaders.length && rowHeaders[i];
    }

    private int firstVisibleQuickIndex() {
        for (int i = 0; i < quickItems.length; i++) {
            if (quickItems[i].visible) return i;
        }
        return 0;
    }

    private int lastVisibleQuickIndex() {
        for (int i = quickItems.length - 1; i >= 0; i--) {
            if (quickItems[i].visible) return i;
        }
        return -1;
    }

    private boolean isVisibleQuick(int i) {
        return i >= 0 && i < quickItems.length && quickItems[i].visible;
    }

    private int stepQuick(int from, int delta) {
        int n = quickItems.length;
        if (n == 0) return -1;
        int i = from;
        for (int step = 0; step < n; step++) {
            i = i + delta;
            if (i < 0 || i >= n) return -1;
            if (quickItems[i].visible) return i;
        }
        return -1;
    }

    /**
     * Y1/Y2 scrollwheel → list up.
     * 2026-07-10 — Match Y1InputKeys: MEDIA_PLAY (126) and legacy DPAD_UP (19).
     */
    public static boolean isWheelUp(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == 19
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88;
    }

    /**
     * Scrollwheel other way → list down.
     * 2026-07-10 — Match Y1InputKeys: MEDIA_PAUSE (127) and legacy DPAD_DOWN (20).
     */
    public static boolean isWheelDown(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == 20
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87;
    }

    /** Side Prev — horizontal chip left (DPAD_LEFT). */
    public static boolean isSideLeft(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == 21;
    }

    /** Side Next — horizontal chip right (DPAD_RIGHT). */
    public static boolean isSideRight(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == 22;
    }

    public static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == 23
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == 66
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == 79;
    }

    public static boolean isBackKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_BACK || keyCode == 4;
    }

    /** Volume glyph by level for chip icon swaps. */
    public static int volumeIconResForLevel(int cur, int max) {
        if (max <= 0 || cur <= 0) return R.drawable.ic_volume_mute;
        float r = (float) cur / (float) max;
        if (r < 0.34f) return R.drawable.ic_volume_low;
        if (r < 0.67f) return R.drawable.ic_volume_mid;
        return R.drawable.ic_volume_high;
    }

    public static int brightnessIconResForLevel(int cur, int max) {
        if (max <= 0 || cur <= 0) return R.drawable.ic_brightness_empty;
        float r = (float) cur / (float) max;
        if (r < 0.5f) return R.drawable.ic_brightness_half;
        return R.drawable.ic_brightness;
    }

    /**
     * 2026-07-11 — Show volume/lock chips when not Y2 (Y1 + A5).
     * Was: Y1-only via !y2/!mt6582. Now: also Timmkoo A5 tokens.
     */
    public static boolean isY1Device() {
        String model = Build.MODEL != null ? Build.MODEL.toLowerCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toLowerCase() : "";
        String manu = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        if (model.contains("timmkoo") || manu.contains("timmkoo") || brand.contains("timmkoo")
                || model.equals("a5") || model.contains("a5-")) {
            return true; // A5 needs overlay volume/lock chips like Y1
        }
        if (model.contains("y2") || device.contains("y2")) return false;
        String board = Build.BOARD != null ? Build.BOARD.toLowerCase() : "";
        return !board.contains("mt6582");
    }
}
