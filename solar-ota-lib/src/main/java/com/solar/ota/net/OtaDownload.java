package com.solar.ota.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Request;
import okhttp3.Response;

/** 2026-07-05 — Download OTA APK bytes to a local file (shared by Solar + Updater). */
public final class OtaDownload {
    private OtaDownload() {}

    /** Try URLs in order — first successful download wins (companion APK fallbacks). */
    public static void downloadFirstOk(String[] urls, File dest, String userAgent) throws IOException {
        if (dest == null) throw new IOException("null dest");
        IOException last = null;
        if (urls != null) {
            for (String url : urls) {
                if (url == null || url.trim().isEmpty()) continue;
                try {
                    downloadToFile(url, dest, userAgent);
                    return;
                } catch (IOException e) {
                    last = e;
                    if (dest.isFile()) dest.delete();
                }
            }
        }
        throw last != null ? last : new IOException("All URLs failed");
    }

    public static void downloadToFile(String url, File dest, String userAgent) throws IOException {
        if (url == null || url.trim().isEmpty()) throw new IOException("empty url");
        if (dest == null) throw new IOException("null dest");
        OtaTlsHelper.ensureSecurityProvider();
        Response resp = OtaHttp.longReadClient().newCall(new Request.Builder()
                .url(url.trim())
                .header("User-Agent", userAgent != null ? userAgent : "SolarOta/1.0")
                .header("Accept", "application/vnd.android.package-archive,*/*")
                .header("Accept-Encoding", "identity")
                .build()).execute();
        if (!resp.isSuccessful() || resp.body() == null) {
            int code = resp.code();
            resp.close();
            throw new IOException("HTTP " + code);
        }
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            is = resp.body().byteStream();
            fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
            if (fos != null) try { fos.close(); } catch (IOException ignored) {}
            if (resp.body() != null) resp.body().close();
        }
    }
}
