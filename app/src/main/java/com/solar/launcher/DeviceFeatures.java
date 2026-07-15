package com.solar.launcher;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2026-07-05 — Device family hub: Y1 (MT6572), Y2 (MT6582), A5 (Timmkoo touch 240×320).
 * 2026-07-11 — A5 is exclusive third family (not Y1-by-default); touch + portrait themes.
 * Intentional divergences: Y1 no power button (BACK-long overlay); Y2 power/BACK-long;
 *   Y1 AVRCP native BT patches; Y2 dual storage; Y2 hides volume/lock chips;
 *   A5 touchscreen, dual face/side nav modes, portrait default, overlay volume/lock.
 * Reversal: drop isA5 branches; restore isY1 = !isY2.
 */
public final class DeviceFeatures {
    private static final String TAG = "DeviceFeatures";
    /** Y2 only — when true, new media saves may use internal storage (sdcard0) as well as MicroSD. */
    public static final String PREF_Y2_USE_INTERNAL_MEDIA = "y2_use_internal_media";
    /** Y2 only — when true, long OK sleeps the screen (legacy); default false opens quick menu instead. */
    public static final String PREF_Y2_HOLD_OK_TO_SLEEP = "y2_hold_ok_to_sleep";
    /** Y2 primary save target: {@link #PRIMARY_MEDIA_MICROSD} or {@link #PRIMARY_MEDIA_INTERNAL}. */
    public static final String PREF_Y2_PRIMARY_MEDIA = "y2_primary_media";
    public static final String PRIMARY_MEDIA_MICROSD = "microsd";
    public static final String PRIMARY_MEDIA_INTERNAL = "internal";
    /** Emulator / lab override — persist.solar.device_family = y1|y2|a5. */
    public static final String PROP_DEVICE_FAMILY = "persist.solar.device_family";
    private static final String PREFS = "SOLAR_SETTINGS";
    private static volatile String cachedFamily;
    private static Boolean testMicroSdPresentOverride;

    private DeviceFeatures() {}

    public static String deviceFamily() {
        return detectFamily();
    }

    public static String deviceModel() {
        if (isA5()) return "A5";
        return isY2() ? "Y2" : "Y1";
    }

    public static String deviceModelLabel() {
        if (isA5()) return "A5";
        return isY2() ? "Y2" : "Y1";
    }

    /**
     * Short product name for USB eject copy ("Please eject your Y1…").
     * Alias of {@link #deviceModelLabel()} — kept for call sites / companion parity.
     */
    public static String productModelLabel() {
        return deviceModelLabel();
    }

    public static int soulseekClientMinor() {
        // Reach-specific minor under experimental major 177 (not Nicotine+ 160.x).
        if (isA5()) return 103;
        return isY2() ? 102 : 101;
    }

    public static String reachClientName() {
        // 2026-07-11 — A5 is Timmkoo, not Innioasis; keep Reach brand clear.
        if (isA5()) return "Reach for Timmkoo A5";
        return "Reach for Innioasis " + deviceModelLabel();
    }

    /** Soulseek profile description peers see — reflects Sharing / Messaging toggles. */
    public static String reachUserBio(android.content.Context ctx, boolean sharingEnabled,
            boolean messagingEnabled) {
        if (ctx == null) return reachClientName();
        String intro = ctx.getString(com.solar.launcher.R.string.reach_user_bio_intro, deviceModelLabel());
        String sharingState = ctx.getString(sharingEnabled
                ? com.solar.launcher.R.string.common_on : com.solar.launcher.R.string.common_off);
        String messagingState = ctx.getString(messagingEnabled
                ? com.solar.launcher.R.string.common_on : com.solar.launcher.R.string.common_off);
        String status = ctx.getString(com.solar.launcher.R.string.reach_user_bio_status,
                sharingState, messagingState);
        String shareNote = ctx.getString(sharingEnabled
                ? com.solar.launcher.R.string.reach_user_bio_note_share_on
                : com.solar.launcher.R.string.reach_user_bio_note_share_off);
        String msgNote = ctx.getString(messagingEnabled
                ? com.solar.launcher.R.string.reach_user_bio_note_msg_on
                : com.solar.launcher.R.string.reach_user_bio_note_msg_off);
        return intro + "\n\n" + status + "\n\n" + shareNote + " " + msgNote;
    }

    public static boolean isY2() {
        return "y2".equals(detectFamily());
    }

    /** Exclusive Y1 — not “everything that is not Y2” (A5 is its own family). */
    public static boolean isY1() {
        return "y1".equals(detectFamily());
    }

    /** 2026-07-11 — Timmkoo A5 touch MP3 (240×320 portrait). */
    public static boolean isA5() {
        return "a5".equals(detectFamily());
    }

    /** Touch UI path — A5 only; Y1/Y2 stay wheel/key. */
    public static boolean hasTouchscreen() {
        return isA5();
    }

    /** Overlay volume + lock chips — Y1/A5, or any emulator family pin. */
    public static boolean showsOverlayVolumeLockChips() {
        if (isY1() || isA5()) return true;
        // 2026-07-11 — Emulator y1/y2/a5 pins still show Solar volume/lock chips.
        return EmulatorInputMap.isEmulator();
    }

    /** AVD / sdk build — for volume HUD and input map. */
    public static boolean isEmulator() {
        return EmulatorInputMap.isEmulator();
    }

    public static boolean hasRootAccess() {
        // 2026-07-15 — A5 Solar ROM bakes the same setuid su as Y1 (verify-a5-rom-contents).
        // Was: Y1/Y2 only — UMS + prep helpers treated A5 as unrooted even on Solar ROM.
        // Reversal: return isY1() || isY2() if stock A5 without su returns as a product SKU.
        return isY1() || isY2() || isA5();
    }

    /** True when su actually works from this app — ROM-only APK installs must fail-open without root. */
    public static boolean canRunRootShell() {
        return RootShell.canRun();
    }

    /**
     * Expected primary volume path.
     * 2026-07-11 — A5: TF/MicroSD when present else emulated sdcard0 (like Y2 primary).
     */
    public static String primaryStoragePath() {
        if (isY2() || isA5()) {
            // A5 often uses sdcard0 as TF; prefer sdcard1 when both exist (Y2-like).
            if (isY2()) return "/storage/sdcard1";
            File sd1 = new File("/storage/sdcard1");
            if (sd1.isDirectory()) return "/storage/sdcard1";
            return "/storage/sdcard0";
        }
        return "/storage/sdcard0";
    }

    /**
     * Secondary / internal volume — Y2 sdcard0; A5 internal when dual mounts exist.
     * 2026-07-11 — Emulator may only have one volume (secondary null).
     */
    public static String secondaryStoragePath() {
        if (isY2()) return "/storage/sdcard0";
        if (isA5()) {
            String primary = primaryStoragePath();
            if ("/storage/sdcard1".equals(primary)) return "/storage/sdcard0";
            // Single-volume A5/emulator — also try /mnt/sdcard as alias only when distinct.
            File mnt = new File("/mnt/sdcard");
            File dataMedia = new File("/data/media/0");
            if (dataMedia.isDirectory() && !primary.equals(dataMedia.getAbsolutePath())) {
                return "/data/media/0";
            }
            if (mnt.isDirectory() && !"/storage/sdcard0".equals(primary)) {
                return "/mnt/sdcard";
            }
            return null;
        }
        return null;
    }

    /**
     * Volume paths exported to the PC in USB mass-storage mode (2026-07-05).
     * Layman: Y1 shares the big internal MicroSD; Y2 shares internal 8GB + removable MicroSD slot.
     * Tech: vold share order — Y2 {@code sdcard0} then {@code sdcard1} for dual LUN when hardware allows.
     */
    public static List<String> getUmsExportVolumePaths() {
        List<String> paths = new ArrayList<String>();
        if (isY2() || isA5()) {
            String secondary = secondaryStoragePath();
            if (secondary != null) paths.add(secondary);
            paths.add(primaryStoragePath());
        } else {
            paths.add("/storage/sdcard0");
        }
        return paths;
    }

    public static File getPrimaryStorageRoot() {
        if (isY2() || isA5()) {
            File primary = new File(primaryStoragePath());
            if (primary.isDirectory()) return primary;
        }
        return new File("/storage/sdcard0");
    }

    /** Y2/A5 internal when both volumes are mounted; null on Y1 / single-volume A5. */
    public static File getSecondaryStorageRoot() {
        if (isY2() || isA5()) {
            String secondary = secondaryStoragePath();
            if (secondary == null) return null;
            File sd0 = new File(secondary);
            if (sd0.isDirectory() && new File(primaryStoragePath()).isDirectory()) {
                return sd0;
            }
        }
        return null;
    }

    /** All mounted user volumes — both on Y2/A5 dual, MicroSD only on Y1. */
    public static java.util.List<File> getStorageRoots() {
        java.util.List<File> roots = new java.util.ArrayList<File>();
        roots.add(getPrimaryStorageRoot());
        File secondary = getSecondaryStorageRoot();
        if (secondary != null) {
            roots.add(secondary);
        }
        // 2026-07-11 — Emulator: also expose /mnt/sdcard when it is a distinct mount.
        if (isEmulator() || isA5()) {
            File mnt = new File("/mnt/sdcard");
            if (mnt.isDirectory()) {
                String mntPath = mnt.getAbsolutePath();
                boolean known = false;
                for (int i = 0; i < roots.size(); i++) {
                    if (mntPath.equals(roots.get(i).getAbsolutePath())) {
                        known = true;
                        break;
                    }
                }
                if (!known) roots.add(mnt);
            }
        }
        return roots;
    }

    /** Roots the file browser may open — same as {@link #getStorageRoots()} on Y2. */
    public static java.util.List<File> getBrowsableStorageRoots() {
        return getStorageRoots();
    }

    /** True when {@code dir} is a top-level /storage/sdcard* mount on this device. */
    public static boolean isStorageVolumeRoot(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        String path = dir.getAbsolutePath();
        for (File root : getStorageRoots()) {
            if (path.equals(root.getAbsolutePath())) return true;
        }
        return false;
    }

    /** User-facing label for a storage volume (Settings, folder browser cross-links). */
    public static String storageRootLabel(Context ctx, File root) {
        if (root == null) return "";
        String path = root.getAbsolutePath();
        if (isY2() || isA5()) {
            if (path.equals(primaryStoragePath()) || path.equals(getPrimaryStorageRoot().getAbsolutePath())) {
                return ctx != null ? ctx.getString(R.string.storage_volume_microsd)
                        : "MicroSD";
            }
            if (secondaryStoragePath() != null && path.equals(secondaryStoragePath())) {
                return ctx != null ? ctx.getString(R.string.storage_volume_internal)
                        : "Internal Storage";
            }
        }
        return ctx != null ? ctx.getString(R.string.storage_volume_microsd) : "MicroSD";
    }

    /** True when the user-facing MicroSD volume is mounted (Y2: sdcard1; Y1/A5: primary). */
    public static boolean isMicroSdPresent() {
        if (testMicroSdPresentOverride != null) {
            return testMicroSdPresentOverride.booleanValue();
        }
        if (isY2() || isA5()) {
            return new File(primaryStoragePath()).isDirectory();
        }
        return new File("/storage/sdcard0").isDirectory();
    }

    /** Y2/A5 pref: allow new downloads/saves on internal storage (library scans always include both). */
    public static boolean useInternalForNewMedia(Context ctx) {
        return PRIMARY_MEDIA_INTERNAL.equals(resolvePrimaryMediaPref(ctx));
    }

    /** Resolved Y2/A5 primary medium — smart default when unset; Y1 always microsd path. */
    public static String resolvePrimaryMediaPref(Context ctx) {
        if ((!isY2() && !isA5()) || ctx == null) return PRIMARY_MEDIA_MICROSD;
        android.content.SharedPreferences prefs =
                ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String explicit = prefs.getString(PREF_Y2_PRIMARY_MEDIA, null);
        if (PRIMARY_MEDIA_MICROSD.equals(explicit) || PRIMARY_MEDIA_INTERNAL.equals(explicit)) {
            return explicit;
        }
        if (prefs.contains(PREF_Y2_USE_INTERNAL_MEDIA)) {
            return prefs.getBoolean(PREF_Y2_USE_INTERNAL_MEDIA, false)
                    ? PRIMARY_MEDIA_INTERNAL : PRIMARY_MEDIA_MICROSD;
        }
        return isMicroSdPresent() ? PRIMARY_MEDIA_MICROSD : PRIMARY_MEDIA_INTERNAL;
    }

    /** Persist Y2/A5 primary medium choice from Settings submenu. */
    public static void setPrimaryMediaPref(Context ctx, String medium) {
        if ((!isY2() && !isA5()) || ctx == null) return;
        if (!PRIMARY_MEDIA_MICROSD.equals(medium) && !PRIMARY_MEDIA_INTERNAL.equals(medium)) return;
        android.content.SharedPreferences prefs =
                ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(PREF_Y2_PRIMARY_MEDIA, medium)
                .putBoolean(PREF_Y2_USE_INTERNAL_MEDIA, PRIMARY_MEDIA_INTERNAL.equals(medium))
                .commit();
    }

    /** Preferred root for new user media (Reach saves, podcast downloads, Solar_Covers). */
    public static File getNewMediaRoot(Context ctx) {
        if (useInternalForNewMedia(ctx)) {
            File internal = getSecondaryStorageRoot();
            if (internal != null) return internal;
        }
        return getPrimaryStorageRoot();
    }

    /** Rockbox lives on internal storage on Y2; on Y1 the user SD card is the only mount. */
    public static File getRockboxRoot() {
        if (isY2()) {
            File internal = getSecondaryStorageRoot();
            if (internal != null) return internal;
        }
        return getPrimaryStorageRoot();
    }

    /** Reset → MicroSD wipe target — primary user volume on each device. */
    public static File getMicroSdWipeRoot() {
        return getPrimaryStorageRoot();
    }

    public static java.util.List<File> getMusicRoots() {
        return subdirRoots("Music");
    }

    public static java.util.List<File> getPodcastRoots() {
        return subdirRoots("Podcasts");
    }

    public static java.util.List<File> getVideoRoots() {
        return subdirRoots("Videos");
    }

    public static java.util.List<File> getThemeRoots() {
        return subdirRoots("Themes");
    }

    public static java.util.List<File> getPhotoRoots() {
        java.util.List<File> list = new java.util.ArrayList<File>();
        for (File root : getStorageRoots()) {
            list.add(new File(root, "Pictures"));
            list.add(new File(root, "DCIM"));
        }
        return list;
    }

    public static java.util.List<File> getFmRecordingRoots() {
        return subdirRoots("FM Recordings");
    }

    private static java.util.List<File> subdirRoots(String subdir) {
        java.util.List<File> list = new java.util.ArrayList<File>();
        for (File root : getStorageRoots()) {
            list.add(new File(root, subdir));
        }
        return list;
    }

    public static void setCachedFamilyForTest(String family) {
        cachedFamily = family;
    }

    static void resetCacheForTest() {
        cachedFamily = null;
        testMicroSdPresentOverride = null;
    }

    /** Unit tests — simulate SD mount without /storage paths. */
    static void setMicroSdPresentForTest(boolean present) {
        testMicroSdPresentOverride = present;
    }

    static String resolvePrimaryMediaPrefForTest(String explicitPref, Boolean legacyInternal) {
        if (PRIMARY_MEDIA_MICROSD.equals(explicitPref) || PRIMARY_MEDIA_INTERNAL.equals(explicitPref)) {
            return explicitPref;
        }
        if (legacyInternal != null) {
            return legacyInternal ? PRIMARY_MEDIA_INTERNAL : PRIMARY_MEDIA_MICROSD;
        }
        return isMicroSdPresent() ? PRIMARY_MEDIA_MICROSD : PRIMARY_MEDIA_INTERNAL;
    }

    static String detectFamilyForTest(String cpuHardware, String boardHardware, int sdkInt, String model) {
        cachedFamily = null;
        return probeFamily(cpuHardware, boardHardware, sdkInt, model, "");
    }

    /** Unit / emulator — include manufacturer tokens (timmkoo, etc.). */
    static String detectFamilyForTest(String cpuHardware, String boardHardware, int sdkInt,
            String model, String manufacturer) {
        cachedFamily = null;
        return probeFamily(cpuHardware, boardHardware, sdkInt, model, manufacturer);
    }

    private static String detectFamily() {
        if (cachedFamily != null) return cachedFamily;
        synchronized (DeviceFeatures.class) {
            if (cachedFamily != null) return cachedFamily;
            // 2026-07-11 — Lab/emulator pin before hardware probe.
            String prop = readSystemProperty(PROP_DEVICE_FAMILY);
            if ("a5".equals(prop) || "y1".equals(prop) || "y2".equals(prop)) {
                cachedFamily = prop;
                return cachedFamily;
            }
            String board = (Build.HARDWARE != null ? Build.HARDWARE : "")
                    + " " + (Build.BOARD != null ? Build.BOARD : "");
            String manu = (Build.MANUFACTURER != null ? Build.MANUFACTURER : "")
                    + " " + (Build.BRAND != null ? Build.BRAND : "")
                    + " " + (Build.PRODUCT != null ? Build.PRODUCT : "");
            cachedFamily = probeFamily(readProcCpuHardware(), board, Build.VERSION.SDK_INT,
                    Build.MODEL != null ? Build.MODEL : "", manu);
            try {
                Log.i(TAG, "detected device family: " + cachedFamily
                        + " hw=" + Build.HARDWARE + " board=" + Build.BOARD
                        + " sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL
                        + " manu=" + Build.MANUFACTURER);
            } catch (Throwable ignored) {}
            return cachedFamily;
        }
    }

    /**
     * 2026-07-11 — A5 tokens before SDK/Y1 defaults so KitKat-class A5 never becomes Y2.
     * Was: CPU → SDK ≥19 Y2 / ≤16 Y1 → model y1/y2 → default y1.
     * Now: prop/override elsewhere; then A5 model/manu; then MT6582/MT6572; then SDK/model.
     */
    private static String probeFamily(String cpuHardware, String boardHardware, int sdkInt,
            String model, String manufacturer) {
        String cpu = cpuHardware != null ? cpuHardware.toLowerCase(Locale.US) : "";
        String board = boardHardware != null ? boardHardware.toLowerCase(Locale.US) : "";
        String m = model != null ? model.toLowerCase(Locale.US) : "";
        String manu = manufacturer != null ? manufacturer.toLowerCase(Locale.US) : "";
        if (looksLikeA5(m, manu)) return "a5";
        if (cpu.contains("mt6582") || board.contains("mt6582")) return "y2";
        if (cpu.contains("mt6572") || board.contains("mt6572")) return "y1";
        if (sdkInt >= 19) return "y2";
        if (sdkInt <= 16) return "y1";
        if (m.contains("y2")) return "y2";
        if (m.contains("y1")) return "y1";
        return "y1";
    }

    /** Model/brand strings that mean Timmkoo A5 (placeholders until props confirmed). */
    private static boolean looksLikeA5(String m, String manu) {
        if (m.contains("y1") || m.contains("y2")) return false;
        if (m.contains("a5")) return true;
        if (manu.contains("timmkoo") || m.contains("timmkoo")) return true;
        // 2026-07-11 — Some A5 lab touch keyboards report generic.
        if (m.equals("generic") && android.os.Build.VERSION.SDK_INT == 19) return false; // Y2 sdk emulator
        return false;
    }

    private static String readSystemProperty(String key) {
        if (key == null || key.length() == 0) return "";
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, "");
            return v != null ? String.valueOf(v).trim().toLowerCase(Locale.US) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readProcCpuHardware() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase(Locale.US).startsWith("hardware")) {
                    int colon = trimmed.indexOf(':');
                    if (colon >= 0 && colon + 1 < trimmed.length()) {
                        return trimmed.substring(colon + 1).trim();
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
        }
        return "";
    }
}
