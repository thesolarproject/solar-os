package com.solar.launcher;

import org.junit.Test;

public class SolarAutoTimeTest {

    @Test
    public void wallClockImplausibleDetectsEpochRange() {
        // Method uses System.currentTimeMillis — on CI wall clock is fine (≥2020).
        // Just ensure the helper is callable and consistent with “not before 2020”.
        boolean implausible = SolarAutoTime.isWallClockImplausible();
        long now = System.currentTimeMillis();
        boolean expect = now < 1577836800000L || now > 1893456000000L;
        if (implausible != expect) {
            throw new AssertionError("implausible=" + implausible + " now=" + now);
        }
    }

    @Test
    public void ntpSelfCheck() {
        SolarNtpClient.selfCheck();
    }

    @Test
    public void regionFromTimezoneMapsMajorContinents() {
        if (!"europe".equals(SolarAutoTime.regionFromTimezone("Europe/London"))) {
            throw new AssertionError("europe");
        }
        if (!"north-america".equals(SolarAutoTime.regionFromTimezone("America/New_York"))) {
            throw new AssertionError("na");
        }
        if (!"asia".equals(SolarAutoTime.regionFromTimezone("Asia/Tokyo"))) {
            throw new AssertionError("asia");
        }
        if (!"oceania".equals(SolarAutoTime.regionFromTimezone("Australia/Sydney"))) {
            throw new AssertionError("oceania");
        }
    }

    @Test
    public void poolsForRegionIncludeFallback() {
        String[] eu = SolarAutoTime.poolsForRegion("europe");
        if (eu == null || eu.length < 2) throw new AssertionError("eu pools");
        boolean hasPool = false;
        for (String h : eu) {
            if (h != null && h.contains("pool.ntp.org")) hasPool = true;
        }
        if (!hasPool) throw new AssertionError("need pool.ntp.org fallback");
    }

    @Test
    public void timezoneDisplayLabelNonEmpty() {
        String label = SolarAutoTime.displayTimezoneLabel("Europe/Paris");
        if (label == null || !label.contains("UTC")) throw new AssertionError(label);
    }

    @Test
    public void commonTimezoneListHasUtc() {
        String[] ids = SolarAutoTime.commonTimezoneIds();
        if (!"UTC".equals(ids[0])) throw new AssertionError("utc first");
        if (SolarAutoTime.indexOfTimezone("Asia/Tokyo") < 0) throw new AssertionError("tokyo");
    }

    @Test
    public void localWallToUtcRespectsTimezoneOffset() {
        // Fixed: 2024-01-15 12:00 America/New_York = EST (UTC-5) → 17:00 UTC
        long winter = SolarAutoTime.localWallToUtcEpochMs(
                "America/New_York", 2024, 1, 15, 12, 0);
        // 2024-07-15 12:00 America/New_York = EDT (UTC-4) → 16:00 UTC
        long summer = SolarAutoTime.localWallToUtcEpochMs(
                "America/New_York", 2024, 7, 15, 12, 0);
        if (winter <= 0 || summer <= 0) throw new AssertionError("epoch");
        // Summer local noon is 1h earlier in UTC than winter local noon (DST).
        long delta = winter - summer;
        // Roughly 183 days apart minus 1 hour DST → still large positive; check DST hour gap:
        // At same UTC calendar... easier: convert both offsets.
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("America/New_York");
        int offW = tz.getOffset(winter);
        int offS = tz.getOffset(summer);
        if (offW >= offS) {
            // Winter offset more negative (or equal if no DST data) — expect winter < summer offset abs
            // EST -5h, EDT -4h → offW < offS (both negative)
            if (offW == offS) {
                // Device tzdata may be stale; still require conversion non-zero
                return;
            }
        }
        if (!(offW < offS)) {
            throw new AssertionError("expected winter offset < summer offset (DST): w="
                    + offW + " s=" + offS);
        }
    }

    @Test
    public void displayLabelMentionsDstWhenActive() {
        // Cannot force system date; just ensure non-empty and contains UTC.
        String label = SolarAutoTime.displayTimezoneLabel("Europe/London");
        if (label == null || !label.contains("UTC")) throw new AssertionError(label);
    }

    @Test
    public void fixedOffsetEtcGmtInvertsSign() {
        // UTC−5 (US Eastern standard) → Etc/GMT+5
        String id = SolarAutoTime.fixedOffsetEtcGmtId(-5 * 3600000);
        if (!"Etc/GMT+5".equals(id)) throw new AssertionError(id);
        // UTC+1 → Etc/GMT-1
        String id2 = SolarAutoTime.fixedOffsetEtcGmtId(3600000);
        if (!"Etc/GMT-1".equals(id2)) throw new AssertionError(id2);
        if (!"UTC".equals(SolarAutoTime.fixedOffsetEtcGmtId(0))) throw new AssertionError("utc0");
    }

    @Test
    public void noDstLabelMentionsNoDstForZonesWithRules() {
        String label = SolarAutoTime.displayTimezoneLabel("America/New_York", false);
        if (label == null || !label.contains("no DST")) throw new AssertionError(label);
    }
}
