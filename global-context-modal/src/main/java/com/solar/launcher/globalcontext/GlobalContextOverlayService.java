package com.solar.launcher.globalcontext;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.solar.input.policy.StaleOverlayGate;

/**
 * 2026-07-05 — Companion WM overlay shell when Solar :overlay is unavailable.
 * Layman: fallback quick-menu dim layer if Solar was force-stopped.
 * Technical: :overlay process; Solar SolarOverlayService owns full menu when Solar runs.
 * Reversal: delete; Xposed targets Solar overlay only.
 */
public final class GlobalContextOverlayService extends Service {

    private static final String TAG = "GlobalContextOverlay";
    /** Placeholder shell auto-dismiss — companion must not block forever on "Loading…". 2026-07-05 */
    private static final long PLACEHOLDER_WATCHDOG_MS = 2800L;

    private WindowManager windowManager;
    private FrameLayout overlayRoot;
    private volatile boolean openInFlight;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable placeholderWatchdog = new Runnable() {
        @Override
        public void run() {
            if (overlayRoot == null) return;
            Log.i(TAG, "placeholder watchdog — dismissing stuck Quick menu shell");
            tearDownOverlay();
            retrySolarOverlay();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if (CompanionOverlayTriggers.ACTION_DISMISS_OVERLAY.equals(action)) {
            tearDownOverlay();
            return START_NOT_STICKY;
        }
        if (CompanionOverlayTriggers.ACTION_OVERLAY_KEEPALIVE.equals(action)) {
            ThemeReader.refresh(getApplicationContext());
            return START_STICKY;
        }
        if (CompanionOverlayTriggers.ACTION_SHOW_OVERLAY_POWER.equals(action)) {
            StaleOverlayGate.clearIfNeeded();
            if (StaleOverlayGate.isActiveOrOpening() && overlayRoot != null) {
                refreshPowerShell();
                return START_NOT_STICKY;
            }
            if (openInFlight && overlayRoot == null) {
                return START_NOT_STICKY;
            }
            openInFlight = true;
            try {
                setOverlayOpening(true);
                showPowerOverlayShell();
            } catch (Throwable t) {
                Log.e(TAG, "showPowerOverlayShell failed", t);
                tearDownOverlay();
            } finally {
                openInFlight = false;
            }
            return START_NOT_STICKY;
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        tearDownOverlay();
        super.onDestroy();
    }

    /** Minimal shell — NOT_FOCUSABLE like Solar overlay; keys forwarded by Xposed. */
    private void showPowerOverlayShell() {
        tearDownOverlay();
        ThemeReader.refresh(getApplicationContext());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            setOverlayOpening(false);
            return;
        }
        overlayRoot = new FrameLayout(this);
        // 2026-07-06 — transparent WM host; themed panel only (no dim scrim).
        overlayRoot.setBackgroundColor(0x00000000);
        overlayRoot.setClickable(false);
        overlayRoot.setFocusable(false);
        overlayRoot.setFocusableInTouchMode(false);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(ThemeReader.backgroundColor());
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText(safeString(R.string.overlay_power_title, "Quick menu"));
        title.setTextColor(ThemeReader.foregroundColor());
        title.setTextSize(18f);
        panel.addView(title);

        TextView body = new TextView(this);
        body.setText(formatPowerSnapshotRows(fetchLivePowerSnapshot()));
        body.setTextColor(ThemeReader.accentColor());
        body.setTextSize(14f);
        body.setPadding(0, dp(8), 0, 0);
        panel.addView(body);

        FrameLayout.LayoutParams lpPanel = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lpPanel.gravity = Gravity.CENTER;
        overlayRoot.addView(panel, lpPanel);

        int wmType = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                wmType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            windowManager.addView(overlayRoot, lp);
            setOverlayActive(true);
            setOverlayUi(true);
            mainHandler.removeCallbacks(placeholderWatchdog);
            mainHandler.postDelayed(placeholderWatchdog, PLACEHOLDER_WATCHDOG_MS);
        } catch (Exception e) {
            Log.e(TAG, "addView failed", e);
            overlayRoot = null;
            setOverlayOpening(false);
            setOverlayActive(false);
            setOverlayUi(false);
        }
    }

    private String safeString(int resId, String fallback) {
        try {
            return getString(resId);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void refreshPowerShell() {
        if (overlayRoot == null) return;
        ThemeReader.refresh(getApplicationContext());
        android.view.View panel = overlayRoot.getChildAt(0);
        if (panel instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) panel;
            if (ll.getChildCount() > 1 && ll.getChildAt(1) instanceof TextView) {
                ((TextView) ll.getChildAt(1)).setText(formatPowerSnapshotRows(fetchLivePowerSnapshot()));
            }
        }
    }

    private void tearDownOverlay() {
        mainHandler.removeCallbacks(placeholderWatchdog);
        setOverlayActive(false);
        setOverlayUi(false);
        setOverlayOpening(false);
        if (windowManager != null && overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Exception ignored) {}
        }
        overlayRoot = null;
    }

    private static void setOverlayOpening(boolean opening) {
        SysPropHelper.set(StaleOverlayGate.OPENING_PROPERTY, opening ? "1" : "0");
        if (opening) {
            SysPropHelper.set(StaleOverlayGate.OPENING_AT_PROPERTY,
                    String.valueOf(StaleOverlayGate.elapsedRealtime()));
        } else {
            SysPropHelper.set(StaleOverlayGate.OPENING_AT_PROPERTY, "0");
        }
    }

    private static void setOverlayActive(boolean active) {
        SysPropHelper.set(StaleOverlayGate.ACTIVE_PROPERTY, active ? "1" : "0");
        if (active) {
            SysPropHelper.set(StaleOverlayGate.ACTIVE_AT_PROPERTY,
                    String.valueOf(StaleOverlayGate.elapsedRealtime()));
        } else {
            SysPropHelper.set(StaleOverlayGate.ACTIVE_AT_PROPERTY, "0");
        }
    }

    private static void setOverlayUi(boolean visible) {
        SysPropHelper.set(StaleOverlayGate.UI_PROPERTY, visible ? "1" : "0");
    }

    private static final String KEY_LABELS = "labels";
    private static final String KEY_STATES = "states";

    private android.os.Bundle fetchLivePowerSnapshot() {
        SolarOverlayStateClient client = new SolarOverlayStateClient();
        android.os.Bundle snap = client.fetchPowerSnapshot(this);
        return snap != null ? snap : fallbackPowerSnapshot();
    }

    private android.os.Bundle fallbackPowerSnapshot() {
        android.os.Bundle b = new android.os.Bundle();
        b.putStringArray(KEY_LABELS, new String[] {"Home", "Wi‑Fi", "Bluetooth"});
        return b;
    }

    private String formatPowerSnapshotRows(android.os.Bundle snap) {
        if (snap == null) return safeString(R.string.overlay_power_placeholder, "Loading…");
        String[] labels = snap.getStringArray(KEY_LABELS);
        boolean[] states = snap.getBooleanArray(KEY_STATES);
        if (labels == null || labels.length == 0) {
            return safeString(R.string.overlay_power_placeholder, "Loading…");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            if (states != null && i < states.length && !states[i]) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(labels[i]);
        }
        return sb.length() > 0 ? sb.toString()
                : safeString(R.string.overlay_power_placeholder, "Loading…");
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    /** Nudge Solar IPC when companion placeholder needs refresh — Solar overlay optional fallback. */
    private void retrySolarOverlay() {
        try {
            android.content.Intent bind = new android.content.Intent(
                    SolarOverlayStateClient.ACTION_BIND);
            bind.setComponent(new android.content.ComponentName(
                    "com.solar.launcher", "com.solar.launcher.SolarOverlayStateService"));
            startService(bind);
        } catch (Exception ignored) {}
    }
}
