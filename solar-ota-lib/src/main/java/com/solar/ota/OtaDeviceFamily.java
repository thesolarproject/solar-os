package com.solar.ota;

import android.os.Build;
import android.util.DisplayMetrics;

import java.util.Locale;

/**
 * 2026-07-05 — Y1/Y2 probe for OTA APK variant filtering.
 * 2026-07-16 — Aligned with DeviceFeatures: A5 QVGA, SoC, shared 360p via SDK, ROM pin.
 */
public final class OtaDeviceFamily {
    private static final String PROP_DEVICE_FAMILY = "persist.solar.device_family";
    private static volatile String cachedFamily;

    private OtaDeviceFamily() {}

    public static String deviceFamily() {
        if (cachedFamily != null) return cachedFamily;
        synchronized (OtaDeviceFamily.class) {
            if (cachedFamily != null) return cachedFamily;
            int[] px = readDisplayPixels();
            String board = (Build.HARDWARE != null ? Build.HARDWARE : "")
                    + " " + (Build.BOARD != null ? Build.BOARD : "");
            String manu = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "")
                    + " " + (Build.BRAND != null ? Build.BRAND : "")
                    + " " + (Build.PRODUCT != null ? Build.PRODUCT : "");
            String pin = readSystemProperty(PROP_DEVICE_FAMILY);
            cachedFamily = probeFamily(readProcCpuHardware(), board, Build.VERSION.SDK_INT,
                    Build.MODEL != null ? Build.MODEL : "", manu, px[0], px[1], pin);
            return cachedFamily;
        }
    }

    static String probeFamily(String cpuHardware, String boardHardware, int sdkInt,
            String model, String manufacturer, int displayW, int displayH, String familyPin) {
        String cpu = cpuHardware != null ? cpuHardware.toLowerCase(Locale.US) : "";
        String board = boardHardware != null ? boardHardware.toLowerCase(Locale.US) : "";
        String m = model != null ? model.toLowerCase(Locale.US) : "";
        String manu = manufacturer != null ? manufacturer.toLowerCase(Locale.US) : "";
        String pin = familyPin != null ? familyPin.toLowerCase(Locale.US).trim() : "";
        if (looksLikeA5Display(displayW, displayH)) return "a5";
        if (m.contains("a5") || manu.contains("a5")) return "a5";
        if (cpu.contains("mt6582") || board.contains("mt6582")) return "y2";
        if (cpu.contains("mt6572") || board.contains("mt6572")) return "y1";
        if ("a5".equals(pin) || "y1".equals(pin) || "y2".equals(pin)) return pin;
        if (looksLikeY1Display(displayW, displayH)) {
            if (sdkInt >= 19) return "y2";
            if (sdkInt <= 17) return "y1";
            if (m.contains("y2")) return "y2";
            if (m.contains("y1")) return "y1";
            return "y1";
        }
        if (sdkInt >= 19) return "y2";
        if (sdkInt <= 16) return "y1";
        if (m.contains("y2")) return "y2";
        if (m.contains("y1")) return "y1";
        return "y1";
    }

    static boolean looksLikeA5Display(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return false;
        int a = Math.min(widthPx, heightPx);
        int b = Math.max(widthPx, heightPx);
        return a >= 220 && a <= 260 && b >= 300 && b <= 340;
    }

    static boolean looksLikeY1Display(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return false;
        int a = Math.min(widthPx, heightPx);
        int b = Math.max(widthPx, heightPx);
        return a >= 340 && a <= 380 && b >= 460 && b <= 500;
    }

    private static int[] readDisplayPixels() {
        int[] out = new int[] { 0, 0 };
        try {
            android.content.res.Resources res = android.content.res.Resources.getSystem();
            if (res == null) return out;
            DisplayMetrics dm = res.getDisplayMetrics();
            if (dm == null) return out;
            out[0] = dm.widthPixels;
            out[1] = dm.heightPixels;
        } catch (Throwable ignored) {}
        return out;
    }

    private static String readSystemProperty(String key) {
        if (key == null || key.length() == 0) return "";
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v != null ? String.valueOf(v).trim().toLowerCase(Locale.US) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readProcCpuHardware() {
        java.io.BufferedReader r = null;
        try {
            r = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.toLowerCase(Locale.US).startsWith("hardware")) {
                    int idx = line.indexOf(':');
                    if (idx >= 0) return line.substring(idx + 1).trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) try { r.close(); } catch (Exception ignored) {}
        }
        return "";
    }
}
