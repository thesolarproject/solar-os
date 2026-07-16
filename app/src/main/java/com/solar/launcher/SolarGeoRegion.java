package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import com.solar.launcher.diag.SolarDiagFeatureLog;
import com.solar.launcher.net.SolarHttp;
import com.solar.launcher.podcast.PodcastCatalog;
import com.solar.launcher.radio.RadioSettings;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-07-16 — IP geolocation soft-defaults so Solar feels local: timezone (if unset),
 * system locale/region (once), podcast storefront, FM/internet radio country, YouTube region.
 *
 * <ul>
 *   <li>Never overrides an explicit user timezone or podcast storefront choice.</li>
 *   <li>Caches country/timezone for NTP pool + YouTube trending without re-hitting the API every call.</li>
 *   <li>Triggers: process start (with net) and Wi‑Fi online.</li>
 * </ul>
 */
public final class SolarGeoRegion {
    public static final String PREF_GEO_COUNTRY = "solar_geo_country";
    public static final String PREF_GEO_TIMEZONE = "solar_geo_timezone";
    public static final String PREF_GEO_LAST_MS = "solar_geo_last_ms";
    /** Soft locale applied once unless user later changes system locale outside Solar. */
    public static final String PREF_GEO_LOCALE_APPLIED = "solar_geo_locale_soft_applied";
    /** True after the user picks a podcast storefront in the picker. */
    public static final String PREF_PODCAST_USER_SET = "podcast_storefront_user_set";
    public static final String PREF_PODCAST_STOREFRONT = "podcast_storefront";
    public static final String PREF_YOUTUBE_REGION = "solar_youtube_region";

    private static final long MIN_REFRESH_MS = 6L * 60L * 60L * 1000L;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static volatile long lastAttemptMs;

    private SolarGeoRegion() {}

    /** Cached ISO-3166 alpha-2, or empty. */
    public static String countryCode(SharedPreferences prefs) {
        if (prefs == null) return "";
        String c = prefs.getString(PREF_GEO_COUNTRY, "");
        return c != null ? c.trim().toUpperCase(Locale.US) : "";
    }

    public static String countryCode(Context ctx) {
        return countryCode(settings(ctx));
    }

    /** Cached IANA timezone from last geo hit, or empty. */
    public static String geoTimezone(SharedPreferences prefs) {
        if (prefs == null) return "";
        String t = prefs.getString(PREF_GEO_TIMEZONE, "");
        return t != null ? t.trim() : "";
    }

    /**
     * YouTube trending/search region (2-letter). Prefers geo cache, then system locale country, else US.
     */
    public static String youtubeRegion(Context ctx) {
        SharedPreferences prefs = settings(ctx);
        if (prefs != null) {
            String y = prefs.getString(PREF_YOUTUBE_REGION, "");
            if (y != null && y.length() == 2) return y.toUpperCase(Locale.US);
            String g = countryCode(prefs);
            if (g.length() == 2) return g;
        }
        try {
            String lc = Locale.getDefault().getCountry();
            if (lc != null && lc.length() == 2) return lc.toUpperCase(Locale.US);
        } catch (Exception ignored) {}
        return "US";
    }

    public static void markPodcastStorefrontUserSet(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_PODCAST_USER_SET, true).apply();
    }

    public static boolean isPodcastStorefrontUserSet(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_PODCAST_USER_SET, false);
    }

    /**
     * Podcast iTunes/OpenRSS country: user pick wins; otherwise soft geo (or locale) storefront.
     * Layman: charts feel local until you choose another country in Podcasts.
     */
    public static String effectivePodcastStorefront(Context ctx) {
        return effectivePodcastStorefront(settings(ctx), ctx);
    }

    public static String effectivePodcastStorefront(SharedPreferences prefs) {
        return effectivePodcastStorefront(prefs, null);
    }

    public static String effectivePodcastStorefront(SharedPreferences prefs, Context ctx) {
        if (prefs != null && prefs.getBoolean(PREF_PODCAST_USER_SET, false)) {
            String user = prefs.getString(PREF_PODCAST_STOREFRONT, "");
            if (user != null && user.length() == 2) return user.toUpperCase(Locale.US);
        }
        if (prefs != null) {
            String soft = prefs.getString(PREF_PODCAST_STOREFRONT, "");
            if (soft != null && soft.length() == 2 && !prefs.getBoolean(PREF_PODCAST_USER_SET, false)) {
                // Soft geo write — trust it when present.
                // Still re-map from geo country if available so travel updates stick.
            }
            String geo = countryCode(prefs);
            if (geo.length() == 2) {
                String mapped = mapPodcastStorefront(geo);
                if (mapped != null) return mapped;
            }
            if (soft != null && soft.length() == 2) return soft.toUpperCase(Locale.US);
        }
        try {
            String lc = Locale.getDefault().getCountry();
            if (lc != null && lc.length() == 2) {
                String mapped = mapPodcastStorefront(lc);
                if (mapped != null) return mapped;
            }
        } catch (Exception ignored) {}
        if (ctx != null) {
            String yt = youtubeRegion(ctx);
            if (yt != null && yt.length() == 2) {
                String mapped = mapPodcastStorefront(yt);
                if (mapped != null) return mapped;
            }
        }
        return "US";
    }

    /**
     * Process start / online — refresh geo and soft-apply local experience prefs.
     * Offline-first: returns immediately when not already online (never wakes Wi‑Fi).
     */
    public static void onInternetAvailable(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        // Hybrid: geo is pure nicety — zero work when disconnected.
        if (!ConnectivityHelper.allowPassiveOnlineWork(app)) return;
        long now = System.currentTimeMillis();
        SharedPreferences prefs = settings(app);
        long last = prefs != null ? prefs.getLong(PREF_GEO_LAST_MS, 0L) : 0L;
        // Still run if never applied locale/podcast soft defaults even inside throttle window.
        boolean needSoft = prefs != null && (
                !prefs.getBoolean(PREF_GEO_LOCALE_APPLIED, false)
                        || (!prefs.getBoolean(PREF_PODCAST_USER_SET, false)
                        && !prefs.contains(PREF_PODCAST_STOREFRONT)));
        if (!needSoft && now - last < MIN_REFRESH_MS && now - lastAttemptMs < MIN_REFRESH_MS) {
            return;
        }
        if (!running.compareAndSet(false, true)) return;
        lastAttemptMs = now;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!ConnectivityHelper.allowPassiveOnlineWork(app)) return;
                    Snapshot snap = fetchGeoSnapshot();
                    if (snap == null || snap.countryCode == null || snap.countryCode.length() != 2) {
                        return;
                    }
                    applySnapshot(app, settings(app), snap);
                } catch (Exception ignored) {
                } finally {
                    running.set(false);
                }
            }
        }, "SolarGeoRegion").start();
    }

    /** Force refresh (settings / tests). */
    public static void requestRefreshNow(final Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        lastAttemptMs = 0L;
        if (!running.compareAndSet(false, true)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!ConnectivityHelper.isOnline(app)) return;
                    Snapshot snap = fetchGeoSnapshot();
                    if (snap != null) applySnapshot(app, settings(app), snap);
                } catch (Exception ignored) {
                } finally {
                    running.set(false);
                }
            }
        }, "SolarGeoRegionForce").start();
    }

    static void applySnapshot(Context app, SharedPreferences prefs, Snapshot snap) {
        if (prefs == null || snap == null) return;
        String cc = snap.countryCode != null
                ? snap.countryCode.trim().toUpperCase(Locale.US) : "";
        if (cc.length() != 2) return;
        String tz = snap.timezone != null ? snap.timezone.trim() : "";

        prefs.edit()
                .putString(PREF_GEO_COUNTRY, cc)
                .putString(PREF_GEO_TIMEZONE, tz)
                .putString(PREF_YOUTUBE_REGION, cc)
                .putLong(PREF_GEO_LAST_MS, System.currentTimeMillis())
                .apply();

        softApplyTimezone(prefs, tz);
        softApplyPodcastStorefront(prefs, cc);
        softApplyRadio(app, cc);
        softApplySystemLocale(app, prefs, cc);

        try {
            SolarDiagFeatureLog.event("geo", "region_ok country=" + cc
                    + " tz=" + tz
                    + " yt=" + cc
                    + " podcast=" + prefs.getString(PREF_PODCAST_STOREFRONT, "")
                    + " locale_applied=" + prefs.getBoolean(PREF_GEO_LOCALE_APPLIED, false));
        } catch (Throwable ignored) {}
    }

    /**
     * Soft timezone: only when user never saved {@link SolarAutoTime#PREF_TIMEZONE_ID}.
     * Writes pref + applies effective zone (respects Observe DST offline policy).
     */
    static void softApplyTimezone(SharedPreferences prefs, String tzId) {
        if (prefs == null || tzId == null || tzId.isEmpty()) return;
        String existing = prefs.getString(SolarAutoTime.PREF_TIMEZONE_ID, "");
        if (existing != null && !existing.trim().isEmpty()) return;
        // Prefer an id that appears in the wheel when possible; else store geo IANA as-is.
        String pick = pickCommonTimezone(tzId);
        prefs.edit().putString(SolarAutoTime.PREF_TIMEZONE_ID, pick).apply();
        SolarAutoTime.applyEffectiveTimezoneRoot(prefs);
    }

    /** Map arbitrary IANA id onto Solar's wheel list when a close match exists. */
    static String pickCommonTimezone(String geoTz) {
        if (geoTz == null || geoTz.isEmpty()) return "UTC";
        String[] ids = SolarAutoTime.commonTimezoneIds();
        for (String id : ids) {
            if (id.equalsIgnoreCase(geoTz)) return id;
        }
        // Same continent city often shares offset — keep exact geo id for correctness.
        return geoTz;
    }

    /**
     * Soft podcast storefront whenever the user has not picked one.
     * Always writes a 2-letter code (mapped or US) so charts are never stuck on an empty pref.
     */
    static void softApplyPodcastStorefront(SharedPreferences prefs, String countryCode) {
        if (prefs == null) return;
        if (prefs.getBoolean(PREF_PODCAST_USER_SET, false)) return;
        String mapped = mapPodcastStorefront(countryCode);
        if (mapped == null || mapped.length() != 2) mapped = "US";
        prefs.edit().putString(PREF_PODCAST_STOREFRONT, mapped).apply();
    }

    /**
     * Map ISO country → iTunes/podcast storefront code.
     * Catalog countries pass through; others get nearest storefront; last resort US.
     * Never returns null for a valid 2-letter ISO (always a usable storefront).
     */
    static String mapPodcastStorefront(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return "US";
        String cc = countryCode.toUpperCase(Locale.US);
        for (PodcastCatalog.Country c : PodcastCatalog.COUNTRIES) {
            if (c.code.equals(cc)) return cc;
        }
        // Closest storefronts for common locales outside the short list.
        if ("NZ".equals(cc)) return "AU";
        if ("IE".equals(cc)) return "GB";
        if ("AT".equals(cc) || "CH".equals(cc) || "LI".equals(cc)) return "DE";
        if ("BE".equals(cc) || "LU".equals(cc) || "MC".equals(cc)) return "FR";
        if ("PT".equals(cc)) return "BR";
        if ("AR".equals(cc) || "CL".equals(cc) || "CO".equals(cc) || "PE".equals(cc)
                || "UY".equals(cc) || "VE".equals(cc) || "EC".equals(cc)) {
            return "MX";
        }
        if ("DK".equals(cc) || "NO".equals(cc) || "FI".equals(cc) || "IS".equals(cc)) return "SE";
        if ("PL".equals(cc) || "CZ".equals(cc) || "SK".equals(cc) || "HU".equals(cc)
                || "RO".equals(cc) || "BG".equals(cc) || "GR".equals(cc) || "PT".equals(cc)
                || "HR".equals(cc) || "SI".equals(cc) || "RS".equals(cc) || "UA".equals(cc)
                || "BY".equals(cc) || "LT".equals(cc) || "LV".equals(cc) || "EE".equals(cc)) {
            return "DE"; // continental EU charts often closer via DE/FR; DE is in catalog
        }
        if ("TW".equals(cc) || "HK".equals(cc) || "SG".equals(cc) || "MY".equals(cc)
                || "TH".equals(cc) || "VN".equals(cc) || "PH".equals(cc) || "ID".equals(cc)) {
            return "JP"; // East/SE Asia nearest major storefront in catalog
        }
        if ("CN".equals(cc)) return "JP";
        if ("ZA".equals(cc) || "NG".equals(cc) || "KE".equals(cc) || "EG".equals(cc)) return "GB";
        if ("IL".equals(cc) || "SA".equals(cc) || "AE".equals(cc) || "TR".equals(cc)) return "GB";
        if ("RU".equals(cc) || "KZ".equals(cc)) return "DE";
        // iTunes accepts most ISO-2 country codes — use geo country even if not in UI picker list.
        if (cc.matches("[A-Z]{2}")) return cc;
        return "US";
    }

    static void softApplyRadio(Context app, String countryCode) {
        if (app == null || countryCode == null || countryCode.length() != 2) return;
        try {
            if (RadioSettings.getAutoDetectRegion(app)) {
                RadioSettings.setInternetRadioCountry(app, countryCode);
                RadioSettings.setFmBandRegion(app, RadioSettings.isoToFmRegion(countryCode));
            }
        } catch (Exception ignored) {}
    }

    /**
     * Soft system locale/region once — rooted Solar devices (Y1/Y2/A5).
     * Does not re-apply after first success so manual system changes stick.
     */
    static void softApplySystemLocale(Context app, SharedPreferences prefs, String countryCode) {
        if (prefs == null || countryCode == null || countryCode.length() != 2) return;
        if (prefs.getBoolean(PREF_GEO_LOCALE_APPLIED, false)) return;
        LocaleHint hint = localeHintForCountry(countryCode);
        if (hint == null) return;
        boolean ok = applySystemLocaleRoot(hint);
        if (ok) {
            prefs.edit().putBoolean(PREF_GEO_LOCALE_APPLIED, true).apply();
        }
    }

    /**
     * BCP-47 language + region for common ISO countries.
     * Layman: pick the language people in that country usually expect on their phone.
     */
    static LocaleHint localeHintForCountry(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return null;
        String cc = countryCode.toUpperCase(Locale.US);
        if ("US".equals(cc)) return new LocaleHint("en", "US", "en-US");
        if ("GB".equals(cc) || "IE".equals(cc)) return new LocaleHint("en", "GB", "en-GB");
        if ("AU".equals(cc) || "NZ".equals(cc)) return new LocaleHint("en", "AU", "en-AU");
        if ("CA".equals(cc)) return new LocaleHint("en", "CA", "en-CA");
        if ("IN".equals(cc)) return new LocaleHint("en", "IN", "en-IN");
        if ("DE".equals(cc) || "AT".equals(cc)) return new LocaleHint("de", "DE", "de-DE");
        if ("CH".equals(cc)) return new LocaleHint("de", "CH", "de-CH");
        if ("FR".equals(cc) || "BE".equals(cc)) return new LocaleHint("fr", "FR", "fr-FR");
        if ("ES".equals(cc)) return new LocaleHint("es", "ES", "es-ES");
        if ("MX".equals(cc)) return new LocaleHint("es", "MX", "es-MX");
        if ("AR".equals(cc) || "CL".equals(cc) || "CO".equals(cc)) {
            return new LocaleHint("es", cc, "es-" + cc);
        }
        if ("IT".equals(cc)) return new LocaleHint("it", "IT", "it-IT");
        if ("NL".equals(cc)) return new LocaleHint("nl", "NL", "nl-NL");
        if ("SE".equals(cc)) return new LocaleHint("sv", "SE", "sv-SE");
        if ("BR".equals(cc)) return new LocaleHint("pt", "BR", "pt-BR");
        if ("PT".equals(cc)) return new LocaleHint("pt", "PT", "pt-PT");
        if ("JP".equals(cc)) return new LocaleHint("ja", "JP", "ja-JP");
        if ("KR".equals(cc)) return new LocaleHint("ko", "KR", "ko-KR");
        if ("CN".equals(cc)) return new LocaleHint("zh", "CN", "zh-CN");
        if ("TW".equals(cc)) return new LocaleHint("zh", "TW", "zh-TW");
        if ("RU".equals(cc)) return new LocaleHint("ru", "RU", "ru-RU");
        if ("PL".equals(cc)) return new LocaleHint("pl", "PL", "pl-PL");
        if ("TR".equals(cc)) return new LocaleHint("tr", "TR", "tr-TR");
        if ("SA".equals(cc) || "AE".equals(cc) || "EG".equals(cc)) {
            return new LocaleHint("ar", cc, "ar-" + cc);
        }
        // Fallback: keep English language, set region only (still improves region-aware APIs).
        return new LocaleHint("en", cc, "en-" + cc);
    }

    static boolean applySystemLocaleRoot(LocaleHint hint) {
        if (hint == null) return false;
        // API 17–22 style language/country props + modern locale tag.
        String cmd = "setprop persist.sys.language " + shellQuote(hint.language)
                + "; setprop persist.sys.country " + shellQuote(hint.country)
                + "; setprop persist.sys.locale " + shellQuote(hint.tag)
                + "; setprop persist.sys.localevar '' 2>/dev/null"
                + "; settings put system system_locales " + shellQuote(hint.tag) + " 2>/dev/null"
                + "; true";
        boolean ok = RootShell.run(cmd, true);
        try {
            Locale.setDefault(new Locale(hint.language, hint.country));
        } catch (Exception ignored) {}
        return ok;
    }

    static Snapshot fetchGeoSnapshot() {
        try {
            String json = SolarHttp.getText(
                    "http://ip-api.com/json/?fields=status,country,countryCode,timezone");
            if (json == null || json.isEmpty()) return null;
            JSONObject o = new JSONObject(json);
            if (!"success".equalsIgnoreCase(o.optString("status", ""))) return null;
            Snapshot s = new Snapshot();
            s.countryCode = o.optString("countryCode", "");
            s.countryName = o.optString("country", "");
            s.timezone = o.optString("timezone", "");
            if (s.countryCode == null || s.countryCode.length() != 2) return null;
            s.countryCode = s.countryCode.toUpperCase(Locale.US);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static SharedPreferences settings(Context ctx) {
        if (ctx == null) return null;
        return ctx.getApplicationContext()
                .getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
    }

    private static String shellQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static final class Snapshot {
        String countryCode;
        String countryName;
        String timezone;
    }

    static final class LocaleHint {
        final String language;
        final String country;
        final String tag;

        LocaleHint(String language, String country, String tag) {
            this.language = language;
            this.country = country;
            this.tag = tag;
        }
    }
}
