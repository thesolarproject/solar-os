package com.solar.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 2026-07-06 — Root app_process HUD: countdown chip + fullscreen Restarting during rescue exec.
 * Layman: Linux paints the message on screen even when Android's window manager is stuck.
 * Technical: polls sys.solar.rescue.* props; spawned by solar-rescue-daemon.sh / solar-rescue-exec.sh.
 * Reversal: bottom-chip only — drop FULLSCREEN_PROPERTY branch in pollRunnable + layout builder.
 */
public final class SolarRescueHudMain {

    private static final String TAG = "SolarRescueHudMain";
    private static final long POLL_MS = 100L;
    /** 2026-07-06 — solar-rescue-exec.sh sets while painting pre-reboot fullscreen message. */
    private static final String FULLSCREEN_PROPERTY = SolarRescueHoldState.FULLSCREEN_PROPERTY;

    private static Handler handler;
    private static Context appContext;
    private static WindowManager windowManager;
    private static TextView hudView;
    private static boolean hudFullscreen;

    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            appContext = obtainSystemContext();
            if (appContext == null) {
                System.err.println(TAG + ": no system context");
                System.exit(1);
            }
            HandlerThread ht = new HandlerThread("SolarRescueHud");
            ht.start();
            handler = new Handler(ht.getLooper());
            windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
            System.out.println("READY");
            System.out.flush();
            handler.post(pollRunnable);
            Looper.loop();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            boolean fullscreen = isFullscreenRestart();
            String text = fullscreen
                    ? appContext.getString(R.string.rescue_hold_restarting)
                    : SolarRescueHoldState.hudText(appContext);
            if (text != null) {
                showHud(text, fullscreen);
            } else if (!fullscreen) {
                hideHud();
            }
            if (handler != null) {
                handler.postDelayed(this, POLL_MS);
            }
        }
    };

    /** True when exec script arms fullscreen — survives WM glitches until reboot. */
    private static boolean isFullscreenRestart() {
        return SolarRescueHoldState.isHudRestarting()
                || "1".equals(readProperty(FULLSCREEN_PROPERTY, "0"));
    }

    private static String readProperty(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
            Object v = get.invoke(null, key, def);
            return v != null ? v.toString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** ActivityThread.systemMain — same bootstrap as GlobalOverlayTriggerMain. */
    private static Context obtainSystemContext() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object thread = atClass.getMethod("systemMain").invoke(null);
            Context ctx = (Context) atClass.getMethod("getSystemContext").invoke(thread);
            return ctx != null ? ctx.getApplicationContext() : null;
        } catch (Exception e) {
            System.err.println(TAG + ": context failed: " + e.getMessage());
            return null;
        }
    }

    private static void showHud(final String text, final boolean fullscreen) {
        if (windowManager == null || appContext == null) return;
        if (hudView != null && hudFullscreen != fullscreen) {
            hideHud();
        }
        if (hudView == null) {
            hudFullscreen = fullscreen;
            hudView = buildHudView(text, fullscreen);
            try {
                windowManager.addView(hudView, buildLayoutParams(fullscreen));
            } catch (Exception e) {
                System.err.println(TAG + ": addView failed: " + e.getMessage());
                hudView = null;
            }
            return;
        }
        hudView.post(new Runnable() {
            @Override
            public void run() {
                if (hudView == null) return;
                hudView.setText(text);
                if (fullscreen) {
                    hudView.setTextSize(42f);
                    hudView.setBackgroundColor(Color.BLACK);
                }
            }
        });
    }

    private static void hideHud() {
        if (windowManager == null || hudView == null) return;
        try {
            windowManager.removeView(hudView);
        } catch (Exception ignored) {}
        hudView = null;
        hudFullscreen = false;
    }

    private static TextView buildHudView(String text, boolean fullscreen) {
        TextView tv = new TextView(appContext);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        if (fullscreen) {
            tv.setTextSize(42f);
            tv.setBackgroundColor(Color.BLACK);
        } else {
            tv.setTextSize(36f);
            tv.setPadding(dp(24), dp(14), dp(24), dp(14));
            tv.setBackgroundColor(0xDD000000);
        }
        return tv;
    }

    private static WindowManager.LayoutParams buildLayoutParams(boolean fullscreen) {
        if (fullscreen) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_FULLSCREEN
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.OPAQUE);
            lp.gravity = Gravity.CENTER;
            return lp;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(36);
        return lp;
    }

    private static int dp(int v) {
        float d = appContext.getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}
