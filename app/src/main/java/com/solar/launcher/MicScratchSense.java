package com.solar.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Lightweight wheel-scrape mic probe for {@link MicScrollBoost}.
 *
 * <p>Does <b>not</b> record to disk. Runs AudioRecord only while the wheel is
 * active (notch within idle window), then releases.
 *
 * <p>Outputs (thread-safe):
 * <ul>
 *   <li>{@link #volumeLevel()} — broadband RMS (how loud the pad scrape is)</li>
 *   <li>{@link #hfLevel()} — high-frequency / zero-cross texture (finger scrape vs bass music)</li>
 *   <li>{@link #scratchLevel()} — fused energy for boost</li>
 *   <li>{@link #isFingerContact()} — finger still on the dial (volume + HF)</li>
 * </ul>
 * Never sets CW/CCW — KEY owns direction.
 */
public final class MicScratchSense {

    private static final int SAMPLE_HZ = 16000;
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    /** Keep probe this long after last wheel notch (ms) so lift can be heard. */
    private static final long IDLE_STOP_MS = 520L;
    private static final long POLL_MS = 20L;

    /** Contact floors — quiet room / music bleed stay below. */
    public static final float VOLUME_CONTACT = 0.22f;
    public static final float HF_CONTACT = 0.18f;
    /** Either channel firm enough alone, or both mildly present. */
    public static final float FUSED_CONTACT = 0.28f;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();

    private volatile float volumeLevel;
    private volatile float hfLevel;
    private volatile float scratchLevel;
    private volatile boolean fingerContact;
    private volatile boolean wantRunning;
    private Thread worker;
    private AudioRecord recorder;
    private long lastNotchMs = -1L;
    private ContactListener contactListener;
    private boolean lastPostedContact;

    /** Main-thread: finger contact edge (true = on pad, false = lift). */
    public interface ContactListener {
        void onFingerContactChanged(boolean contact);
    }

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (lastNotchMs < 0 || (now - lastNotchMs) >= IDLE_STOP_MS) {
                stopInternal();
            } else {
                mainHandler.postDelayed(this, IDLE_STOP_MS / 3);
            }
        }
    };

    public MicScratchSense(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setContactListener(ContactListener listener) {
        this.contactListener = listener;
    }

    /** Call on every HW wheel notch — arms/refreshes the short-lived probe. */
    public void onWheelNotch() {
        lastNotchMs = SystemClock.uptimeMillis();
        if (!hasMicPermission()) {
            volumeLevel = 0f;
            hfLevel = 0f;
            scratchLevel = 0f;
            fingerContact = false;
            return;
        }
        startInternal();
        mainHandler.removeCallbacks(idleCheck);
        mainHandler.postDelayed(idleCheck, IDLE_STOP_MS);
    }

    /** Broadband scrape loudness (~0 quiet … ~2+ firm). */
    public float volumeLevel() {
        return volumeLevel;
    }

    /** High-frequency / scrape texture (~0 smooth … ~2+ pad creak). */
    public float hfLevel() {
        return hfLevel;
    }

    /** Fused energy for boost (max-biased volume+HF). */
    public float scratchLevel() {
        return scratchLevel;
    }

    /** True when mic says a finger is still working the dial. */
    public boolean isFingerContact() {
        return fingerContact;
    }

    public void release() {
        lastNotchMs = -1L;
        mainHandler.removeCallbacks(idleCheck);
        stopInternal();
        volumeLevel = 0f;
        hfLevel = 0f;
        scratchLevel = 0f;
        fingerContact = false;
        lastPostedContact = false;
    }

    private boolean hasMicPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startInternal() {
        synchronized (lock) {
            if (wantRunning) return;
            wantRunning = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    runCaptureLoop();
                }
            }, "solar-mic-scratch");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void stopInternal() {
        Thread t;
        synchronized (lock) {
            wantRunning = false;
            t = worker;
            worker = null;
            AudioRecord r = recorder;
            recorder = null;
            if (r != null) {
                try {
                    r.stop();
                } catch (Exception ignored) {}
                try {
                    r.release();
                } catch (Exception ignored) {}
            }
        }
        if (t != null) {
            try {
                t.join(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        volumeLevel *= 0.25f;
        hfLevel *= 0.25f;
        scratchLevel *= 0.25f;
        if (volumeLevel < 0.04f) volumeLevel = 0f;
        if (hfLevel < 0.04f) hfLevel = 0f;
        if (scratchLevel < 0.04f) scratchLevel = 0f;
        publishContact(false);
    }

    private void publishContact(boolean contact) {
        fingerContact = contact;
        if (contact == lastPostedContact) return;
        lastPostedContact = contact;
        final ContactListener l = contactListener;
        if (l == null) return;
        final boolean c = contact;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    l.onFingerContactChanged(c);
                } catch (Exception ignored) {}
            }
        });
    }

    private void runCaptureLoop() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_HZ, CHANNEL, ENCODING);
        if (minBuf <= 0) {
            wantRunning = false;
            return;
        }
        // ~40ms blocks for lift detection latency without thrashing.
        int bufSize = Math.max(minBuf, SAMPLE_HZ / 25);
        short[] buf = new short[bufSize / 2];
        AudioRecord ar;
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_HZ, CHANNEL, ENCODING, bufSize);
        } catch (Exception e) {
            wantRunning = false;
            return;
        }
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
            try {
                ar.release();
            } catch (Exception ignored) {}
            wantRunning = false;
            return;
        }
        synchronized (lock) {
            recorder = ar;
        }
        try {
            ar.startRecording();
        } catch (Exception e) {
            try {
                ar.release();
            } catch (Exception ignored) {}
            synchronized (lock) {
                recorder = null;
            }
            wantRunning = false;
            return;
        }

        // High-pass residual (scrape) + zero-crossing rate (texture / frequency proxy).
        float prev = 0f;
        while (wantRunning) {
            int n;
            try {
                n = ar.read(buf, 0, buf.length);
            } catch (Exception e) {
                break;
            }
            if (n <= 0) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            double sumSq = 0;
            double sumHpSq = 0;
            int zc = 0;
            float lastHp = 0f;
            for (int i = 0; i < n; i++) {
                float s = buf[i] / 32768f;
                float hp = s - prev;
                prev = s;
                sumSq += s * s;
                sumHpSq += hp * hp;
                if (i > 0 && ((lastHp >= 0f && hp < 0f) || (lastHp < 0f && hp >= 0f))) {
                    zc++;
                }
                lastHp = hp;
            }
            float rms = (float) Math.sqrt(sumSq / n);
            float hpRms = (float) Math.sqrt(sumHpSq / n);
            // Zero-cross rate 0..1 relative to Nyquist-ish pad scrape band.
            float zcr = (n > 1) ? (zc / (float) (n - 1)) : 0f;
            // Scale into comparable units for boost / contact.
            float vol = rms * 14f;
            // HF: high-pass energy + zero-cross (scrape is bright + busy).
            float hf = hpRms * 16f + zcr * 2.2f;

            volumeLevel = smooth(volumeLevel, vol);
            hfLevel = smooth(hfLevel, hf);
            // Fuse: loud scrape OR bright scrape; max-bias so either channel can drive boost.
            float fused = Math.max(volumeLevel, hfLevel) * 0.65f
                    + Math.min(volumeLevel, hfLevel) * 0.35f;
            scratchLevel = smooth(scratchLevel, fused);

            boolean contact = volumeLevel >= VOLUME_CONTACT
                    || hfLevel >= HF_CONTACT
                    || fused >= FUSED_CONTACT;
            publishContact(contact);
        }

        try {
            ar.stop();
        } catch (Exception ignored) {}
        try {
            ar.release();
        } catch (Exception ignored) {}
        synchronized (lock) {
            if (recorder == ar) recorder = null;
        }
    }

    private static float smooth(float cur, float next) {
        if (next > cur) {
            return cur * 0.35f + next * 0.65f; // attack fast (touch)
        }
        return cur * 0.72f + next * 0.28f; // release slower but still sub-100ms
    }
}
