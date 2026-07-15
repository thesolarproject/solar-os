package com.solar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 2026-07-14 — Portrait splash that preloads MainActivity off the UI thread on weak A5 SoCs.
 * Layman: show a tall Solar title first; load the heavy home screen in the background.
 * Tech: Class.forName on worker; startActivity on main — avoids ANR from sync MainActivity resolve.
 * Reversal: delete; put MAIN/LAUNCHER back on MainActivity; remove preload thread.
 */
public class SolarLaunchActivity extends Activity {

    private static final String TAG = "SolarDebugB4208e";
    private static final String MAIN = "com.solar.launcher.MainActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean handedOff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // #region agent log
        Log.e(TAG, "SolarLaunchActivity.onCreate ENTER isA5=" + DeviceFeatures.isA5());
        // #endregion
        if (DeviceFeatures.isA5()) {
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } catch (Exception ignored) {}
            LandscapeOrientationGuard.enforceA5Orientation(this);
        } else {
            try {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } catch (Exception ignored) {}
        }
        super.onCreate(savedInstanceState);
        setContentView(buildSplash());
        // #region agent log
        try {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("isA5", DeviceFeatures.isA5());
            d.put("dispW", dm.widthPixels);
            d.put("dispH", dm.heightPixels);
            d.put("req", getRequestedOrientation());
            DebugB4208eLog.log("SolarLaunchActivity.onCreate", "splash shown", "G,H", d);
            Log.e(TAG, "splash " + dm.widthPixels + "x" + dm.heightPixels);
        } catch (Exception ignored) {}
        // #endregion
        // 2026-07-14 — Resolve MainActivity off main; start when Class.forName returns.
        final Intent extras = getIntent();
        new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = android.os.SystemClock.uptimeMillis();
                Throwable err = null;
                try {
                    Class.forName(MAIN);
                } catch (Throwable t) {
                    err = t;
                }
                final long ms = android.os.SystemClock.uptimeMillis() - t0;
                final Throwable fail = err;
                // #region agent log
                Log.e(TAG, "MainActivity preload ms=" + ms + " err=" + fail);
                // #endregion
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        handOffToMain(extras, ms, fail);
                    }
                });
            }
        }, "SolarMainPreload").start();
    }

    /** Tall/wide splash matching A5 portrait or Y1 landscape while MainActivity dex resolves. */
    private FrameLayout buildSplash() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        TextView tv = new TextView(this);
        tv.setText(DeviceFeatures.isA5() ? "Solar" : "Solar");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, DeviceFeatures.isA5() ? 22f : 28f);
        tv.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        root.addView(tv, lp);
        return root;
    }

    /** Start MainActivity once its class is resident; keep splash on failure. */
    private void handOffToMain(Intent from, long preloadMs, Throwable fail) {
        if (handedOff || isFinishing()) return;
        handedOff = true;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("preloadMs", preloadMs);
            d.put("fail", fail != null ? String.valueOf(fail) : "");
            DebugB4208eLog.log("SolarLaunchActivity.handOffToMain", "starting MainActivity", "G", d);
        } catch (Exception ignored) {}
        // #endregion
        if (fail != null) {
            Log.e(TAG, "preload failed", fail);
            return;
        }
        try {
            Intent i = new Intent();
            i.setClassName(getPackageName(), MAIN);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (from != null && from.getExtras() != null) {
                i.putExtras(from);
            }
            startActivity(i);
            finish();
        } catch (Throwable t) {
            Log.e(TAG, "handOff startActivity failed", t);
            handedOff = false;
        }
    }
}
