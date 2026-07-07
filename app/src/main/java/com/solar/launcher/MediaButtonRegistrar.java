package com.solar.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;

import org.json.JSONObject;

/**
 * 2026-07-06 — Register MEDIA_BUTTON receiver without MainActivity on screen.
 * Layman: Solar must hear wheel keys while JJ/Rockbox is your home app.
 * Technical: AudioManager.registerMediaButtonEventReceiver for MediaBtnReceiver.
 * Reversal: register only from MainActivity.onCreate again.
 */
public final class MediaButtonRegistrar {

    /** Last successful register — informational only; slot is re-claimed from JJ on every call. */
    private static volatile boolean registered;
    /** Throttle expensive reclaim work on wheel down/up bursts; JJ steal recovery still re-arms quickly. */
    private static volatile long lastRegisterAtMs;
    private static final long REGISTER_THROTTLE_MS = 500L;

    private MediaButtonRegistrar() {}

    /**
     * 2026-07-06 — Claim MEDIA_BUTTON slot for Solar wheel handoff while JJ/Rockbox is foreground.
     * Layman: JJ steals the wheel receiver on startup; Solar must take it back for remap to work.
     * Technical: always call registerMediaButtonEventReceiver — JJ MainActivity re-registers too.
     * Reversal: restore one-shot registered gate (wheel breaks when JJ is HOME).
     */
    public static void ensureRegistered(Context context) {
        if (context == null) return;
        synchronized (MediaButtonRegistrar.class) {
            long t0 = SystemClock.uptimeMillis();
            if (registered && (t0 - lastRegisterAtMs) < REGISTER_THROTTLE_MS) {
                return;
            }
            try {
                AudioManager am = (AudioManager) context.getApplicationContext()
                        .getSystemService(Context.AUDIO_SERVICE);
                if (am == null) return;
                ComponentName cn = new ComponentName(
                        context.getPackageName(),
                        MainActivity.MediaBtnReceiver.class.getName());
                am.registerMediaButtonEventReceiver(cn);
                registered = true;
                lastRegisterAtMs = t0;
                // Debug logging removed for performance
                // Latency logging removed to reduce overhead
            } catch (Exception e) {
                registered = false;
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("err", e.getMessage());
                    DebugE93bdbLog.log("MediaButtonRegistrar.ensureRegistered",
                            "register failed", "H2", d);
                } catch (Exception ignored) {}
                // #endregion
                android.util.Log.w("MediaButtonRegistrar", "register failed: " + e.getMessage());
            }
        }
    }

    /** Test hook — allow re-register after unregister in unit tests. */
    static void resetForTest() {
        registered = false;
    }
}
