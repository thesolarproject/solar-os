package com.solar.ota;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SolarUpdateClientTest {
    @Test
    public void selfCheck() {
        SolarUpdateClient.selfCheck();
    }

    @Test
    public void fetchLiveCatalog() throws Exception {
        List<SolarUpdateClient.ReleaseInfo> raw =
                SolarUpdateClient.fetchUpdatesRaw(SolarUpdateClient.DEFAULT_UPDATES_URL);
        if (raw.isEmpty()) {
            throw new AssertionError("live OTA catalog empty");
        }
        List<SolarUpdateClient.ReleaseInfo> releases =
                SolarUpdateClient.filterForDevice(raw);
        List<SolarUpdateClient.ReleaseInfo> picker = SolarUpdateClient.releasesForPicker(
                raw, 4, "0.2.1", SolarUpdateClient.MAX_PICKER_RELEASES);
        if (picker.isEmpty() && !releases.isEmpty()) {
            throw new AssertionError("picker empty for stable install");
        }
    }

    @Test
    public void pickerCapsLargeCatalog() {
        String variant = SolarUpdateClient.deviceVariant();
        List<SolarUpdateClient.ReleaseInfo> many = new ArrayList<SolarUpdateClient.ReleaseInfo>();
        for (int i = 1; i <= SolarUpdateClient.MAX_PICKER_RELEASES + 10; i++) {
            String day = String.format(Locale.US, "%02d", (i % 28) + 1);
            String tag = String.format(Locale.US, "nightly-202406%s-%04d", day, 1000 + i);
            many.add(new SolarUpdateClient.ReleaseInfo(
                    tag, tag, 1000 + i,
                    "https://x/solar-" + variant + "-" + tag + ".apk", true));
        }
        List<SolarUpdateClient.ReleaseInfo> picker = SolarUpdateClient.releasesForPicker(
                many, 4, "0.2.1", SolarUpdateClient.MAX_PICKER_RELEASES);
        if (picker.size() != SolarUpdateClient.MAX_PICKER_RELEASES) {
            throw new AssertionError("expected cap " + SolarUpdateClient.MAX_PICKER_RELEASES
                    + " got " + picker.size());
        }
        if (!picker.get(0).tag.contains("202406")) {
            throw new AssertionError("newest timestamp nightly first");
        }
    }

    @Test
    public void romOnlyReleaseBlocksApkSkipAhead() {
        List<SolarUpdateClient.ReleaseInfo> catalog = new ArrayList<SolarUpdateClient.ReleaseInfo>();
        catalog.add(new SolarUpdateClient.ReleaseInfo(
                "20260101-1200", "20260101-1200", 100,
                "https://x/solar-20260101-1200.apk", false, true, "universal"));
        catalog.add(new SolarUpdateClient.ReleaseInfo(
                "20260102-1200", "20260102-1200", 200,
                "", false, false, "universal"));
        catalog.add(new SolarUpdateClient.ReleaseInfo(
                "20260103-1200", "20260103-1200", 300,
                "https://x/solar-20260103-1200.apk", false, true, "universal"));
        SolarUpdateClient.NextUpdate next = SolarUpdateClient.findNextUpdate(catalog, 100, "20260101-1200");
        if (next.kind != SolarUpdateClient.NextUpdateKind.ROM_FLASH_REQUIRED) {
            throw new AssertionError("expected ROM gap blocker, got " + next.kind);
        }
        SolarUpdateClient.ReleaseInfo later = catalog.get(2);
        if (!SolarUpdateClient.isApkBlockedByRomGap(catalog, later, 100, "20260101-1200")) {
            throw new AssertionError("later APK should be blocked by ROM-only gap");
        }
    }

    @Test
    public void parseRomOnlyUpdatesXml() {
        String xml = "<?xml version=\"1.0\"?><solar-updates base=\"https://example.com/ota/\">"
                + "<release tag=\"20260102-1200\" versionName=\"20260102-1200\" versionCode=\"200\" "
                + "nightly=\"false\" romOnly=\"true\" variant=\"universal\"/>"
                + "</solar-updates>";
        List<SolarUpdateClient.ReleaseInfo> parsed = SolarUpdateClient.parseUpdatesXml(xml);
        if (parsed.size() != 1) throw new AssertionError("rom-only xml count");
        if (parsed.get(0).hasApk) throw new AssertionError("rom-only row must not have apk");
    }
}
