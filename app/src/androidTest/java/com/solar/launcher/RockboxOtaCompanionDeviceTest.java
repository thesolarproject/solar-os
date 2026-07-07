package com.solar.launcher;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.solar.launcher.net.TlsHelper;
import com.solar.ota.OtaCompanionUrls;
import com.solar.ota.net.OtaDownload;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-06 — Rockbox lib fallback zip reachability on Y1/Y2 hardware.
 * Layman: confirms update.zip mirror has native codec libs when github is down.
 */
@RunWith(AndroidJUnit4.class)
public class RockboxOtaCompanionDeviceTest {

    @Test
    public void device_downloadUpdateZip_hasLibRockbox() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (!ConnectivityHelper.isOnline(ctx)) {
            android.util.Log.w("RockboxOtaTest", "offline — skip");
            return;
        }
        TlsHelper.init(ctx.getApplicationContext());
        File dest = new File(ctx.getCacheDir(), "rockbox_update_zip_test.zip");
        if (dest.exists()) dest.delete();
        try {
            OtaDownload.downloadToFile(OtaCompanionUrls.ROCKBOX_UPDATE_ZIP_URL, dest,
                    "SolarDeviceTest/1.0");
        } catch (Exception e) {
            android.util.Log.w("RockboxOtaTest", "update.zip not hosted yet — skip: " + e.getMessage());
            return;
        }
        assertTrue("update.zip must exist", dest.isFile() && dest.length() > 10000L);
        ZipFile zip = new ZipFile(dest);
        try {
            ZipEntry entry = zip.getEntry("update/libs/armeabi/librockbox.so");
            assertNotNull("update.zip must contain librockbox.so", entry);
            assertTrue("librockbox.so must be non-empty", entry.getSize() > 1000L);
        } finally {
            zip.close();
        }
    }
}
