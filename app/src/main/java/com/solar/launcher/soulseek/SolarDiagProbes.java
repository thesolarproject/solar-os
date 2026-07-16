package com.solar.launcher.soulseek;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import com.solar.launcher.AppVersion;
import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.SolarAutoTime;
import com.solar.launcher.SolarGeoRegion;
import com.solar.launcher.WifiScanFilter;
import com.solar.launcher.diag.SolarDiagContextCollector;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-07-16 — Developer recon probes over Reach/Soulseek.
 *
 * <ul>
 *   <li><b>{@code solar_diag}</b> alone (bare token) → full Cloudflare/GitHub log ship
 *       (handled by {@link SolarDiagnosticReporter#shipOnRemoteDiagCommand}).</li>
 *   <li><b>{@code solar_diag_*}</b> suffixes → quick silent auto-replies with targeted
 *       recon data only — <em>no</em> GitHub issue.</li>
 * </ul>
 * Probe replies use {@link SolarDeveloperAccounts#formatAutoMessage} so conversation
 * UIs omit them. Tokens are stripped from any mixed human text before display/storage.
 */
public final class SolarDiagProbes {

    /**
     * Bare pull token: {@code solar_diag} not followed by {@code _suffix}.
     * Word-boundary aware so {@code solar_diag_wifi} is a probe, not a pull.
     */
    private static final Pattern BARE_PULL = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])solar_diag(?![A-Za-z0-9_])");

    /** One or more probe tokens: {@code solar_diag_wifi_ssid} etc. */
    private static final Pattern PROBE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])solar_diag_([a-z0-9_]+)(?![A-Za-z0-9_])");

    /** Known probe keys (lowercase, without the solar_diag_ prefix). */
    public static final String[] KNOWN_KEYS = new String[] {
            "help",
            "ping",
            "device",
            "build",
            "version",
            "time",
            "timezone",
            "locale",
            "geo",
            "wifi",
            "wifi_ssid",
            "wifi_bssid",
            "wifi_mac",
            "wifi_ip",
            "wifi_rssi",
            "wifi_scan",
            "ip",
            "ips",
            "network",
            "bt",
            "bt_mac",
            "bt_name",
            "bt_bonded",
            "storage",
            "battery",
            "reach",
            "soulseek",
            "podcast_storefront",
            "youtube_region",
            "media",
            "last_track",
            "queue",
            "env_light",
            "uptime",
    };

    private SolarDiagProbes() {}

    /** Parsed developer PM for diag routing. */
    public static final class Parsed {
        public final boolean barePull;
        public final List<String> probeKeys;
        /** Human text with all diag tokens removed (may be empty). */
        public final String strippedText;
        /** Original trimmed text. */
        public final String original;

        Parsed(boolean barePull, List<String> probeKeys, String strippedText, String original) {
            this.barePull = barePull;
            this.probeKeys = probeKeys != null ? probeKeys : Collections.<String>emptyList();
            this.strippedText = strippedText != null ? strippedText : "";
            this.original = original != null ? original : "";
        }

        public boolean hasProbes() {
            return !probeKeys.isEmpty();
        }

        /** True when the message has no user-visible remainder after strip. */
        public boolean isCommandOnly() {
            return strippedText.trim().isEmpty() && (barePull || hasProbes());
        }
    }

    public static Parsed parse(String text) {
        if (text == null) return new Parsed(false, null, "", "");
        String original = text.trim();
        if (original.isEmpty()) return new Parsed(false, null, "", "");

        boolean bare = BARE_PULL.matcher(original).find();
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        Matcher pm = PROBE.matcher(original);
        while (pm.find()) {
            String k = pm.group(1);
            if (k != null && !k.isEmpty()) {
                keys.add(k.toLowerCase(Locale.US));
            }
        }
        String stripped = stripDiagTokens(original);
        return new Parsed(bare, new ArrayList<String>(keys), stripped, original);
    }

    /** Remove bare pull + probe tokens; collapse leftover whitespace. */
    public static String stripDiagTokens(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = PROBE.matcher(text).replaceAll(" ");
        s = BARE_PULL.matcher(s).replaceAll(" ");
        // Collapse whitespace / punctuation leftovers like "  ,  " mid-sentence.
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" *\\n *", "\n");
        return s.trim();
    }

    public static boolean hasBarePull(String text) {
        return text != null && BARE_PULL.matcher(text).find();
    }

    public static boolean hasProbe(String text) {
        return text != null && PROBE.matcher(text).find();
    }

    /**
     * Build a silent recon reply body (without auto prefix) for all probe keys in text.
     * Returns null when there are no probes.
     */
    public static String buildProbeReply(Context ctx, SharedPreferences prefs, String text) {
        Parsed p = parse(text);
        if (!p.hasProbes()) return null;
        return buildProbeReplyForKeys(ctx, prefs, p.probeKeys);
    }

    public static String buildProbeReplyForKeys(Context ctx, SharedPreferences prefs,
            List<String> keys) {
        if (keys == null || keys.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(512);
        sb.append("probe ");
        try {
            SoulseekAccount acct = SoulseekAccount.load(prefs, ctx);
            sb.append(acct != null && acct.username != null ? acct.username : "?");
        } catch (Exception e) {
            sb.append("?");
        }
        sb.append('@');
        try {
            sb.append(DeviceFeatures.deviceModelLabel());
        } catch (Exception e) {
            sb.append("?");
        }
        sb.append('\n');
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            String k = key.toLowerCase(Locale.US);
            sb.append(k).append(": ");
            try {
                sb.append(runProbe(ctx, prefs, k));
            } catch (Exception e) {
                sb.append("error=").append(e.getMessage() != null ? e.getMessage() : "fail");
            }
            sb.append('\n');
        }
        String out = sb.toString().trim();
        // Keep wire PMs bounded for Soulseek.
        if (out.length() > 3500) out = out.substring(0, 3490) + "\n…[truncated]";
        return out;
    }

    static String runProbe(Context ctx, SharedPreferences prefs, String key) {
        if ("help".equals(key) || "list".equals(key)) {
            StringBuilder h = new StringBuilder("keys=");
            for (int i = 0; i < KNOWN_KEYS.length; i++) {
                if (i > 0) h.append(',');
                h.append(KNOWN_KEYS[i]);
            }
            return h.toString();
        }
        if ("ping".equals(key)) return "pong t=" + System.currentTimeMillis();
        if ("device".equals(key) || "build".equals(key)) {
            return "family=" + DeviceFeatures.deviceFamily()
                    + " model=" + DeviceFeatures.deviceModelLabel()
                    + " buildModel=" + Build.MODEL
                    + " sdk=" + Build.VERSION.SDK_INT
                    + " release=" + Build.VERSION.RELEASE
                    + " fp=" + shortFp(Build.FINGERPRINT);
        }
        if ("version".equals(key)) {
            return "name=" + AppVersion.installedVersionName(ctx)
                    + " code=" + AppVersion.installedVersionCode(ctx);
        }
        if ("time".equals(key)) {
            return "wall=" + new java.util.Date().toString()
                    + " ms=" + System.currentTimeMillis();
        }
        if ("timezone".equals(key)) {
            SharedPreferences p = prefsOr(ctx, prefs);
            String id = SolarAutoTime.timezoneId(p);
            return "id=" + id + " label=" + SolarAutoTime.displayTimezoneLabel(id)
                    + " sys=" + java.util.TimeZone.getDefault().getID();
        }
        if ("locale".equals(key)) {
            java.util.Locale l = java.util.Locale.getDefault();
            return "locale=" + l
                    + " lang=" + l.getLanguage()
                    + " country=" + l.getCountry();
        }
        if ("geo".equals(key)) {
            SharedPreferences p = prefsOr(ctx, prefs);
            return "country=" + SolarGeoRegion.countryCode(p)
                    + " tz=" + SolarGeoRegion.geoTimezone(p)
                    + " yt=" + SolarGeoRegion.youtubeRegion(ctx);
        }
        if ("wifi".equals(key) || "wifi_ssid".equals(key) || "wifi_bssid".equals(key)
                || "wifi_mac".equals(key) || "wifi_ip".equals(key) || "wifi_rssi".equals(key)
                || "wifi_scan".equals(key)) {
            return wifiProbe(ctx, key);
        }
        if ("ip".equals(key) || "ips".equals(key) || "network".equals(key)) {
            return ipProbe(ctx, key);
        }
        if ("bt".equals(key) || "bt_mac".equals(key) || "bt_name".equals(key)
                || "bt_bonded".equals(key)) {
            return btProbe(key);
        }
        if ("storage".equals(key)) return storageProbe();
        if ("battery".equals(key)) return batteryProbe(ctx);
        if ("reach".equals(key) || "soulseek".equals(key)) {
            SharedPreferences p = prefsOr(ctx, prefs);
            SoulseekAccount a = SoulseekAccount.load(p, ctx);
            return "user=" + (a != null ? a.username : "")
                    + " custom=" + (a != null && a.custom);
        }
        if ("podcast_storefront".equals(key)) {
            SharedPreferences p = prefsOr(ctx, prefs);
            String sf = p != null ? p.getString("podcast_storefront", "US") : "US";
            return "storefront=" + sf;
        }
        if ("youtube_region".equals(key)) {
            return "region=" + SolarGeoRegion.youtubeRegion(ctx);
        }
        if ("media".equals(key) || "last_track".equals(key) || "queue".equals(key)) {
            return mediaProbe(ctx, prefs, key);
        }
        if ("env_light".equals(key)) {
            String env = SolarDiagContextCollector.collectEnvironmentLight(ctx);
            if (env == null) return "(empty)";
            if (env.length() > 1200) return env.substring(0, 1200) + "…";
            return env.replace("\n", " | ");
        }
        if ("uptime".equals(key)) {
            return "elapsedRealtime=" + android.os.SystemClock.elapsedRealtime()
                    + " uptimeMillis=" + android.os.SystemClock.uptimeMillis();
        }
        return "unknown_probe (try solar_diag_help)";
    }

    private static String wifiProbe(Context ctx, String key) {
        if (ctx == null) return "no_context";
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "no_wifi_manager";
            if ("wifi_scan".equals(key)) {
                try {
                    List<android.net.wifi.ScanResult> scan = wm.getScanResults();
                    if (scan == null || scan.isEmpty()) return "scan_empty";
                    StringBuilder s = new StringBuilder();
                    int n = 0;
                    for (android.net.wifi.ScanResult r : scan) {
                        if (r == null || n >= 12) break;
                        if (n > 0) s.append("; ");
                        s.append(r.SSID).append('@').append(r.level).append("dBm");
                        n++;
                    }
                    return s.toString();
                } catch (Exception e) {
                    return "scan_err=" + e.getMessage();
                }
            }
            StringBuilder s = new StringBuilder();
            s.append("enabled=").append(wm.isWifiEnabled());
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return s.append(" info=null").toString();
            String ssid = WifiScanFilter.displayableConnectedSsid(info.getSSID());
            if ("wifi_ssid".equals(key)) return ssid != null ? ssid : String.valueOf(info.getSSID());
            if ("wifi_bssid".equals(key)) return String.valueOf(info.getBSSID());
            if ("wifi_mac".equals(key)) return String.valueOf(info.getMacAddress());
            if ("wifi_ip".equals(key)) return intToIp(info.getIpAddress());
            if ("wifi_rssi".equals(key)) return String.valueOf(info.getRssi());
            // full wifi summary
            s.append(" ssid=").append(ssid != null ? ssid : info.getSSID());
            s.append(" bssid=").append(info.getBSSID());
            s.append(" mac=").append(info.getMacAddress());
            s.append(" ip=").append(intToIp(info.getIpAddress()));
            s.append(" rssi=").append(info.getRssi());
            s.append(" link=").append(info.getLinkSpeed()).append("Mbps");
            return s.toString();
        } catch (Exception e) {
            return "err=" + e.getMessage();
        }
    }

    private static String ipProbe(Context ctx, String key) {
        StringBuilder s = new StringBuilder();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return "none";
            int n = 0;
            for (NetworkInterface nif : Collections.list(ifaces)) {
                if (nif == null || !nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a == null || a.isLoopbackAddress()) continue;
                    if ("ip".equals(key) && !(a instanceof Inet4Address)) continue;
                    if (n > 0) s.append("; ");
                    s.append(nif.getName()).append('=').append(a.getHostAddress());
                    n++;
                    if (n >= 8) break;
                }
                if (n >= 8) break;
            }
            if (n == 0) return "none";
            return s.toString();
        } catch (Exception e) {
            return "err=" + e.getMessage();
        }
    }

    private static String btProbe(String key) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return "no_adapter";
            if ("bt_mac".equals(key)) {
                try {
                    return String.valueOf(adapter.getAddress());
                } catch (Throwable t) {
                    return "[unavailable]";
                }
            }
            if ("bt_name".equals(key)) {
                try {
                    return String.valueOf(adapter.getName());
                } catch (SecurityException se) {
                    return "[permission]";
                }
            }
            if ("bt_bonded".equals(key)) {
                try {
                    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    if (bonded == null || bonded.isEmpty()) return "none";
                    StringBuilder s = new StringBuilder();
                    int n = 0;
                    for (BluetoothDevice d : bonded) {
                        if (d == null || n >= 10) break;
                        if (n > 0) s.append("; ");
                        try {
                            s.append(d.getName()).append('/').append(d.getAddress());
                        } catch (SecurityException se) {
                            s.append("[permission]");
                        }
                        n++;
                    }
                    return s.toString();
                } catch (SecurityException se) {
                    return "[permission]";
                }
            }
            StringBuilder s = new StringBuilder();
            s.append("enabled=").append(adapter.isEnabled());
            try {
                s.append(" name=").append(adapter.getName());
            } catch (Exception ignored) {}
            try {
                s.append(" addr=").append(adapter.getAddress());
            } catch (Throwable ignored) {}
            return s.toString();
        } catch (Exception e) {
            return "err=" + e.getMessage();
        }
    }

    private static String storageProbe() {
        try {
            StatFs data = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long block = data.getBlockSizeLong();
            long free = data.getAvailableBlocksLong() * block;
            long total = data.getBlockCountLong() * block;
            return "data_free_mb=" + (free / (1024 * 1024))
                    + " data_total_mb=" + (total / (1024 * 1024));
        } catch (Throwable t) {
            try {
                StatFs data = new StatFs(Environment.getDataDirectory().getAbsolutePath());
                long block = data.getBlockSize();
                long free = (long) data.getAvailableBlocks() * block;
                return "data_free_mb=" + (free / (1024 * 1024));
            } catch (Throwable t2) {
                return "err";
            }
        }
    }

    private static String batteryProbe(Context ctx) {
        if (ctx == null) return "no_context";
        try {
            android.content.IntentFilter f =
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent bat = ctx.registerReceiver(null, f);
            if (bat == null) return "unknown";
            int level = bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            int status = bat.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            int pct = scale > 0 ? (level * 100) / scale : level;
            return "pct=" + pct + " status=" + status;
        } catch (Exception e) {
            return "err=" + e.getMessage();
        }
    }

    /**
     * Media recon — best-effort from prefs / impact-ping style caches without heavy I/O.
     */
    private static String mediaProbe(Context ctx, SharedPreferences prefs, String key) {
        SharedPreferences p = prefsOr(ctx, prefs);
        if (p == null) return "no_prefs";
        // Common keys used across Solar playback / impact pings (fail-open empty).
        String title = firstNonEmpty(p,
                "last_played_title", "media_last_title", "now_playing_title");
        String artist = firstNonEmpty(p,
                "last_played_artist", "media_last_artist", "now_playing_artist");
        String service = firstNonEmpty(p,
                "last_played_service", "media_last_service");
        String id = firstNonEmpty(p,
                "last_played_id", "media_last_id");
        if ("last_track".equals(key) || "media".equals(key)) {
            if (title.isEmpty() && artist.isEmpty()) return "none";
            return "service=" + service + " title=" + title + " artist=" + artist + " id=" + id;
        }
        if ("queue".equals(key)) {
            String q = firstNonEmpty(p, "media_queue_summary", "playback_queue_preview");
            return q.isEmpty() ? "none" : q;
        }
        return "none";
    }

    private static String firstNonEmpty(SharedPreferences p, String... keys) {
        if (p == null || keys == null) return "";
        for (String k : keys) {
            try {
                String v = p.getString(k, "");
                if (v != null && !v.trim().isEmpty()) return v.trim();
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static SharedPreferences prefsOr(Context ctx, SharedPreferences prefs) {
        if (prefs != null) return prefs;
        if (ctx == null) return null;
        return ctx.getApplicationContext()
                .getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
    }

    private static String intToIp(int ip) {
        return (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "."
                + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
    }

    private static String shortFp(String fp) {
        if (fp == null || fp.isEmpty()) return "";
        if (fp.length() <= 24) return fp;
        return fp.substring(0, 12) + "…" + fp.substring(fp.length() - 8);
    }
}
