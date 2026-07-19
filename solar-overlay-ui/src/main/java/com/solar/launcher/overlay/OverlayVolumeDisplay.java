package com.solar.launcher.overlay;

import android.content.Context;
import android.media.AudioManager;

/**
 * 2026-07-18 — 0–100 volume display for companion / overlay (matches HearingSafetyVolume).
 * Layman: the volume bar always fills to 100% of what Solar allows — never stuck at 80% of the strip.
 * Technical: reads persist.solar.hearing_safety + abs_max props; maps HW index → display units.
 * Reversal: delete; ChipOverlayHost uses raw getStreamMaxVolume again.
 */
public final class OverlayVolumeDisplay {

    public static final int DISPLAY_MAX = 100;
    /** Matches HearingSafetyVolume.PROP_ENABLED */
    private static final String PROP_HEARING_SAFETY = "persist.solar.hearing_safety";
    /** Matches HearingSafetyVolume.PROP_TEMP_UNLOCK */
    private static final String PROP_TEMP_UNLOCK = "persist.solar.hearing_safety.temp";
    /** Matches HearingSafetyVolume.PROP_ABSOLUTE_MAX */
    private static final String PROP_ABSOLUTE_MAX = "persist.solar.volume.abs_max";
    private static final float CAP_RATIO = 0.80f;
    private static final int DEFAULT_ABS = 15;

    private OverlayVolumeDisplay() {}

    /** Always 100 for sliders. */
    public static int getDisplayMax() {
        return DISPLAY_MAX;
    }

    /**
     * True when HS on, not temp-unlocked, headset/BT route, and display is at 100%.
     * 2026-07-18 — Ear never on speaker (cap is headphone-only).
     */
    public static boolean shouldShowEar(Context ctx) {
        if (!isCapActive(ctx)) return false;
        return getDisplayVolume(ctx) >= DISPLAY_MAX;
    }

    /**
     * Step media volume using display mapping; at HS cap extra +1 temp-unlocks via prop.
     * @return new display 0–100
     */
    public static int adjustDisplay(Context ctx, boolean up) {
        AudioManager am = am(ctx);
        if (am == null) return 0;
        int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int reported = 1;
        try {
            reported = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception ignored) {}
        int abs = absoluteMax(am, reported);
        boolean cap = isCapActive(ctx);
        int eff = cap ? Math.max(1, Math.round(abs * CAP_RATIO)) : abs;
        if (up && cur >= eff) {
            if (cap) {
                setTempUnlockedProp(true);
                // Same loudness; scale opens — display drops toward ~80.
                return indexToDisplay(cur, abs);
            }
            // Already at top (speaker / HS off) — paint 100 if at live max.
            if (cur >= reported) return DISPLAY_MAX;
            return indexToDisplay(cur, Math.min(abs, reported));
        }
        int target = cur;
        if (up && cur < eff) target = cur + 1;
        else if (!up && cur > 0) target = cur - 1;
        if (target > reported) target = reported;
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        } catch (Exception ignored) {}
        return getDisplayVolume(ctx);
    }

    /** Map 0–100 slider value to HW index and apply. */
    public static void setFromDisplay(Context ctx, int display) {
        AudioManager am = am(ctx);
        if (am == null) return;
        int eff = effectiveMax(ctx, am);
        int idx = displayToIndex(display, eff);
        try {
            int reported = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            if (idx > reported) idx = reported;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, idx, 0);
        } catch (Exception ignored) {}
    }

    public static int indexToDisplay(int index, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        return Math.min(DISPLAY_MAX, Math.max(0, index * DISPLAY_MAX / effectiveMax));
    }

    public static int displayToIndex(int display, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        int clamped = Math.max(0, Math.min(DISPLAY_MAX, display));
        return Math.max(0, Math.min(effectiveMax, clamped * effectiveMax / DISPLAY_MAX));
    }

    /** Display 0–100; at live OS max with no cap → always 100. */
    public static int getDisplayVolume(Context ctx) {
        AudioManager am = am(ctx);
        if (am == null) return 0;
        int idx = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        int reported = 1;
        try {
            reported = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception ignored) {}
        if (!isCapActive(ctx) && reported > 0 && idx >= reported) {
            return DISPLAY_MAX;
        }
        int eff = effectiveMax(ctx, am);
        int scale = eff;
        if (!isCapActive(ctx) && reported > 0) {
            scale = Math.min(eff, reported);
        }
        return indexToDisplay(idx, scale);
    }

    private static int effectiveMax(Context ctx, AudioManager am) {
        int reported = 1;
        try {
            reported = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        } catch (Exception ignored) {}
        int abs = absoluteMax(am, reported);
        if (!isCapActive(ctx)) return abs;
        return Math.max(1, Math.round(abs * CAP_RATIO));
    }

    /**
     * 2026-07-18 — Prefer live stream max; prop only if higher (headset EU unlock path).
     * Was: Math.max(DEFAULT_ABS, reported) invented 15 on devices with lower true max.
     */
    private static int absoluteMax(AudioManager am, int reported) {
        int fromProp = readIntProp(PROP_ABSOLUTE_MAX, 0);
        if (fromProp > reported && !isSpeakerOnly(am)) return fromProp;
        if (reported > 0) return reported;
        if (fromProp > 0) return fromProp;
        return DEFAULT_ABS;
    }

    private static boolean isCapActive(Context ctx) {
        if (!isSafetyEnabled() || isTempUnlocked()) return false;
        AudioManager am = am(ctx);
        return am != null && !isSpeakerOnly(am);
    }

    private static boolean isSpeakerOnly(AudioManager am) {
        if (am == null) return false;
        try {
            if (am.isWiredHeadsetOn()) return false;
        } catch (Throwable ignored) {}
        try {
            if (am.isBluetoothA2dpOn()) return false;
        } catch (Throwable ignored) {}
        try {
            if (am.isBluetoothScoOn()) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    private static boolean isSafetyEnabled() {
        return "1".equals(readProp(PROP_HEARING_SAFETY, "0"));
    }

    private static boolean isTempUnlocked() {
        return "1".equals(readProp(PROP_TEMP_UNLOCK, "0"));
    }

    private static void setTempUnlockedProp(boolean unlocked) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class)
                    .invoke(null, PROP_TEMP_UNLOCK, unlocked ? "1" : "0");
        } catch (Throwable ignored) {}
    }

    private static String readProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v != null ? String.valueOf(v) : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static int readIntProp(String key, int def) {
        try {
            String s = readProp(key, "");
            if (s == null || s.length() == 0) return def;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static AudioManager am(Context ctx) {
        if (ctx == null) return null;
        return (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }
}
