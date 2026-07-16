package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Seeds family keylayouts to /system via sync-y1-keymap.sh.
 * 2026-07-16 — A5 must install A5-mtk.kl + A5.kl (never Y1-Rockbox wheel maps).
 * Layman: on A5, face buttons stay left/right/OK and side rocks stay volume + back.
 * Tech: ensureUnified used to treat !y2 as Y1, so A5 boot wrote Y1-Rockbox (MEDIA_PLAY on
 * face, DPAD on volume scancodes) and broke hardware. Now isA5 seeds A5 maps + pins family.
 * Reversal: drop A5 branch; A5 would reapply Y1-Rockbox.kl on every Solar start.
 */
public final class Y1KeymapSync {
    private static final String TAG = "Y1KeymapSync";
    private static final String ASSET_SCRIPT = "y1/sync-y1-keymap.sh";
    private static final String SYSTEM_SCRIPT = "/system/etc/solar/sync-y1-keymap.sh";

    private Y1KeymapSync() {}

    /** Before Rockbox switch and on Solar boot — unified keymap for the active family. */
    public static void ensureUnified(Context context) {
        if (context == null) return;
        // 2026-07-16 — A5 first: never fall through to Y1-Rockbox when model prop lies (Y1).
        if (DeviceFeatures.isA5()) {
            ensureA5(context);
            return;
        }
        // 2026-07-06 — Y2 uses Y2-Rockbox.kl; Y1 uses Y1-Rockbox.kl; model pick in sync script.
        final boolean y2 = DeviceFeatures.isY2();
        final String assetKl = y2 ? "y1/Y2-Rockbox.kl" : "y1/Y1-Rockbox.kl";
        final String klName = y2 ? "Y2-Rockbox.kl" : "Y1-Rockbox.kl";
        final String systemKl = "/system/etc/solar/" + klName;
        File kl = extractAssetToCache(context, assetKl, klName);
        File script = extractAssetToCache(context, ASSET_SCRIPT, "sync-y1-keymap.sh");
        if (kl == null || script == null) {
            Log.w(TAG, "keymap assets missing");
            return;
        }
        StringBuilder cmd = new StringBuilder();
        cmd.append("mkdir -p /system/etc/solar /system/usr/keylayout")
                .append(" && mount -o remount,rw /system 2>/dev/null")
                .append(" && cp ").append(shellQuote(kl.getAbsolutePath()))
                .append(" ").append(shellQuote(systemKl))
                .append(" && cp ").append(shellQuote(script.getAbsolutePath()))
                .append(" ").append(shellQuote(SYSTEM_SCRIPT))
                .append(" && chmod 644 ").append(shellQuote(systemKl))
                .append(" && chmod 755 ").append(shellQuote(SYSTEM_SCRIPT));
        if (!y2) {
            File stock = extractAssetToCache(context, "y1/mtk-kpd.y1.stock.kl", "mtk-kpd.y1.stock.kl");
            if (stock != null) {
                cmd.append(" && cp ").append(shellQuote(stock.getAbsolutePath()))
                        .append(" /system/etc/solar/mtk-kpd.y1.stock.kl")
                        .append(" && chmod 644 /system/etc/solar/mtk-kpd.y1.stock.kl");
            }
            cmd.append(" && rm -f /system/etc/solar/Y2-Rockbox.kl 2>/dev/null");
        }
        cmd.append(" && sh ").append(shellQuote(SYSTEM_SCRIPT));
        runSu(cmd.toString());
        Log.i(TAG, "unified keylayout installed (" + klName + ")");
    }

    /**
     * 2026-07-16 — Install Solar A5 keylayouts from APK assets (same as push-a5-keymap.sh).
     * Layman: put the correct button map on the player and mark it as an A5.
     * Tech: A5-mtk → mtk-kpd; A5.kl → Generic/Stock/Rockbox; setprop family a5; run sync script.
     * Was: ensureUnified only seeded Y1-Rockbox — A5 face/volume scancodes remapped wrong forever.
     * Reversal: call ensureUnified without this branch (broken A5 keys return).
     */
    private static void ensureA5(Context context) {
        File mtk = extractAssetToCache(context, "y1/A5-mtk.kl", "A5-mtk.kl");
        File gen = extractAssetToCache(context, "y1/A5.kl", "A5.kl");
        File script = extractAssetToCache(context, ASSET_SCRIPT, "sync-y1-keymap.sh");
        if (mtk == null || gen == null || script == null) {
            Log.w(TAG, "A5 keymap assets missing");
            return;
        }
        StringBuilder cmd = new StringBuilder();
        cmd.append("mkdir -p /system/etc/solar /system/usr/keylayout")
                .append(" && mount -o remount,rw /system 2>/dev/null")
                .append(" && cp ").append(shellQuote(mtk.getAbsolutePath()))
                .append(" /system/etc/solar/A5-mtk.kl")
                .append(" && cp ").append(shellQuote(gen.getAbsolutePath()))
                .append(" /system/etc/solar/A5.kl")
                .append(" && cp ").append(shellQuote(script.getAbsolutePath()))
                .append(" ").append(shellQuote(SYSTEM_SCRIPT))
                .append(" && chmod 644 /system/etc/solar/A5-mtk.kl /system/etc/solar/A5.kl")
                .append(" && chmod 755 ").append(shellQuote(SYSTEM_SCRIPT))
                // Pin before script so family pick cannot fall through to Y1-Rockbox.
                .append(" && setprop persist.solar.device_family a5 2>/dev/null")
                .append(" && sh ").append(shellQuote(SYSTEM_SCRIPT));
        boolean ok = runSu(cmd.toString());
        Log.i(TAG, "A5 keylayout installed ok=" + ok);
    }

    private static File extractAssetToCache(Context context, String asset, String name) {
        File out = new File(context.getCacheDir(), name);
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            in = context.getAssets().open(asset);
            fos = new FileOutputStream(out);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (n > 0) fos.write(buf, 0, n);
            }
            fos.flush();
            return out.length() > 0 ? out : null;
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
        }
    }

    private static boolean runSu(String command) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
