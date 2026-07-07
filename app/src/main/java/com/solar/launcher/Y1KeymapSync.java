package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Seeds canonical Y1-Rockbox.kl or Y2-Rockbox.kl to Generic/Stock/Rockbox/mtk via sync-y1-keymap.sh.
 */
public final class Y1KeymapSync {
    private static final String TAG = "Y1KeymapSync";
    private static final String ASSET_SCRIPT = "y1/sync-y1-keymap.sh";
    private static final String SYSTEM_SCRIPT = "/system/etc/solar/sync-y1-keymap.sh";

    private Y1KeymapSync() {}

    /** Before Rockbox switch and on Solar boot — unified keymap for both launchers. */
    public static void ensureUnified(Context context) {
        if (context == null) return;
        // 2026-07-06 — Y2 uses Y2-Rockbox.kl; Y1 uses Y1-Rockbox.kl; model pick in sync script, not file presence.
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
