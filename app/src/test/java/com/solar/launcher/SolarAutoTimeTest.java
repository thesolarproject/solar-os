package com.solar.launcher;

import org.junit.Test;

public class SolarAutoTimeTest {

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
}
