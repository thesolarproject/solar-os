package com.solar.launcher;

import org.junit.Test;

public class SolarGeoRegionTest {

    @Test
    public void podcastStorefrontMapsCatalogCountries() {
        if (!"GB".equals(SolarGeoRegion.mapPodcastStorefront("gb"))) {
            throw new AssertionError("gb");
        }
        if (!"JP".equals(SolarGeoRegion.mapPodcastStorefront("JP"))) {
            throw new AssertionError("jp");
        }
        if (!"AU".equals(SolarGeoRegion.mapPodcastStorefront("NZ"))) {
            throw new AssertionError("nz→au");
        }
        if (!"DE".equals(SolarGeoRegion.mapPodcastStorefront("AT"))) {
            throw new AssertionError("at→de");
        }
    }

    @Test
    public void podcastStorefrontAlwaysReturnsTwoLetterForIso() {
        // Unknown ISO still returns a usable storefront (never null).
        String pl = SolarGeoRegion.mapPodcastStorefront("PL");
        if (pl == null || pl.length() != 2) throw new AssertionError("pl=" + pl);
        String zz = SolarGeoRegion.mapPodcastStorefront("ZZ");
        if (!"ZZ".equals(zz) && !"US".equals(zz)) {
            // ZZ is valid ISO shape — pass-through preferred
            if (zz == null || zz.length() != 2) throw new AssertionError(zz);
        }
        if (!"US".equals(SolarGeoRegion.mapPodcastStorefront(null))) {
            throw new AssertionError("null→US");
        }
        if (!"US".equals(SolarGeoRegion.mapPodcastStorefront(""))) {
            throw new AssertionError("empty→US");
        }
    }

    @Test
    public void nordicAndLatamRemaps() {
        if (!"SE".equals(SolarGeoRegion.mapPodcastStorefront("NO"))) {
            throw new AssertionError("no→se");
        }
        if (!"MX".equals(SolarGeoRegion.mapPodcastStorefront("AR"))) {
            throw new AssertionError("ar→mx");
        }
    }

    @Test
    public void localeHintMajorMarkets() {
        SolarGeoRegion.LocaleHint de = SolarGeoRegion.localeHintForCountry("DE");
        if (de == null || !"de".equals(de.language) || !"DE".equals(de.country)) {
            throw new AssertionError("de");
        }
        SolarGeoRegion.LocaleHint jp = SolarGeoRegion.localeHintForCountry("JP");
        if (jp == null || !"ja-JP".equals(jp.tag)) throw new AssertionError("jp tag");
        SolarGeoRegion.LocaleHint us = SolarGeoRegion.localeHintForCountry("US");
        if (us == null || !"en-US".equals(us.tag)) throw new AssertionError("us");
    }

    @Test
    public void pickCommonTimezoneExactMatch() {
        if (!"Europe/London".equals(SolarGeoRegion.pickCommonTimezone("Europe/London"))) {
            throw new AssertionError("london");
        }
        // Unknown IANA kept as-is
        if (!"Europe/Madrid".equals(SolarGeoRegion.pickCommonTimezone("Europe/Madrid"))) {
            throw new AssertionError("madrid keep");
        }
    }
}
