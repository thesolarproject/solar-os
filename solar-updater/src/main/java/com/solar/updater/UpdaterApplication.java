package com.solar.updater;

import android.app.Application;

import com.solar.ota.net.OtaTlsHelper;

/** 2026-07-05 — Bootstrap TLS before OTA catalog fetch (same Conscrypt path as Solar). */
public final class UpdaterApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OtaTlsHelper.init(this);
    }
}
