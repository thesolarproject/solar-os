package com.solar.launcher.xposed.themefont;

import android.graphics.Typeface;

import java.io.File;
import java.util.Locale;

/**
 * Locates Solar-published system font on primary storage (/.solar/system-font.ttf).
 * Probes Y2 MicroSD first, then internal — mirrors DeviceFeatures primary/secondary layout.
 */
final class FontSidecar {

    static final String SIDECAR_DIR = ".solar";
    static final String SIDECAR_FILE = "system-font.ttf";

    private static final String[] CANDIDATE_ROOTS = {
            "/storage/sdcard1",
            "/storage/sdcard0",
            "/mnt/sdcard",
            "/sdcard"
    };

    private static volatile Typeface cachedTypeface;
    private static volatile long cachedMtime;
    private static volatile long lastCheckMs;
    private static final long CHECK_INTERVAL_MS = 5000L;

    /** ThreadLocal guard — our createFromFile must not recurse into hooks. */
    static final ThreadLocal<Boolean> LOADING = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private FontSidecar() {}

    /** Return cached custom base face, reloading when sidecar mtime changes. */
    static Typeface getCustomBase() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < CHECK_INTERVAL_MS && cachedTypeface != null) {
            return cachedTypeface;
        }
        lastCheckMs = now;
        File sidecar = resolveSidecarFile();
        if (sidecar == null || !sidecar.isFile() || sidecar.length() <= 0) {
            cachedTypeface = null;
            cachedMtime = 0;
            return null;
        }
        long mtime = sidecar.lastModified();
        if (cachedTypeface != null && mtime == cachedMtime) {
            return cachedTypeface;
        }
        Typeface loaded = loadFromFile(sidecar);
        cachedTypeface = loaded;
        cachedMtime = loaded != null ? mtime : 0;
        return loaded;
    }

    static File resolveSidecarFile() {
        for (String root : CANDIDATE_ROOTS) {
            File f = new File(new File(root, SIDECAR_DIR), SIDECAR_FILE);
            if (f.isFile() && f.length() > 0) {
                return f;
            }
        }
        return null;
    }

    static boolean isCompatibleSidecar(File file) {
        if (file == null) return false;
        String name = file.getName().toLowerCase(Locale.US);
        return name.endsWith(".ttf") || name.endsWith(".otf");
    }

    private static Typeface loadFromFile(File file) {
        if (!isCompatibleSidecar(file)) return null;
        LOADING.set(Boolean.TRUE);
        try {
            return Typeface.createFromFile(file);
        } catch (Throwable ignored) {
            return null;
        } finally {
            LOADING.set(Boolean.FALSE);
        }
    }
}
