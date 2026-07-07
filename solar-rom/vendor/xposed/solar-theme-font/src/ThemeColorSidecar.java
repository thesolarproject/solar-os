package com.solar.launcher.xposed.themefont;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Reads Solar-published dialog colors from /.solar/theme-colors.json on primary storage.
 */
final class ThemeColorSidecar {

    static final String SIDECAR_FILE = "theme-colors.json";

    private static final String[] CANDIDATE_ROOTS = {
            "/storage/sdcard1",
            "/storage/sdcard0",
            "/mnt/sdcard",
            "/sdcard"
    };

    private static volatile int cachedPanel = 0;
    private static volatile int cachedText = 0;
    private static volatile long cachedMtime;
    private static volatile long lastCheckMs;
    private static final long CHECK_INTERVAL_MS = 5000L;

    private ThemeColorSidecar() {}

    /** Panel fill color from sidecar, or 0 when missing. */
    static int panelColor() {
        refreshIfNeeded();
        return cachedPanel;
    }

    /** Primary dialog text color from sidecar, or 0 when missing. */
    static int textColor() {
        refreshIfNeeded();
        return cachedText;
    }

    static boolean hasColors() {
        refreshIfNeeded();
        return cachedPanel != 0 || cachedText != 0;
    }

    private static void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_INTERVAL_MS && cachedMtime > 0) return;
        lastCheckMs = now;
        File sidecar = resolveSidecarFile();
        if (sidecar == null || !sidecar.isFile()) {
            cachedPanel = 0;
            cachedText = 0;
            cachedMtime = 0;
            return;
        }
        long mtime = sidecar.lastModified();
        if (mtime == cachedMtime && cachedMtime > 0) return;
        parseSidecar(sidecar);
        cachedMtime = mtime;
    }

    static File resolveSidecarFile() {
        for (String root : CANDIDATE_ROOTS) {
            File f = new File(new File(root, FontSidecar.SIDECAR_DIR), SIDECAR_FILE);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    private static void parseSidecar(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            cachedPanel = json.optInt("panelColor", 0);
            cachedText = json.optInt("textColor", 0);
        } catch (Throwable ignored) {
            cachedPanel = 0;
            cachedText = 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        }
    }

    static String colorHex(int color) {
        return String.format(Locale.US, "#%08X", color);
    }
}
