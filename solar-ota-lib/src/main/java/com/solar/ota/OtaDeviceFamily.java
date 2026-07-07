package com.solar.ota;

import android.os.Build;

import java.util.Locale;

/** 2026-07-05 — Y1/Y2 probe for OTA APK variant filtering (y1 vs y2 filenames). */
public final class OtaDeviceFamily {
    private static volatile String cachedFamily;

    private OtaDeviceFamily() {}

    public static String deviceFamily() {
        if (cachedFamily != null) return cachedFamily;
        synchronized (OtaDeviceFamily.class) {
            if (cachedFamily != null) return cachedFamily;
            String board = (Build.HARDWARE != null ? Build.HARDWARE : "")
                    + " " + (Build.BOARD != null ? Build.BOARD : "");
            cachedFamily = probeFamily(readProcCpuHardware(), board, Build.VERSION.SDK_INT,
                    Build.MODEL != null ? Build.MODEL : "");
            return cachedFamily;
        }
    }

    private static String probeFamily(String cpuHardware, String boardHardware, int sdkInt, String model) {
        String cpu = cpuHardware != null ? cpuHardware.toLowerCase(Locale.US) : "";
        String board = boardHardware != null ? boardHardware.toLowerCase(Locale.US) : "";
        if (cpu.contains("mt6582") || board.contains("mt6582")) return "y2";
        if (cpu.contains("mt6572") || board.contains("mt6572")) return "y1";
        if (sdkInt >= 19) return "y2";
        if (sdkInt <= 16) return "y1";
        String m = model != null ? model.toLowerCase(Locale.US) : "";
        if (m.contains("y2")) return "y2";
        return "y1";
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
