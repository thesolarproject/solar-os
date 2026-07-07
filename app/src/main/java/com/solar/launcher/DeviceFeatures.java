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
 * 2026-07-05 — Y1/Y2 parity hub: single APK branches on MT6572 vs MT6582 at runtime.
 * Intentional divergences (not bugs): Y1 no power button (BACK-long overlay); Y2 power/BACK-long;
 *   Y1 AVRCP native BT patches; Y2 dual storage (sdcard1 primary, sdcard0 internal);
 *   Y2 hides volume/lock quick-menu chips (hardware buttons exist).
 * When changing: branch here once — do not hardcode /storage/sdcard0 for Y2 media paths.
 * Reversal: split APKs per device; remove isY1/isY2 branches at call sites.
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
    private static final String PREFS = "SOLAR_SETTINGS";
    private static volatile String cachedFamily;
    private static Boolean testMicroSdPresentOverride;

    private DeviceFeatures() {}

    public static String deviceFamily() {
        return detectFamily();
    }

    public static String deviceModel() {
        return isY2() ? "Y2" : "Y1";
    }

    public static String deviceModelLabel() {
        return isY2() ? "Y2" : "Y1";
    }

    public static int soulseekClientMinor() {
        // Reach-specific minor under experimental major 177 (not Nicotine+ 160.x).
        return isY2() ? 102 : 101;
    }

    public static String reachClientName() {
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

    public static boolean isY1() {
        return !isY2();
    }

    public static boolean hasRootAccess() {
        // Solar ROM: Y1 rockbox base ships su; Y2 ATA gets Y1 permissive su baked in build-rom.sh.
        return isY1() || isY2();
    }

    /** True when su actually works from this app — ROM-only APK installs must fail-open without root. */
    public static boolean canRunRootShell() {
        return RootShell.canRun();
    }

    /** Expected primary volume path — MicroSD on Y2, user SD on Y1 (no mount probe). */
    public static String primaryStoragePath() {
        return isY2() ? "/storage/sdcard1" : "/storage/sdcard0";
    }

    /** Expected internal volume on Y2 (sdcard0); null on Y1 where user media is the SD card only. */
    public static String secondaryStoragePath() {
        return isY2() ? "/storage/sdcard0" : null;
    }

    /**
     * Volume paths exported to the PC in USB mass-storage mode (2026-07-05).
     * Layman: Y1 shares the big internal MicroSD; Y2 shares internal 8GB + removable MicroSD slot.
     * Tech: vold share order — Y2 {@code sdcard0} then {@code sdcard1} for dual LUN when hardware allows.
     */
    public static List<String> getUmsExportVolumePaths() {
        List<String> paths = new ArrayList<String>();
        if (isY2()) {
            paths.add(secondaryStoragePath());
            paths.add(primaryStoragePath());
        } else {
            paths.add("/storage/sdcard0");
        }
        return paths;
    }

    public static File getPrimaryStorageRoot() {
        if (isY2()) {
            File sd1 = new File(primaryStoragePath());
            if (sd1.isDirectory()) return sd1;
        }
        return new File("/storage/sdcard0");
    }

    /** Y2 internal (sdcard0) when both volumes are mounted; null on Y1. */
    public static File getSecondaryStorageRoot() {
        if (isY2()) {
            File sd0 = new File(secondaryStoragePath());
            if (sd0.isDirectory() && new File(primaryStoragePath()).isDirectory()) {
                return sd0;
            }
        }
        return null;
    }

    /** All mounted user volumes — both on Y2, MicroSD only on Y1. */
    public static java.util.List<File> getStorageRoots() {
        java.util.List<File> roots = new java.util.ArrayList<File>();
        roots.add(getPrimaryStorageRoot());
        File secondary = getSecondaryStorageRoot();
        if (secondary != null) {
            roots.add(secondary);
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
        if (isY2()) {
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

    /** True when the user-facing MicroSD volume is mounted (Y2: sdcard1; Y1: sdcard0). */
    public static boolean isMicroSdPresent() {
        if (testMicroSdPresentOverride != null) {
            return testMicroSdPresentOverride.booleanValue();
        }
        if (isY2()) {
            return new File(primaryStoragePath()).isDirectory();
        }
        return new File("/storage/sdcard0").isDirectory();
    }

    /** Y2 pref: allow new downloads/saves on internal storage (library scans always include both). */
    public static boolean useInternalForNewMedia(Context ctx) {
        return PRIMARY_MEDIA_INTERNAL.equals(resolvePrimaryMediaPref(ctx));
    }

    /** Resolved Y2 primary medium — smart default when unset; Y1 always microsd path. */
    public static String resolvePrimaryMediaPref(Context ctx) {
        if (!isY2() || ctx == null) return PRIMARY_MEDIA_MICROSD;
        android.content.SharedPreferences prefs =
                ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String explicit = prefs.getString(PREF_Y2_PRIMARY_MEDIA, null);
        if (PRIMARY_MEDIA_MICROSD.equals(explicit) || PRIMARY_MEDIA_INTERNAL.equals(explicit)) {
            return explicit;
        }
        // Migrate legacy boolean toggle if user set it before submenu existed.
        if (prefs.contains(PREF_Y2_USE_INTERNAL_MEDIA)) {
            return prefs.getBoolean(PREF_Y2_USE_INTERNAL_MEDIA, false)
                    ? PRIMARY_MEDIA_INTERNAL : PRIMARY_MEDIA_MICROSD;
        }
        // First run: MicroSD when inserted, internal when Y2 started without a card.
        return isMicroSdPresent() ? PRIMARY_MEDIA_MICROSD : PRIMARY_MEDIA_INTERNAL;
    }

    /** Persist Y2 primary medium choice from Settings submenu. */
    public static void setPrimaryMediaPref(Context ctx, String medium) {
        if (!isY2() || ctx == null) return;
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
        return probeFamily(cpuHardware, boardHardware, sdkInt, model);
    }

    private static String detectFamily() {
        if (cachedFamily != null) return cachedFamily;
        synchronized (DeviceFeatures.class) {
            if (cachedFamily != null) return cachedFamily;
            String board = (Build.HARDWARE != null ? Build.HARDWARE : "")
                    + " " + (Build.BOARD != null ? Build.BOARD : "");
            cachedFamily = probeFamily(readProcCpuHardware(), board, Build.VERSION.SDK_INT,
                    Build.MODEL != null ? Build.MODEL : "");
            try {
                Log.i(TAG, "detected device family: " + cachedFamily
                        + " hw=" + Build.HARDWARE + " board=" + Build.BOARD
                        + " sdk=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL);
            } catch (Throwable ignored) {}
            return cachedFamily;
        }
    }

    private static String probeFamily(String cpuHardware, String boardHardware, int sdkInt, String model) {
        String cpu = cpuHardware != null ? cpuHardware.toLowerCase(Locale.US) : "";
        String board = boardHardware != null ? boardHardware.toLowerCase(Locale.US) : "";
        if (cpu.contains("mt6582") || board.contains("mt6582")) return "y2";
        if (cpu.contains("mt6572") || board.contains("mt6572")) return "y1";
        if (sdkInt >= 19) return "y2";
        if (sdkInt <= 16) return "y1";
        String m = model != null ? model.toLowerCase(Locale.US) : "";
        if (m.contains("y2")) return "y2";
        if (m.contains("y1")) return "y1";
        return "y1";
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
