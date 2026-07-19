package com.solar.launcher.mix;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.solar.launcher.StemOrMixSession;
import com.solar.launcher.stem.StemBpm;
import com.solar.launcher.stem.StemControls;
import com.solar.launcher.stem.StemFaceView;
import com.solar.launcher.stem.StemTempoSync;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mix Player — three full-track decks on LEFT / RIGHT / PLAY pads.
 * Layman: layer three songs; mute a pad then hold it to scrub; Back picks a new song for that pad.
 * Technical: MixDeck×3 + StemFaceView; exclusive StemOrMixSession; beat sync via StemTempoSync.
 * Was: no Mix mode. Reversal: detach + remove STATE_MIX wiring.
 * 2026-07-19
 */
public final class MixPlayerHost {
    public interface HostCallbacks {
        SharedPreferences prefs();
        android.content.Context appContext();
        void setStatusTitle(String title);
        void onExitMixPlayer();
        /** BACK while playing — open library to reassign focused/last deck. */
        void onRequestReassign(int deckIndex);
        void pauseMainMusic();
        void stopCompetingAudio();
        void toast(String msg);
        void onMixSessionVolumeEnter();
        void onMixSessionVolumeExit();
    }

    private static final long EXIT_HOLD_MS = 600L;
    private static final long SCRUB_HOLD_MS = MixAssignSlots.HOLD_PLAY_START_MS;
    private static final int SCRUB_STEP_MS = 5000;
    private static final float FADE_EPS = MixSession.SCRUB_GAIN_EPS;

    private static volatile boolean sessionActive;

    private final HostCallbacks host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MixSession session = new MixSession();
    private final MixDeck[] decks = new MixDeck[MixSession.DECK_COUNT];
    private final AtomicInteger readyCount = new AtomicInteger();
    private final AtomicInteger loadGen = new AtomicInteger();

    private FrameLayout root;
    private StemFaceView face;
    private TextView statusLine;
    private boolean attached;
    private boolean ready;
    private boolean volumeMode;
    private boolean scrubArmed;
    private int scrubDeck = -1;
    private int scrubCursorMs;
    private boolean prevDown;
    private boolean nextDown;
    private boolean exitHoldFired;
    private final boolean[] padDown = new boolean[MixSession.DECK_COUNT];
    private final long[] padDownAt = new long[MixSession.DECK_COUNT];
    private final boolean[] padScrubHold = new boolean[MixSession.DECK_COUNT];
    private boolean centerDown;
    private boolean centerHoldVol;

    private final Runnable exitHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (prevDown && nextDown && !exitHoldFired) {
                exitHoldFired = true;
                host.onExitMixPlayer();
            }
        }
    };

    private final Runnable scrubHoldRunnable = new Runnable() {
        @Override
        public void run() {
            int d = pendingScrubDeck;
            if (d < 0 || d >= MixSession.DECK_COUNT) return;
            if (!padDown[d]) return;
            MixSession.DeckState st = session.deck(d);
            if (st == null || st.gain > FADE_EPS) return;
            MixDeck deck = decks[d];
            if (deck == null || !deck.isPrepared()) return;
            padScrubHold[d] = true;
            scrubArmed = true;
            scrubDeck = d;
            scrubCursorMs = deck.getPositionMs();
            volumeMode = false;
            paintFace();
            host.toast("Scrub");
        }
    };
    private int pendingScrubDeck = -1;

    private final Runnable centerHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (!centerDown) return;
            centerHoldVol = true;
            volumeMode = true;
            paintFace();
        }
    };

    private final Runnable driftNudge = new Runnable() {
        @Override
        public void run() {
            if (!attached || !ready || scrubArmed) return;
            // Soft nudge: keep decks looping; no hard seek war (scrub owns playhead).
            main.postDelayed(this, 2000L);
        }
    };

    public MixPlayerHost(HostCallbacks host) {
        this.host = host;
    }

    public static boolean isSessionActive() {
        return sessionActive;
    }

    public MixSession session() {
        return session;
    }

    /**
     * Attach Mix UI and load 1–3 tracks (null slots skipped).
     * 2026-07-19
     */
    public void attach(FrameLayout container, File[] tracks) {
        detach();
        sessionActive = true;
        StemOrMixSession.setActive(true);
        try {
            host.onMixSessionVolumeEnter();
        } catch (Exception ignored) {}
        try {
            host.stopCompetingAudio();
        } catch (Exception ignored) {}
        try {
            host.pauseMainMusic();
        } catch (Exception ignored) {}

        root = container;
        root.removeAllViews();
        android.content.Context ctx = host.appContext();
        face = new StemFaceView(ctx);
        statusLine = new TextView(ctx);
        statusLine.setTextColor(0xFFCCCCCC);
        statusLine.setTextSize(12f);
        statusLine.setPadding(12, 8, 12, 4);
        android.widget.LinearLayout col = new android.widget.LinearLayout(ctx);
        col.setOrientation(android.widget.LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0A0A0C);
        col.addView(statusLine, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(face, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        attached = true;
        ready = false;
        volumeMode = true;
        session.bindTracks(tracks);
        host.setStatusTitle("Mix");
        statusLine.setText(buildStatusText());
        paintFace();
        beginLoad();
    }

    public void detach() {
        main.removeCallbacks(exitHoldRunnable);
        main.removeCallbacks(scrubHoldRunnable);
        main.removeCallbacks(centerHoldRunnable);
        main.removeCallbacks(driftNudge);
        loadGen.incrementAndGet();
        boolean was = sessionActive;
        sessionActive = false;
        if (!com.solar.launcher.stem.StemPlayerHost.isSessionActive()) {
            StemOrMixSession.setActive(false);
        }
        if (was) {
            try {
                host.onMixSessionVolumeExit();
            } catch (Exception ignored) {}
        }
        for (int i = 0; i < decks.length; i++) {
            if (decks[i] != null) {
                decks[i].release();
                decks[i] = null;
            }
        }
        ready = false;
        attached = false;
        scrubArmed = false;
        if (root != null) {
            root.removeAllViews();
            root = null;
        }
        face = null;
        statusLine = null;
    }

    public boolean onKey(int keyCode, KeyEvent event) {
        if (event == null || !attached) return false;
        int action = event.getAction();
        // #region agent log
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("isPlayKey", isPlayKey(keyCode));
                d.put("isWheel", keyCode == 126 || keyCode == 127
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE);
                d.put("activeDeck", session.activeDeck());
                com.solar.launcher.Debug8b0481Log.log("MixPlayerHost.onKey", "mix key", "H5", d);
            } catch (Exception ignored) {}
        }
        // #endregion

        if (isPrevKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                prevDown = true;
                exitHoldFired = false;
                main.removeCallbacks(exitHoldRunnable);
                if (prevDown && nextDown) {
                    cancelPadHold(0);
                    cancelPadHold(1);
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginPadHold(0);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                prevDown = false;
                main.removeCallbacks(exitHoldRunnable);
                boolean scrubbed = endPadHold(0);
                if (!exitHoldFired && !scrubbed && !nextDown) {
                    onDeckTap(0);
                }
                exitHoldFired = false;
                return true;
            }
            return true;
        }
        if (isNextKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                nextDown = true;
                exitHoldFired = false;
                main.removeCallbacks(exitHoldRunnable);
                if (prevDown && nextDown) {
                    cancelPadHold(0);
                    cancelPadHold(1);
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginPadHold(1);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                nextDown = false;
                main.removeCallbacks(exitHoldRunnable);
                boolean scrubbed = endPadHold(1);
                if (!exitHoldFired && !scrubbed && !prevDown) {
                    onDeckTap(1);
                }
                exitHoldFired = false;
                return true;
            }
            return true;
        }
        if (isPlayKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                beginPadHold(2);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                boolean scrubbed = endPadHold(2);
                if (!scrubbed) onDeckTap(2);
                return true;
            }
            return true;
        }
        if (isCenterKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                centerDown = true;
                centerHoldVol = false;
                main.removeCallbacks(centerHoldRunnable);
                main.postDelayed(centerHoldRunnable, 280L);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                main.removeCallbacks(centerHoldRunnable);
                if (!centerHoldVol) {
                    volumeMode = true;
                    scrubArmed = false;
                    scrubDeck = -1;
                    paintFace();
                }
                centerDown = false;
                centerHoldVol = false;
                return true;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (action == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                if (scrubArmed) {
                    scrubArmed = false;
                    scrubDeck = -1;
                    paintFace();
                    return true;
                }
                int d = session.activeDeck();
                if (d < 0) d = 0;
                host.onRequestReassign(d);
            }
            return true;
        }
        return false;
    }

    public void onWheel(int rawSteps) {
        if (!attached || !ready) return;
        if (scrubArmed && scrubDeck >= 0 && scrubDeck < MixSession.DECK_COUNT) {
            MixDeck deck = decks[scrubDeck];
            if (deck == null) return;
            int volSteps = StemControls.volumeStepsFromWheel(rawSteps);
            // Wheel up (after polarity) advances scrub forward. 2026-07-19
            scrubCursorMs = MixAssignSlots.scrubWrap(
                    scrubCursorMs, deck.getDurationMs(), volSteps * SCRUB_STEP_MS);
            deck.seekTo(scrubCursorMs);
            statusLine.setText(formatScrub(scrubCursorMs, deck.getDurationMs()));
            return;
        }
        int d = session.activeDeck();
        if (d < 0 || d >= MixSession.DECK_COUNT) return;
        MixSession.DeckState st = session.deck(d);
        MixDeck deck = decks[d];
        if (st == null || deck == null) return;
        int steps = StemControls.volumeStepsFromWheel(rawSteps);
        float g = StemControls.nudgeGain(st.gain, steps);
        st.gain = g;
        deck.setGain(g);
        // LED-only — avoid full status rebuild every tick. 2026-07-19
        paintFace();
    }

    /**
     * Fade out then swap file on a live deck (mid-mix reassign).
     * 2026-07-19
     */
    public void fadeReplaceDeck(final int deckIndex, final File track) {
        if (!attached || deckIndex < 0 || deckIndex >= MixSession.DECK_COUNT) return;
        if (track == null || !track.isFile()) return;
        final MixDeck old = decks[deckIndex];
        final MixSession.DeckState st = session.deck(deckIndex);
        Runnable swap = new Runnable() {
            @Override
            public void run() {
                if (old != null) old.release();
                session.setSlot(deckIndex, track);
                loadOneDeck(deckIndex, loadGen.get());
            }
        };
        if (old != null && MixAssignSlots.needsFadeBeforeReplace(st != null ? st.gain : 0f, FADE_EPS)) {
            old.fadeTo(0f, swap);
            if (st != null) st.gain = 0f;
        } else {
            if (st != null) st.gain = 0f;
            swap.run();
        }
    }

    private void beginLoad() {
        final int gen = loadGen.incrementAndGet();
        readyCount.set(0);
        int need = 0;
        float masterBpm = StemBpm.DEFAULT_BPM;
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            MixSession.DeckState st = session.deck(i);
            if (st == null || !st.hasTrack()) continue;
            need++;
        }
        if (need == 0) {
            host.toast("Mix needs a track");
            host.onExitMixPlayer();
            return;
        }
        // Estimate master BPM from first filled slot after prepare — provisional here. 2026-07-19
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            loadOneDeck(i, gen);
        }
        final int needFinal = need;
        // Wait for ready via listeners; if none prepared, exit.
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (gen != loadGen.get()) return;
                if (readyCount.get() <= 0) {
                    host.toast("Mix could not load");
                    host.onExitMixPlayer();
                }
            }
        }, 15000L);
    }

    private void loadOneDeck(final int index, final int gen) {
        MixSession.DeckState st = session.deck(index);
        if (st == null || !st.hasTrack()) return;
        MixDeck deck = new MixDeck(host.appContext());
        decks[index] = deck;
        deck.setListener(new MixDeck.Listener() {
            @Override
            public void onReady(MixDeck d) {
                if (gen != loadGen.get()) return;
                float bpm = StemBpm.estimateFromDurationMs(d.getDurationMs());
                MixSession.DeckState s = session.deck(index);
                if (s != null) s.bpm = bpm;
                applyRates();
                int n = readyCount.incrementAndGet();
                d.setGain(0f);
                if (s != null) s.gain = 0f;
                d.play();
                maybeAllReady(n);
            }

            @Override
            public void onError(MixDeck d, String message) {
                if (gen != loadGen.get()) return;
                host.toast(message != null ? message : "Mix error");
            }

            @Override
            public void onComplete(MixDeck d) {
                // Loop handled inside MixDeck.
            }
        });
        try {
            float rate = 1f;
            deck.load(st.track, rate);
        } catch (Exception e) {
            host.toast(e.getMessage() != null ? e.getMessage() : "Mix load failed");
        }
    }

    private void applyRates() {
        float master = StemBpm.DEFAULT_BPM;
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            MixSession.DeckState s = session.deck(i);
            if (s != null && s.hasTrack()) {
                master = s.bpm;
                break;
            }
        }
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            MixSession.DeckState s = session.deck(i);
            MixDeck d = decks[i];
            if (s == null || d == null || !s.hasTrack()) continue;
            float rate = StemTempoSync.rateForSong(master, s.bpm, i);
            s.rate = rate;
            d.setRate(rate);
        }
    }

    private void maybeAllReady(int n) {
        int filled = session.filledCount();
        if (n < filled) return;
        ready = true;
        if (session.activeDeck() < 0) {
            for (int i = 0; i < MixSession.DECK_COUNT; i++) {
                if (session.deck(i) != null && session.deck(i).hasTrack()) {
                    session.setActiveDeck(i);
                    break;
                }
            }
        }
        paintFace();
        statusLine.setText(buildStatusText());
        host.setStatusTitle("Mix");
        main.removeCallbacks(driftNudge);
        main.postDelayed(driftNudge, 2000L);
    }

    private void onDeckTap(int deck) {
        if (!session.onDeckKey(deck)) {
            // Already focused — no-op (hold does scrub).
            paintFace();
            return;
        }
        MixSession.DeckState st = session.deck(deck);
        if (st != null && st.displayName != null && st.displayName.length() > 0) {
            host.toast(st.displayName);
        }
        paintFace();
        statusLine.setText(buildStatusText());
    }

    private void beginPadHold(int deck) {
        padDown[deck] = true;
        padDownAt[deck] = android.os.SystemClock.uptimeMillis();
        padScrubHold[deck] = false;
        pendingScrubDeck = deck;
        main.removeCallbacks(scrubHoldRunnable);
        main.postDelayed(scrubHoldRunnable, SCRUB_HOLD_MS);
    }

    private boolean endPadHold(int deck) {
        padDown[deck] = false;
        main.removeCallbacks(scrubHoldRunnable);
        boolean scrubbed = padScrubHold[deck];
        padScrubHold[deck] = false;
        return scrubbed;
    }

    private void cancelPadHold(int deck) {
        padDown[deck] = false;
        padScrubHold[deck] = false;
        main.removeCallbacks(scrubHoldRunnable);
    }

    private void paintFace() {
        if (face == null) return;
        float[] g = new float[] { 0f, 0f, 0f, 0f };
        int[] songs = new int[] { 0, 0, 0, 0 };
        // Map Mix decks → Stem face arms: L=1 Drums, R=2 Bass, Play=3 Melody. 2026-07-19
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            MixSession.DeckState st = session.deck(i);
            int zone = i + 1;
            if (st != null && st.hasTrack()) {
                g[zone] = st.gain;
                songs[zone] = i + 1;
            }
        }
        int zone = session.activeDeck() >= 0 ? session.activeDeck() + 1 : 1;
        face.setState(g, zone, true, false, 1f, !ready, songs, scrubArmed);
    }

    private String buildStatusText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            MixSession.DeckState st = session.deck(i);
            if (i > 0) sb.append("  ·  ");
            if (st == null || !st.hasTrack()) {
                sb.append((i + 1)).append(": —");
            } else {
                sb.append((i + 1)).append(": ").append(st.displayName);
                if (session.activeDeck() == i) sb.append(" ◀");
            }
        }
        if (scrubArmed) sb.append("  [scrub]");
        return sb.toString();
    }

    private static String formatScrub(int pos, int dur) {
        return formatMs(pos) + " / " + formatMs(dur);
    }

    private static String formatMs(int ms) {
        if (ms < 0) ms = 0;
        int s = ms / 1000;
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }

    private static boolean isPrevKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == 21;
    }

    private static boolean isNextKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == 22;
    }

    private static boolean isPlayKey(int keyCode) {
        // Never treat Y1 wheel MEDIA_PLAY/PAUSE as deck 3. 2026-07-19
        return com.solar.launcher.SolarPadKeys.isPadPlayKey(keyCode);
    }

    private static boolean isCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == 66;
    }
}
