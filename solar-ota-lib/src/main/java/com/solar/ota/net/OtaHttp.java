package com.solar.ota.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/** 2026-07-05 — Minimal HTTPS client for OTA catalog + APK download (shared by Solar + Updater). */
public final class OtaHttp {
    private OtaHttp() {}

    public static OkHttpClient longReadClient() {
        return OtaTlsHelper.client().newBuilder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
    }
}
