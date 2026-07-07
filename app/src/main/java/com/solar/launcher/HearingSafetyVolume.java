package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Headphone hearing safety — Solar-owned 80% warning and optional cap; bypasses OS safe-volume
 * limits when disabled (default). UI always uses 0–100 where 100 = top of the allowed range.
 */
public final class HearingSafetyVolume {

    /** Settings → Media — when on, cap at 80% of hardware max and show Solar warnings. */
    public static final String PREF_ENABLED = "hearing_safety_enabled";
    /** Xposed / AudioService reads this — "1" = enforce cap, "0" = bypass OS limits. */
    public static final String PROP_ENABLED = "persist.solar.hearing_safety";
    /** KitKat AudioService bypass when hearing safety is off. */
    public static final String PROP_SAFE_MEDIA_BYPASS = "audio.safemedia.bypass";

    /** Slider + transport HUD always use 0–100 display units. */
    public static final int DISPLAY_MAX = 100;
    /** Absolute hardware level (index) that triggers Solar's hearing warning. */
    private static final float WARN_ABSOLUTE_RATIO = 0.80f;
    private static final float CAP_RATIO = 0.80f;
    private static final int DEFAULT_ABSOLUTE_MAX = 15;
    private static final String PREFS_ABS = "solar_volume_abs_max";
    /** Same store as {@link MainActivity} settings toggles. */
    private static final String PREFS_SETTINGS = "SOLAR_SETTINGS";

    private static volatile int lastWarnIndex = -1;
    private static volatile int cachedAbsoluteMax = -1;

    private HearingSafetyVolume() {}

    /** True when Solar caps volume and shows hearing warnings (default off). */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
    }

    /** Persisted hardware max index for Xposed volume hooks (system_server has no app prefs). */
    public static final String PROP_ABSOLUTE_MAX = "persist.solar.volume.abs_max";

    /** Persist toggle, sync OS bypass props, clamp level when enabling. */
    public static void setEnabled(Context ctx, boolean enabled) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, enabled).apply();
        syncSystemBypass(app, enabled);
        if (enabled) {
            clampStream(app, AudioManager.STREAM_MUSIC);
        }
    }

    /** Boot / prefs load — apply bypass without changing the stored toggle. */
    public static void syncFromPrefs(Context ctx) {
        if (ctx == null) return;
        syncSystemBypass(ctx.getApplicationContext(), isEnabled(ctx));
    }

    /** Hardware stream index maximum (full steps, not OS-limited reporting). */
    public static int getAbsoluteMaxIndex(Context ctx, int streamType) {
        if (cachedAbsoluteMax > 0) return cachedAbsoluteMax;
        int stored = readStoredAbsoluteMax(ctx, streamType);
        if (stored > 0) {
            cachedAbsoluteMax = stored;
            return stored;
        }
        AudioManager am = audioManager(ctx);
        int reported = am != null ? Math.max(1, am.getStreamMaxVolume(streamType)) : DEFAULT_ABSOLUTE_MAX;
        // When OS reports a reduced max (safe volume), infer hardware max.
        int abs = reported;
        if (reported < DEFAULT_ABSOLUTE_MAX) {
            abs = DEFAULT_ABSOLUTE_MAX;
        }
        cachedAbsoluteMax = abs;
        storeAbsoluteMax(ctx, streamType, abs);
        return abs;
    }

    /** Top index Solar allows — full hardware max or 80% cap when hearing safety is on. */
    public static int getEffectiveMaxIndex(Context ctx, int streamType) {
        int abs = getAbsoluteMaxIndex(ctx, streamType);
        if (!isEnabled(ctx)) return abs;
        return Math.max(1, Math.round(abs * CAP_RATIO));
    }

    /** Map stream index to 0–100 display level (100 = effective max). */
    public static int indexToDisplay(int index, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        return Math.min(DISPLAY_MAX, Math.max(0, index * DISPLAY_MAX / effectiveMax));
    }

    /** Map 0–100 display level to stream index. */
    public static int displayToIndex(int display, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        int clamped = Math.max(0, Math.min(DISPLAY_MAX, display));
        return Math.max(0, Math.min(effectiveMax, clamped * effectiveMax / DISPLAY_MAX));
    }

    public static int getDisplayVolume(Context ctx, int streamType) {
        AudioManager am = audioManager(ctx);
        if (am == null) return 0;
        int idx = am.getStreamVolume(streamType);
        return indexToDisplay(idx, getEffectiveMaxIndex(ctx, streamType));
    }

    /** Step volume up/down with cap + warning; returns new stream index. */
    public static int adjustStreamIndex(Context ctx, int streamType, boolean up) {
        AudioManager am = audioManager(ctx);
        if (am == null || !MediaVolumeControl.isMediaVolumeStream(streamType)) return 0;
        int cur = am.getStreamVolume(streamType);
        int effMax = getEffectiveMaxIndex(ctx, streamType);
        if (up && cur < effMax) {
            cur++;
        } else if (!up && cur > 0) {
            cur--;
        }
        setStreamIndex(ctx, streamType, cur);
        return cur;
    }

    /** Set stream index with effective cap and hearing warning. */
    public static void setStreamIndex(Context ctx, int streamType, int index) {
        AudioManager am = audioManager(ctx);
        if (am == null || !MediaVolumeControl.isMediaVolumeStream(streamType)) return;
        int effMax = getEffectiveMaxIndex(ctx, streamType);
        int clamped = Math.max(0, Math.min(effMax, index));
        MediaVolumeControl.markInternalVolumeAdjust();
        am.setStreamVolume(streamType, clamped, MediaVolumeControl.FLAGS_NO_UI);
        maybeShowWarning(ctx, streamType, clamped);
    }

    /** Toast when crossing 80% of hardware max — only while hearing safety is enabled. */
    public static void maybeShowWarning(Context ctx, int streamType, int newIndex) {
        if (ctx == null || !isEnabled(ctx)) return;
        int absMax = getAbsoluteMaxIndex(ctx, streamType);
        int warnIndex = Math.max(1, Math.round(absMax * WARN_ABSOLUTE_RATIO));
        if (newIndex < warnIndex) {
            lastWarnIndex = -1;
            return;
        }
        if (lastWarnIndex >= warnIndex) return;
        lastWarnIndex = newIndex;
        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(app, app.getString(R.string.hearing_safety_warning), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Probe hardware max with OS bypass enabled — call from background bootstrap. */
    public static void probeAbsoluteMaxAsync(final Context ctx) {
        if (ctx == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncSystemBypass(ctx.getApplicationContext(), false);
                AudioManager am = audioManager(ctx);
                if (am == null) return;
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                if (max < DEFAULT_ABSOLUTE_MAX) max = DEFAULT_ABSOLUTE_MAX;
                cachedAbsoluteMax = max;
                storeAbsoluteMax(ctx, AudioManager.STREAM_MUSIC, max);
                if (RootShell.canRun()) {
                    RootShell.run("setprop " + PROP_ABSOLUTE_MAX + " " + max);
                }
                syncSystemBypass(ctx.getApplicationContext(), isEnabled(ctx));
            }
        }, "VolAbsProbe").start();
    }

    /** Test hook — display/index mapping without AudioManager. */
    static int indexToDisplayForTest(int index, int effectiveMax) {
        return indexToDisplay(index, effectiveMax);
    }

    static int displayToIndexForTest(int display, int effectiveMax) {
        return displayToIndex(display, effectiveMax);
    }

    static int effectiveMaxForTest(int absoluteMax, boolean safetyOn) {
        if (!safetyOn) return absoluteMax;
        return Math.max(1, Math.round(absoluteMax * CAP_RATIO));
    }

    static void resetWarnStateForTest() {
        lastWarnIndex = -1;
        cachedAbsoluteMax = -1;
    }

    private static void clampStream(Context ctx, int streamType) {
        AudioManager am = audioManager(ctx);
        if (am == null) return;
        int cur = am.getStreamVolume(streamType);
        int eff = getEffectiveMaxIndex(ctx, streamType);
        if (cur > eff) {
            setStreamIndex(ctx, streamType, eff);
        }
    }

    private static void syncSystemBypass(Context ctx, boolean safetyOn) {
        if (!RootShell.canRun()) return;
        RootShell.run("setprop " + PROP_ENABLED + " " + (safetyOn ? "1" : "0"));
        RootShell.run("setprop " + PROP_SAFE_MEDIA_BYPASS + " " + (safetyOn ? "false" : "true"));
        int abs = getAbsoluteMaxIndex(ctx, AudioManager.STREAM_MUSIC);
        if (abs > 0) {
            RootShell.run("setprop " + PROP_ABSOLUTE_MAX + " " + abs);
        }
        if (!safetyOn) {
            disableOsSafeMediaVolume(ctx);
        }
    }

    /** KitKat+ IAudioService.disableSafeMediaVolume — no-op when unavailable (Y1 JB). */
    private static void disableOsSafeMediaVolume(Context ctx) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "audio");
            Class<?> stub = Class.forName("android.media.IAudioService$Stub");
            Object svc = stub.getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            svc.getClass().getMethod("disableSafeMediaVolume", String.class)
                    .invoke(svc, ctx.getPackageName());
        } catch (Exception ignored) {
            RootShell.run("settings put global audio_safe_volume_state 3");
        }
    }

    private static AudioManager audioManager(Context ctx) {
        if (ctx == null) return null;
        return (AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private static int readStoredAbsoluteMax(Context ctx, int streamType) {
        if (ctx == null) return -1;
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_ABS, Context.MODE_PRIVATE);
        return sp.getInt("stream_" + streamType, -1);
    }

    private static void storeAbsoluteMax(Context ctx, int streamType, int max) {
        if (ctx == null) return;
        ctx.getApplicationContext().getSharedPreferences(PREFS_ABS, Context.MODE_PRIVATE)
                .edit().putInt("stream_" + streamType, max).apply();
    }
}
