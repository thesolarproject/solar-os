package com.solar.launcher;

import android.content.Context;
import android.media.AudioManager;

/**
 * Single music/podcast audio-focus owner — cooperates with FM / Stem / Mix.
 * Layman: Solar asks Android for the speaker so other apps and FM don’t fight it.
 * Technical: STREAM_MUSIC focus; abandon on exclusive jam or teardown.
 * Was: only FmAudioRouter requested focus. Reversal: skip requestAudioFocus calls.
 * 2026-07-19
 */
public final class SolarAudioFocus {
    private static AudioManager.OnAudioFocusChangeListener listener;
    private static boolean held;

    private SolarAudioFocus() {}

    /** Request focus before music/podcast prepare. Returns true if granted or already held. */
    public static boolean request(Context ctx) {
        if (ctx == null) return false;
        if (StemOrMixSession.isActive()) {
            // Stem/Mix own loudness — leave STREAM_MUSIC max; still hold focus briefly.
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            if (listener == null) {
                listener = new AudioManager.OnAudioFocusChangeListener() {
                    @Override
                    public void onAudioFocusChange(int focusChange) {
                        // Pause/duck left to callers; we only track hold bit. 2026-07-19
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            held = false;
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            held = true;
                        }
                    }
                };
            }
            int r = am.requestAudioFocus(listener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            held = r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            return held;
        } catch (Exception e) {
            return false;
        }
    }

    /** Drop focus when leaving music (FM takeover / destroy). */
    public static void abandon(Context ctx) {
        if (ctx == null || listener == null) {
            held = false;
            return;
        }
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.abandonAudioFocus(listener);
        } catch (Exception ignored) {
        } finally {
            held = false;
        }
    }

    public static boolean isHeld() {
        return held;
    }
}
