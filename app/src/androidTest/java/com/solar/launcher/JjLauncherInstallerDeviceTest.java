package com.solar.launcher;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.solar.launcher.net.SolarHttp;
import com.solar.launcher.net.TlsHelper;
import com.solar.ota.OtaCompanionInstaller;
import com.solar.ota.OtaCompanionUrls;
import com.solar.ota.net.OtaDownload;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertTrue;

/**
 * 2026-07-06 — JJ + Rockbox OTA download on real Y1/Y2 hardware (TLS + URL reachability).
 * Layman: proves Wi‑Fi can fetch companion APKs the same way Settings OTA downloads do.
 */
@RunWith(AndroidJUnit4.class)
public class JjLauncherInstallerDeviceTest {

    @Test
    public void device_downloadJjApk_overHttps() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!ConnectivityHelper.isOnline(ctx)) {
            android.util.Log.w("JjInstallerTest", "offline — skip");
            return;
        }
        TlsHelper.init(ctx.getApplicationContext());
        File dest = new File(ctx.getCacheDir(), "jj_device_test.apk");
        if (dest.exists()) dest.delete();
        SolarHttp.downloadToFile(JjLauncherAvailability.JJ_APK_URL, dest);
        assertTrue("jj_latest.apk must be >1MB", dest.isFile() && dest.length() > 1_000_000L);
        android.util.Log.i("JjInstallerTest", "downloaded bytes=" + dest.length());
    }

    @Test
    public void device_downloadRockboxInstallLadder_overHttps() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!ConnectivityHelper.isOnline(ctx)) {
            android.util.Log.w("JjInstallerTest", "offline — skip rockbox");
            return;
        }
        TlsHelper.init(ctx.getApplicationContext());
        File dest = new File(ctx.getCacheDir(), "rockbox_device_test.apk");
        if (dest.exists()) dest.delete();
        OtaDownload.downloadFirstOk(OtaCompanionUrls.ROCKBOX_INSTALL_URLS, dest, "SolarDeviceTest/1.0");
        assertTrue("rockbox install APK must be >1MB", dest.isFile() && dest.length() > 1_000_000L);
        android.util.Log.i("JjInstallerTest", "rockbox bytes=" + dest.length());
    }

    @Test
    public void device_installJjIfNeeded_whenDownloaded() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!ConnectivityHelper.isOnline(ctx) && !LauncherSwitch.isJjInstalled(ctx)) {
            android.util.Log.w("JjInstallerTest", "offline and JJ missing — skip install");
            return;
        }
        File workDir = ctx.getDir("update", Context.MODE_PRIVATE);
        boolean ok = OtaCompanionInstaller.installJjIfNeeded(ctx, workDir);
        assertTrue("JJ must be installed after installJjIfNeeded", ok);
        assertTrue("PM must see com.themoon.y1", LauncherSwitch.isJjInstalled(ctx));
    }
}
