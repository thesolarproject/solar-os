package com.solar.launcher;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

/**
 * 2026-07-05 — Force GPU rendering + disable HW overlays on Y1/Y2 for smoother Solar UI.
 * Layman: turn on the same Developer Options graphics toggles that help wheel menus scroll smoothly.
 * Technical: {@code force_gpu_rendering}/{@code force_hw_ui} in Settings + SurfaceFlinger 1008 via su.
 * Sanity: disable-overlays is normally a debug switch — we enable it on MTK for stable overlay compositing.
 * Reversal: stop calling from boot/prep paths; remove enable-gpu-performance.sh from 99SolarInit.sh.
 */
public final class GraphicsPerformancePolicy {

    private static final String TAG = "GraphicsPerfPolicy";
    /** Developer option — force 2D drawing on GPU (API 17+ global/system table). */
    static final String KEY_FORCE_GPU = "force_gpu_rendering";
    /** Legacy alias on JB/KK developer settings screens. */
    static final String KEY_FORCE_HW_UI = "force_hw_ui";
    /** Settings mirror for disable overlays where SF call is unavailable. */
    static final String KEY_DISABLE_OVERLAYS = "disable_overlays";

    private GraphicsPerformancePolicy() {}

    /** Boot / prep entry — idempotent, off main thread. */
    public static void ensureAsync(Context context) {
        if (context == null || !appliesToDevice()) return;
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                apply(app);
            }
        }, "GraphicsPerfPolicy").start();
    }

    /** Platform prep ladder — blocking apply when root shell is ready. */
    public static void applySync(Context context) {
        if (context == null || !appliesToDevice()) return;
        apply(context.getApplicationContext());
    }

    /** Y1/Y2 on Android 4.2.2 / 4.4.4 only — matches LargeFontAccessibilitySuppressor scope. */
    static boolean appliesToDevice() {
        if (!DeviceFeatures.isY1() && !DeviceFeatures.isY2()) return false;
        int sdk = Build.VERSION.SDK_INT;
        return sdk == Build.VERSION_CODES.JELLY_BEAN_MR1
                || sdk == Build.VERSION_CODES.KITKAT;
    }

    /** Apply GPU settings when not already enabled — safe to call every boot. */
    static void apply(Context context) {
        if (context == null) return;
        boolean gpuOk = isGpuRenderingEnabled(context);
        boolean overlayOk = isOverlaysDisabled(context);
        if (gpuOk && overlayOk) return;

        if (!gpuOk) {
            enableGpuRendering(context);
        }
        if (!overlayOk) {
            disableHardwareOverlays(context);
        }
        SolarLog.i(TAG, "graphics perf applied gpu=" + isGpuRenderingEnabled(context)
                + " overlaysDisabled=" + isOverlaysDisabled(context));
    }

    /** True when force_gpu_rendering or force_hw_ui is set. */
    static boolean isGpuRenderingEnabled(Context context) {
        return readIntSetting(context, KEY_FORCE_GPU, 0) == 1
                || readIntSetting(context, KEY_FORCE_HW_UI, 0) == 1;
    }

    /** True when disable_overlays setting is persisted. */
    static boolean isOverlaysDisabled(Context context) {
        return readIntSetting(context, KEY_DISABLE_OVERLAYS, 0) == 1;
    }

    static void enableGpuRendering(Context context) {
        writeIntSetting(context, KEY_FORCE_GPU, 1);
        writeIntSetting(context, KEY_FORCE_HW_UI, 1);
        if (DeviceFeatures.canRunRootShell()) {
            shellSettingsPut("global", KEY_FORCE_GPU, "1");
            shellSettingsPut("system", KEY_FORCE_GPU, "1");
            shellSettingsPut("system", KEY_FORCE_HW_UI, "1");
            shellSettingsPut("global", KEY_FORCE_HW_UI, "1");
        }
    }

    static void disableHardwareOverlays(Context context) {
        if (DeviceFeatures.canRunRootShell()) {
            // SurfaceFlinger transaction — same as AOSP disable_overlays dev option.
            RootShell.run("service call SurfaceFlinger 1008 i32 1 2>/dev/null || true");
            shellSettingsPut("global", KEY_DISABLE_OVERLAYS, "1");
            shellSettingsPut("system", KEY_DISABLE_OVERLAYS, "1");
            runBundledScript(context);
        } else {
            writeIntSetting(context, KEY_DISABLE_OVERLAYS, 1);
        }
    }

    /** Run ROM-staged shell when present — matches 99SolarInit.sh path. */
    private static void runBundledScript(Context context) {
        String staged = "/system/etc/solar/enable-gpu-performance.sh";
        if (RootShell.runCapture("test -f " + staged + " && echo yes").contains("yes")) {
            RootShell.run("sh " + staged);
            return;
        }
        try {
            java.io.File tmp = new java.io.File(context.getCacheDir(), "enable-gpu-performance.sh");
            java.io.InputStream in = context.getAssets().open("y1/enable-gpu-performance.sh");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) out.write(buf, 0, n);
            }
            in.close();
            out.close();
            tmp.setExecutable(true, false);
            RootShell.run("sh " + tmp.getAbsolutePath());
        } catch (Exception ignored) {}
    }

    private static void shellSettingsPut(String table, String key, String value) {
        RootShell.run("settings put " + table + " " + key + " " + value + " 2>/dev/null || true");
    }

    private static int readIntSetting(Context context, String key, int def) {
        try {
            int v = Settings.System.getInt(context.getContentResolver(), key, def);
            if (v != def) return v;
        } catch (Exception ignored) {}
        try {
            return Settings.Global.getInt(context.getContentResolver(), key, def);
        } catch (Exception ignored) {}
        if (DeviceFeatures.canRunRootShell()) {
            String sys = RootShell.runCapture("settings get system " + key + " 2>/dev/null");
            if (sys != null && sys.trim().equals("1")) return 1;
            String glob = RootShell.runCapture("settings get global " + key + " 2>/dev/null");
            if (glob != null && glob.trim().equals("1")) return 1;
        }
        return def;
    }

    private static void writeIntSetting(Context context, String key, int value) {
        try {
            Settings.System.putInt(context.getContentResolver(), key, value);
        } catch (Exception ignored) {}
        try {
            Settings.Global.putInt(context.getContentResolver(), key, value);
        } catch (Exception ignored) {}
    }
}
