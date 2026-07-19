package com.solar.launcher.stem;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stem Player — puck + Lalal prep + Gen1 pad-local loop + hold-stutter + optional mashup.
 * Layman: one song = classic four pads; 2–3 songs = all stems keep playing; cycle steers the wheel.
 * Technical: StemMixer[] (one per song, always running); cycle = activeSongPerZone only (no seek/swap).
 * Was: single mixer + replaceZoneStem (restarted pads). Reversal: that swap path.
 * 2026-07-19
 */
public final class StemPlayerHost {
    private static final long EXIT_HOLD_MS = 500L;

    /** True while Stem Player UI is attached — defer heavy library work. 2026-07-18 */
    private static volatile boolean sessionActive;

    public interface HostCallbacks {
        SharedPreferences prefs();
        /** App context — internal cache + preferred storage for stem staging. 2026-07-19 */
        android.content.Context appContext();
        File appCacheDir();
        void setStatusTitle(String title);
        void onExitStemPlayer();
        void pauseMainMusic();
        void stopCompetingAudio();
        void toast(String msg);
        /** Force STREAM_MUSIC to max so pad gains own loudness. 2026-07-19 */
        void onStemSessionVolumeEnter();
        /** Restore STREAM_MUSIC saved on enter. 2026-07-19 */
        void onStemSessionVolumeExit();
    }

    private final HostCallbacks host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobGen = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private FrameLayout root;
    private StemFaceView face;
    private TextView titleView;
    private TextView hintView;
    private TextView statusView;
    /** One StemMixer per song — all timelines run; gains audition stems. 2026-07-19 */
    private StemMixer[] mixers;
    private final StemSession session = new StemSession();
    /** Soft cross-song playhead nudge (song0 lead). 2026-07-19 */
    private final Runnable mashDriftRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ready || mixers == null || mixers.length < 2) return;
            StemMixer lead = mixers[0];
            if (lead == null) return;
            int pos = lead.getPositionMs();
            if (pos < 0) {
                main.postDelayed(this, 1000);
                return;
            }
            for (int i = 1; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                // Don't yank a song that is in a pad-local loop. 2026-07-19
                if (m == null || m.isLooping()) continue;
                try {
                    int d = Math.abs(m.getPositionMs() - pos);
                    if (d > 120) m.seekAllPlaying(pos);
                } catch (Exception ignored) {}
            }
            main.postDelayed(this, 1000);
        }
    };
    private File track;
    private java.util.ArrayList<File> tracks = new java.util.ArrayList<File>();
    private int activeZone;
    private boolean loading;
    private boolean ready;
    private boolean armed;
    /** True when wheel adjusts loop bars; hold-Center can peek volume without leaving loop-ctrl. */
    private boolean wheelLoopMode;
    /**
     * Per-stem: joined loop-control while A–B is playing (active song).
     * 2026-07-19
     */
    private final boolean[] zoneLoopCtrl = new boolean[StemMixer.STEM_COUNT];
    /** Chop step for hold-stutter slice (StemBpm.CHOP_FRAC). 2026-07-19 */
    private int stutterChopStep = StemControls.DEFAULT_STUTTER_CHOP_STEP;
    private boolean centerDown;
    private boolean centerHoldVolume;
    private int centerHoldZone = -1;
    private boolean centerHoldStemChanged;
    private static final long CENTER_HOLD_MS = 350L;
    private float savedLoopBars = StemControls.DEFAULT_LOOP_BARS;
    private boolean songFinished;

    private boolean prevDown;
    private boolean nextDown;
    private boolean exitHoldFired;
    /** Stem key hold → stutter (zone while held). 2026-07-19 */
    private int stemHoldZone = -1;
    private boolean stemStutterArmed;

    private final Runnable exitHoldRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit only when both side keys held — solo hold is stutter. 2026-07-19
            if (!StemControls.stemExitBothSidesHeld(prevDown, nextDown)) return;
            exitHoldFired = true;
            stopStemStutter();
            requestExit();
        }
    };

    private final Runnable stemStutterHoldRunnable = new Runnable() {
        @Override
        public void run() {
            StemMixer m = activeMixer();
            if (stemHoldZone < 0 || !ready || m == null) return;
            if (prevDown && nextDown) return; // exit hold wins
            stemStutterArmed = true;
            // Focus pad for stutter without cycling (UP will skip onStemKey). 2026-07-19
            activeZone = stemHoldZone;
            session.setActiveZone(stemHoldZone);
            armed = true;
            float bpm = m.getBpm();
            int slice = StemBpm.chopSliceMs(bpm, stutterChopStep);
            if (slice <= 0) slice = StemMixer.DEFAULT_STUTTER_MS;
            m.startHoldStutter(stemHoldZone, slice);
            refreshFace();
            updateStatusLine();
        }
    };

    /** After hold delay on a looping stem — wheel peeks volume until Center UP. 2026-07-19 */
    private final Runnable centerHoldRunnable = new Runnable() {
        @Override
        public void run() {
            StemMixer m = activeMixer();
            if (!centerDown || !ready || m == null) return;
            boolean audioLoop = m.isLooping();
            boolean inCtrl = activeZone >= 0 && activeZone < zoneLoopCtrl.length
                    && zoneLoopCtrl[activeZone];
            if (!StemControls.centerHoldVolumeEligible(audioLoop, inCtrl)) return;
            centerHoldVolume = true;
            centerHoldZone = activeZone;
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("zone", activeZone);
                d.put("holdVol", true);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.centerHold",
                        "hold-Center volume peek",
                        "H-HOLD",
                        d);
            } catch (Exception ignored) {}
            // #endregion
            refreshFace();
            updateStatusLine();
        }
    };

    public StemPlayerHost(HostCallbacks host) {
        this.host = host;
    }

    /** Library/art scanners should pause while Stem Player mixes. */
    public static boolean isSessionActive() {
        return sessionActive;
    }

    /** Build full-screen stem UI into parent (single track). */
    public View attach(Context ctx, ViewGroup parent, File trackFile) {
        java.util.ArrayList<File> one = new java.util.ArrayList<File>();
        if (trackFile != null) one.add(trackFile);
        return attach(ctx, parent, one);
    }

    /**
     * Build stem UI for 1–3 tracks (mashup). Gains start at 0 — jam from silence.
     * 2026-07-19
     */
    public View attach(Context ctx, ViewGroup parent, List<File> trackFiles) {
        detach();
        sessionActive = true;
        try {
            host.onStemSessionVolumeEnter();
        } catch (Exception ignored) {}
        cancelled.set(false);
        tracks.clear();
        if (trackFiles != null) {
            for (int i = 0; i < trackFiles.size() && tracks.size() < StemSession.MAX_SONGS; i++) {
                File f = trackFiles.get(i);
                if (f != null && f.isFile()) tracks.add(f);
            }
        }
        session.bindTracks(tracks);
        this.track = tracks.isEmpty() ? null : tracks.get(0);
        // No pad focused yet — first stem key focuses only (must match session −1). 2026-07-19
        this.activeZone = -1;
        this.loading = true;
        this.ready = false;
        this.armed = false;
        this.wheelLoopMode = false;
        this.stutterChopStep = StemControls.DEFAULT_STUTTER_CHOP_STEP;
        clearZoneLoopCtrl();
        resetCenterHold();
        stopStemStutter();
        this.songFinished = false;
        this.savedLoopBars = StemControls.DEFAULT_LOOP_BARS;

        root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF0A0A0C);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        col.setLayoutParams(colLp);
        col.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));

        // Title shows interacted song (marquee for long names on 480×360). 2026-07-19
        String title = track != null ? stripTrackExt(track.getName()) : "Stem Player";
        if (tracks.size() > 1) title = tracks.size() + " tracks · Stem mashup";
        titleView = label(ctx, title, 15, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setHorizontallyScrolling(true);
        titleView.setSelected(true);
        col.addView(titleView);

        statusView = label(ctx, "Preparing…", 12, false);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextColor(0xFFB0B0B8);
        col.addView(statusView);

        face = new StemFaceView(ctx);
        int faceSize = Math.min(
                ctx.getResources().getDisplayMetrics().widthPixels,
                ctx.getResources().getDisplayMetrics().heightPixels) * 78 / 100;
        if (faceSize < dp(ctx, 200)) faceSize = dp(ctx, 220);
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(faceSize, faceSize);
        faceLp.gravity = Gravity.CENTER_HORIZONTAL;
        faceLp.topMargin = dp(ctx, 2);
        face.setLayoutParams(faceLp);
        col.addView(face);

        // Feature parity: focus/volume/loop/stutter for 1 and 2–3; multi adds cycle line. 2026-07-19
        hintView = label(ctx,
                session.isMulti()
                        ? "Tap stem = focus · again = next song · hold = stutter\n"
                                + "Center = loop · wheel = volume · PREV+NEXT exit"
                        : "Tap stem = focus · hold = stutter · Center = loop\n"
                                + "Wheel = volume · PREV+NEXT exit",
                10, false);
        hintView.setGravity(Gravity.CENTER);
        hintView.setTextColor(0xFF8A8A96);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(ctx, 4);
        hintView.setLayoutParams(hintLp);
        col.addView(hintView);

        root.addView(col);
        parent.removeAllViews();
        parent.addView(root);

        host.setStatusTitle("Stem Player");
        try {
            host.stopCompetingAudio();
        } catch (Exception ignored) {
            host.pauseMainMusic();
        }
        refreshFace();
        startJob();
        return root;
    }

    public void detach() {
        cancelled.set(true);
        jobGen.incrementAndGet();
        boolean wasActive = sessionActive;
        sessionActive = false;
        if (wasActive) {
            try {
                host.onStemSessionVolumeExit();
            } catch (Exception ignored) {}
        }
        main.removeCallbacks(exitHoldRunnable);
        main.removeCallbacks(centerHoldRunnable);
        main.removeCallbacks(stemStutterHoldRunnable);
        main.removeCallbacks(mashDriftRunnable);
        resetCenterHold();
        stopStemStutter();
        releaseMixers();
        ready = false;
        armed = false;
        wheelLoopMode = false;
        clearZoneLoopCtrl();
        if (root != null) {
            ViewGroup p = (ViewGroup) root.getParent();
            if (p != null) p.removeView(root);
            root = null;
        }
        face = null;
    }

    /**
     * Mixer for the song currently steered by a pad (wheel / loop / stutter).
     * Layman: which always-on song deck this pad’s knobs turn.
     * 2026-07-19
     */
    private StemMixer mixerAt(int songIndex) {
        if (mixers == null || songIndex < 0 || songIndex >= mixers.length) return null;
        return mixers[songIndex];
    }

    /** Mixer for the focused pad’s control song (song 0 if no focus). 2026-07-19 */
    private StemMixer activeMixer() {
        if (activeZone < 0) return mixerAt(0);
        return mixerAt(session.songIndexForZone(activeZone));
    }

    private boolean hasMixers() {
        return mixers != null && mixers.length > 0 && mixers[0] != null;
    }

    /** Release every song mixer — stop mash drift first. 2026-07-19 */
    private void releaseMixers() {
        main.removeCallbacks(mashDriftRunnable);
        if (mixers == null) return;
        for (int i = 0; i < mixers.length; i++) {
            StemMixer m = mixers[i];
            mixers[i] = null;
            if (m == null) continue;
            try { m.release(); } catch (Exception ignored) {}
        }
        mixers = null;
    }

    private void stopStemStutter() {
        main.removeCallbacks(stemStutterHoldRunnable);
        stemHoldZone = -1;
        stemStutterArmed = false;
        StemMixer m = activeMixer();
        if (m != null) m.stopStutter();
    }

    private void syncLoopCtrlToMixer() {
        StemMixer m = activeMixer();
        if (m != null) m.setLoopCtrlMask(zoneLoopCtrl);
    }

    public void shutdown() {
        detach();
        io.shutdownNow();
    }

    /**
     * Hardware keys while Stem Player is open (DOWN + UP for holds).
     * @return true if consumed
     */
    public boolean onKey(int keyCode, KeyEvent event) {
        if (event == null) return false;
        int action = event.getAction();
        // #region agent log
        if (action == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("ready", ready);
                d.put("loading", loading);
                d.put("hasMixer", hasMixers());
                d.put("activeZone", activeZone);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.onKey", "key UP while stem open", "A", d);
            } catch (Exception ignored) {}
        }
        // #endregion

        // Hold PREV / NEXT → exit only when BOTH held; solo hold = stutter; short = focus/cycle.
        // Was: postDelayed exit on every solo side-key (old runnable exited if either down).
        // Reversal: schedule exit whenever either key is held alone.
        // 2026-07-19
        if (isPrevKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                prevDown = true;
                exitHoldFired = false;
                main.removeCallbacks(exitHoldRunnable);
                if (prevDown && nextDown) {
                    // Second side key — cancel stutter, arm dual-hold exit. 2026-07-19
                    stopStemStutter();
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginStemHold(1);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                prevDown = false;
                main.removeCallbacks(exitHoldRunnable);
                // If the other side is still down, do not treat this UP as a tap. 2026-07-19
                boolean stuttered = endStemHold(1);
                if (!exitHoldFired && !stuttered && !nextDown) {
                    onStemKey(1);
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
                    stopStemStutter();
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginStemHold(2);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                nextDown = false;
                main.removeCallbacks(exitHoldRunnable);
                boolean stuttered = endStemHold(2);
                if (!exitHoldFired && !stuttered && !prevDown) {
                    onStemKey(2);
                }
                exitHoldFired = false;
                return true;
            }
            return true;
        }

        // Center — short = start/join/stop loop; hold on looping stem = temp volume on wheel.
        // Melody is PLAY (bottom). Was: UP always onCenterTap. Reversal: ignore hold for tap.
        if (isStemCenterKey(keyCode)) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("action", action);
                d.put("role", "center_dial");
                d.put("wheelLoopMode", wheelLoopMode);
                d.put("holdVol", centerHoldVolume);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.onKey:center",
                        "stem center dial",
                        "STEM_CTR",
                        d);
            } catch (Exception ignored) {}
            // #endregion
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                centerDown = true;
                centerHoldVolume = false;
                centerHoldStemChanged = false;
                centerHoldZone = activeZone;
                main.removeCallbacks(centerHoldRunnable);
                main.postDelayed(centerHoldRunnable, CENTER_HOLD_MS);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                main.removeCallbacks(centerHoldRunnable);
                boolean wasHoldVol = centerHoldVolume;
                boolean stemChanged = centerHoldStemChanged;
                int holdZone = centerHoldZone;
                resetCenterHold();
                if (wasHoldVol) {
                    // Release after volume peek — restore loop wheel if still on same stem.
                    StemMixer am = activeMixer();
                    if (!stemChanged && holdZone == activeZone
                            && am != null && am.isLooping()
                            && activeZone >= 0 && activeZone < zoneLoopCtrl.length
                            && zoneLoopCtrl[activeZone]) {
                        wheelLoopMode = true;
                    }
                    refreshFace();
                    updateStatusLine();
                    return true;
                }
                if (ready && hasMixers()) {
                    onCenterTap();
                }
                return true;
            }
            return true;
        }

        // BACK (top) = Vocals — hold = stutter; short = focus/cycle. 2026-07-19
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                beginStemHold(0);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                boolean stuttered = endStemHold(0);
                if (!stuttered) onStemKey(0);
                return true;
            }
            return true;
        }

        // PLAY (bottom) = Melody — hold stutter; Center is dial-only above. 2026-07-19
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == 85
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                beginStemHold(3);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                boolean stuttered = endStemHold(3);
                if (!stuttered) onStemKey(3);
                return true;
            }
            return true;
        }

        return false;
    }

    /**
     * Arm hold timer for hip-hop stutter on one stem (one at a time).
     * Layman: keep the pad down and it chatters; tap is focus only.
     * Technical: do NOT selectZone/setActiveZone on DOWN — that armed cycle on UP.
     * Was: selectZone(zone) when activeZone!=zone. Reversal: preview-only face tint.
     * 2026-07-19
     */
    private void beginStemHold(int zone) {
        if (prevDown && nextDown) {
            // Dual side-key = exit — do not stutter. 2026-07-19
            main.removeCallbacks(stemStutterHoldRunnable);
            return;
        }
        stopStemStutter();
        stemHoldZone = zone;
        stemStutterArmed = false;
        // Preview which pad is pressed — session focus waits for UP / stutter arm. 2026-07-19
        if (face != null && zone >= 0) face.setActiveZone(zone);
        main.removeCallbacks(stemStutterHoldRunnable);
        main.postDelayed(stemStutterHoldRunnable, StemControls.STEM_STUTTER_HOLD_MS);
    }

    /**
     * End stem hold. @return true if stutter ran (skip focus/cycle tap).
     * 2026-07-19
     */
    private boolean endStemHold(int zone) {
        main.removeCallbacks(stemStutterHoldRunnable);
        boolean was = stemStutterArmed && stemHoldZone == zone;
        StemMixer m = activeMixer();
        if (m != null) m.stopStutter();
        stemHoldZone = -1;
        stemStutterArmed = false;
        refreshFace();
        updateStatusLine();
        return was;
    }

    /**
     * Short stem key: focus pad, or cycle that arm’s control song when multi (2–3 tracks).
     * songCount==1: focus only — classic single-track UX.
     * Cycle = routing only — never seek/swap/restart always-on song mixers.
     * Was: replaceZoneStem on cycle (playhead jump). Reversal: call swapPadStem again.
     * 2026-07-19
     */
    private void onStemKey(int zone) {
        // Decide cycle from session focus BEFORE any host write. 2026-07-19
        boolean willCycle = StemControls.stemKeyShouldCycleSong(
                session.activeZone(), zone, session.songCount());
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("zone", zone);
            d.put("ready", ready);
            d.put("loading", loading);
            d.put("prevActive", activeZone);
            d.put("sessionActive", session.activeZone());
            d.put("willCycle", willCycle);
            d.put("multi", session.isMulti());
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.onStemKey", "focus/cycle", "D", d);
        } catch (Exception ignored) {}
        // #endregion
        boolean cycled = session.onStemKey(zone);
        activeZone = session.activeZone();
        armed = true;
        if (cycled && session.isMulti()) {
            // Control routing only — timelines keep running. 2026-07-19
            host.toast("Song " + session.displaySongNumber(zone));
        }
        syncHostFromActiveSong();
        selectZone(activeZone);
        updateInteractedTrackTitle();
    }

    /**
     * Pull loop-ctrl + wheel mode from the song under the focused arm.
     * Layman: each song keeps its own mute/loop slate when you switch control.
     * Technical: host zoneLoopCtrl ← SongState; apply mask to that song’s mixer only.
     * Was: single mixer setLoopCtrlMask. Reversal: mixer.setLoopCtrlMask on one instance.
     * 2026-07-19
     */
    private void syncHostFromActiveSong() {
        StemSession.SongState st = session.activeSongState();
        if (st == null) return;
        for (int i = 0; i < zoneLoopCtrl.length; i++) {
            zoneLoopCtrl[i] = st.zoneLoopCtrl[i];
        }
        wheelLoopMode = st.looping
                && activeZone >= 0
                && activeZone < zoneLoopCtrl.length
                && zoneLoopCtrl[activeZone];
        if (st.loopBars > 0f) savedLoopBars = st.loopBars;
        StemMixer m = activeMixer();
        if (m == null) return;
        m.setLoopCtrlMask(zoneLoopCtrl);
    }

    /**
     * Push every song’s stem gains onto its always-on mixer (all decks audible).
     * Layman: each track’s volumes stay where you left them while others play.
     * Technical: for each song i, mixers[i].setGain(z, songs[i].gains[z]).
     * Was: single mixer gains from songIndexForZone only (muted other songs). Reversal: that loop.
     * 2026-07-19
     */
    private void syncAllSongGainsToMixers() {
        if (mixers == null) return;
        for (int s = 0; s < mixers.length; s++) {
            StemMixer m = mixers[s];
            StemSession.SongState st = session.song(s);
            if (m == null || st == null) continue;
            for (int z = 0; z < StemMixer.STEM_COUNT; z++) {
                m.setGain(z, st.gains[z]);
            }
        }
    }

    /**
     * Wheel — volume or loop bars (hold-Center forces volume peek on a looping stem).
     * CW = louder / fewer bars; CCW = quieter / more bars.
     * 2026-07-19
     */
    public boolean onWheel(int steps) {
        StemMixer mixer = activeMixer();
        if (!ready || mixer == null || steps == 0) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("ready", ready);
                d.put("hasMixer", mixer != null);
                d.put("steps", steps);
                d.put("loading", loading);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.onWheel", "wheel blocked", "A", d);
            } catch (Exception ignored) {}
            // #endregion
            return true;
        }
        // While holding stutter — wheel sizes the chop slice (no full restart). 2026-07-19
        if (stemStutterArmed && mixer.isStuttering()) {
            stutterChopStep = StemBpm.nudgeChopStep(
                    stutterChopStep, StemControls.loopStepsFromWheel(steps));
            int slice = StemBpm.chopSliceMs(mixer.getBpm(), stutterChopStep);
            if (slice <= 0) slice = StemMixer.DEFAULT_STUTTER_MS;
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("slice", slice);
                d.put("step", stutterChopStep);
                d.put("zone", mixer.getStutterZone());
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.onWheel", "chop resize", "F1", d);
            } catch (Exception ignored) {}
            // #endregion
            mixer.setStutterSliceMs(slice);
            refreshFace();
            updateStatusLine();
            return true;
        }
        boolean useVolume = StemControls.wheelUsesVolume(wheelLoopMode, centerHoldVolume);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("raw", steps);
            d.put("wheelLoopMode", wheelLoopMode);
            d.put("holdVol", centerHoldVolume);
            d.put("useVolume", useVolume);
            d.put("activeZone", activeZone);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.onWheel",
                    "stem wheel polarity",
                    "STEM_WHL",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        if (activeZone < 0 || activeZone >= StemMixer.STEM_COUNT) {
            // No pad focused yet — ignore wheel until a stem key focuses. 2026-07-19
            return true;
        }
        if (!useVolume) {
            float bars = StemControls.nudgeLoopBars(
                    mixer.getLoopBars(), StemControls.loopStepsFromWheel(steps));
            savedLoopBars = bars;
            if (!mixer.isLooping()) {
                mixer.startLoop(bars);
            } else {
                mixer.setLoopBars(bars);
            }
            StemSession.SongState lst = session.activeSongState();
            if (lst != null) lst.loopBars = bars;
            syncLoopCtrlToMixer();
            refreshFace();
            updateStatusLine();
            return true;
        }
        // Gain only on the focused pad’s control song — other decks keep their levels. 2026-07-19
        mixer.nudgeGainSteps(activeZone, StemControls.volumeStepsFromWheel(steps));
        StemSession.SongState st = session.activeSongState();
        if (st != null) st.gains[activeZone] = mixer.getGain(activeZone);
        refreshFace();
        updateStatusLine();
        return true;
    }

    /** Leave stem player — cancel job, pause every song mixer. */
    public void requestExit() {
        cancelled.set(true);
        jobGen.incrementAndGet();
        stopStemStutter();
        main.removeCallbacks(mashDriftRunnable);
        if (mixers != null) {
            for (int i = 0; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                if (m == null) continue;
                try { m.pause(); } catch (Exception ignored) {}
            }
        }
        host.onExitStemPlayer();
    }

    /**
     * Center/Enter — start/join/stop loop; if song finished, Center replays from the top.
     * 2026-07-19
     */
    private void onCenterTap() {
        StemMixer mixer = activeMixer();
        // After natural end — Center replays once (user-driven, not auto).
        if (songFinished) {
            songFinished = false;
            if (mixers != null) {
                for (int i = 0; i < mixers.length; i++) {
                    StemMixer m = mixers[i];
                    if (m != null) {
                        try { m.play(); } catch (Exception ignored) {}
                    }
                }
            }
            updateStatusLine();
            return;
        }
        boolean audioLoop = mixer != null && mixer.isLooping();
        boolean inCtrl = activeZone >= 0 && activeZone < zoneLoopCtrl.length
                && zoneLoopCtrl[activeZone];
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("audioLoop", audioLoop);
            d.put("zone", activeZone);
            d.put("inCtrl", inCtrl);
            d.put("stop", StemControls.centerShouldStopLoop(audioLoop, inCtrl));
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.onCenterTap",
                    "center loop join/stop",
                    "H-LOOP",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        if (StemControls.centerShouldStopLoop(audioLoop, inCtrl)) {
            stopLoopToVolumeMode();
            return;
        }
        dialInToLoopMode();
    }

    /** Enter loop-bars wheel mode; mark this stem; engage A–B if needed. 2026-07-19 */
    private void dialInToLoopMode() {
        StemMixer mixer = activeMixer();
        if (mixer == null || !ready) return;
        markZoneLoopCtrl(activeZone, true);
        StemSession.SongState st = session.activeSongState();
        if (st != null && activeZone >= 0 && activeZone < st.zoneLoopCtrl.length) {
            st.zoneLoopCtrl[activeZone] = true;
            st.looping = true;
        }
        wheelLoopMode = true;
        // Prefer this song’s loop bars — do not inherit another track’s length. 2026-07-19
        float bars = (st != null && st.loopBars > 0f) ? st.loopBars
                : (savedLoopBars > 0f ? savedLoopBars : StemControls.DEFAULT_LOOP_BARS);
        if (!mixer.isLooping()) {
            mixer.startLoop(bars);
        } else {
            mixer.setLoopBars(bars);
        }
        syncLoopCtrlToMixer();
        savedLoopBars = mixer.getLoopBars();
        if (st != null) st.loopBars = savedLoopBars;
        refreshFace();
        updateStatusLine();
    }

    /**
     * Stop A–B loop, clear every stem’s loop-ctrl, wheel = volume.
     * 2026-07-19
     */
    private void stopLoopToVolumeMode() {
        wheelLoopMode = false;
        clearZoneLoopCtrl();
        StemSession.SongState st = session.activeSongState();
        if (st != null) {
            for (int i = 0; i < st.zoneLoopCtrl.length; i++) st.zoneLoopCtrl[i] = false;
            st.looping = false;
        }
        StemMixer mixer = activeMixer();
        if (mixer != null) {
            if (mixer.isLooping()) {
                savedLoopBars = mixer.getLoopBars();
            }
            mixer.clearLoop();
        }
        refreshFace();
        updateStatusLine();
    }

    /**
     * Focus a stem — restore that stem’s last mode: loop wheel only if it joined loop-ctrl
     * and audio is still looping; otherwise volume (fast path).
     * Was: any select while looping forced loop wheel. Reversal: selectZone always wheelLoopMode if looping.
     * 2026-07-19
     */
    private void selectZone(int zone) {
        if (zone < 0 || zone >= StemMixer.STEM_COUNT) return;
        if (centerDown && zone != activeZone) {
            // Focus changed while Center held — release must not snap back to loop wheel.
            centerHoldStemChanged = true;
        }
        activeZone = zone;
        session.setActiveZone(zone);
        armed = true;
        StemMixer mixer = activeMixer();
        boolean audioLoop = mixer != null && mixer.isLooping();
        boolean inCtrl = zoneLoopCtrl[zone];
        wheelLoopMode = StemControls.wheelLoopModeForStem(audioLoop, inCtrl);
        // Hold-volume peek only applies to the stem we started holding on.
        if (centerHoldVolume && zone != centerHoldZone) {
            centerHoldVolume = false;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("zone", zone);
            d.put("audioLoop", audioLoop);
            d.put("inCtrl", inCtrl);
            d.put("wheelLoopMode", wheelLoopMode);
            d.put("holdStemChanged", centerHoldStemChanged);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.selectZone",
                    "per-stem mode restore",
                    "H-LOOP",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        if (face != null) face.setActiveZone(zone);
        refreshFace();
        updateStatusLine();
    }

    /** Clear Center hold-peek state (detach / UP / attach). 2026-07-19 */
    private void resetCenterHold() {
        centerDown = false;
        centerHoldVolume = false;
        centerHoldZone = -1;
        centerHoldStemChanged = false;
        main.removeCallbacks(centerHoldRunnable);
    }

    /** Clear per-stem loop-control membership. 2026-07-19 */
    private void clearZoneLoopCtrl() {
        for (int i = 0; i < zoneLoopCtrl.length; i++) zoneLoopCtrl[i] = false;
    }

    /** Mark one stem as (not) in the loop-control set. 2026-07-19 */
    private void markZoneLoopCtrl(int zone, boolean on) {
        if (zone < 0 || zone >= zoneLoopCtrl.length) return;
        zoneLoopCtrl[zone] = on;
    }

    /**
     * Prepare 1–3 songs (cache / user stems / Lalal) then open mixers.
     * Layman: pull the stem files for each track, then jam.
     * 2026-07-19
     */
    private void startJob() {
        final int gen = jobGen.incrementAndGet();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("gen", gen);
            d.put("trackCount", tracks.size());
            d.put("multi", tracks.size() > 1);
            for (int ti = 0; ti < tracks.size() && ti < 3; ti++) {
                File tf = tracks.get(ti);
                d.put("t" + ti, tf != null ? tf.getName() : "null");
            }
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.startJob", "startJob enter", "B", d);
        } catch (Exception ignored) {}
        // #endregion
        if (tracks.isEmpty()) {
            statusView.setText("No local track file");
            loading = false;
            refreshFace();
            return;
        }
        final boolean premix = LalalAccount.isPremixExperimental(host.prefs());
        final String key = LalalAccount.effectiveKey(host.prefs());
        final android.content.Context ctx = host.appContext();
        final java.util.ArrayList<File> jobTracks = new java.util.ArrayList<File>(tracks);

        // Count who still needs Lalal — cached live/premix (any probe path) stay local. 2026-07-19
        // Was: all-or-nothing allLocal; mixed batches still said "Uploading" for everyone.
        int needLal = 0;
        for (int i = 0; i < jobTracks.size(); i++) {
            File tf = jobTracks.get(i);
            boolean ready = LalalClient.trackStemsReady(ctx, tf, premix, host.appCacheDir());
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("i", i);
                d.put("name", tf != null ? tf.getName() : "");
                d.put("cacheHit", ready);
                d.put("needLalal", !ready);
                File hit = ready
                        ? LalalClient.findReadyStemDir(ctx, tf, premix, host.appCacheDir())
                        : null;
                d.put("dir", hit != null ? hit.getName() : "");
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.startJob",
                        ready ? "cache-hit skip Lalal" : "need Lalal separate",
                        "LOCAL",
                        d);
            } catch (Exception ignored) {}
            // #endregion
            if (!ready) needLal++;
        }
        final boolean allLocal = needLal == 0;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("allLocal", allLocal);
            d.put("needLal", needLal);
            d.put("trackCount", jobTracks.size());
            d.put("premix", premix);
            d.put("keyLen", key != null ? key.length() : 0);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.startJob", "local stem check", "B", d);
        } catch (Exception ignored) {}
        // #endregion
        if (allLocal) {
            statusView.setText(jobTracks.size() > 1 ? "Loading mashup stems…" : "Loading stems…");
        } else if (key.length() < 8) {
            statusView.setText("Lalal key missing — add stems folders or key");
            loading = false;
            refreshFace();
            return;
        } else if (needLal < jobTracks.size()) {
            // Partial cache hit — only missing tracks upload. 2026-07-19
            statusView.setText("Loading local + separating " + needLal + "…");
        } else {
            statusView.setText(jobTracks.size() > 1
                    ? "Preparing mashup (Lalal)…"
                    : (LalalAccount.isUserConfigured(host.prefs())
                            ? "Uploading to Lalal.ai…"
                            : "Demo key · Uploading to Lalal.ai…"));
        }

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (com.solar.launcher.SolarAutoTime.isWallClockImplausible()) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != jobGen.get()) return;
                                statusView.setText("Fixing clock (needed for secure download)…");
                            }
                        });
                        com.solar.launcher.SolarAutoTime.requestSyncNow(ctx);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {}
                        if (com.solar.launcher.SolarAutoTime.isWallClockImplausible()) {
                            throw new IOException(
                                    "Clock is wrong (shows 1970). Settings → Date & Time → Sync now, then retry.");
                        }
                    }

                    for (int i = 0; i < jobTracks.size(); i++) {
                        if (gen != jobGen.get() || cancelled.get()) {
                            // #region agent log
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("gen", gen);
                                d.put("jobGen", jobGen.get());
                                d.put("cancelled", cancelled.get());
                                d.put("atIndex", i);
                                com.solar.launcher.Debug543e15Log.log(
                                        "StemPlayerHost.startJob", "job cancelled mid-resolve", "E", d);
                            } catch (Exception ignored) {}
                            // #endregion
                            return;
                        }
                        final int songIndex = i;
                        final File src = jobTracks.get(i);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != jobGen.get()) return;
                                statusView.setText("Song " + (songIndex + 1) + "/"
                                        + jobTracks.size() + "…");
                            }
                        });
                        List<LalalClient.StemFile> stems = resolveStemsForTrack(
                                src, premix, key, ctx, gen);
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("songIndex", songIndex);
                            d.put("name", src != null ? src.getName() : "");
                            d.put("stemCount", stems != null ? stems.size() : -1);
                            boolean[] zh = new boolean[4];
                            if (stems != null) {
                                for (int si = 0; si < stems.size(); si++) {
                                    LalalClient.StemFile sf = stems.get(si);
                                    if (sf != null && sf.zone >= 0 && sf.zone < 4) zh[sf.zone] = true;
                                }
                            }
                            d.put("z0", zh[0]);
                            d.put("z1", zh[1]);
                            d.put("z2", zh[2]);
                            d.put("z3", zh[3]);
                            com.solar.launcher.Debug543e15Log.log(
                                    "StemPlayerHost.startJob", "resolved stems", "B", d);
                        } catch (Exception ignored) {}
                        // #endregion
                        if (stems == null || stems.isEmpty()) {
                            throw new IOException("No stems for " + src.getName());
                        }
                        // Skip StemBassBody.ensure on open — keeps Y1 off the premix stall. 2026-07-19
                        // Was: StemBassBody.ensure on critical path. Reversal: call ensure here.
                        StemSession.SongState st = session.song(songIndex);
                        if (st != null) {
                            st.stems = stems;
                            st.bassBody = null;
                        }
                    }
                    if (gen != jobGen.get() || cancelled.get()) return;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get() || cancelled.get()) return;
                            beginMixers();
                        }
                    });
                } catch (final Exception e) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("err", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                        com.solar.launcher.Debug543e15Log.log(
                                "StemPlayerHost.startJob", "resolve failed", "C", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get()) return;
                            loading = false;
                            refreshFace();
                            if (cancelled.get()) {
                                statusView.setText("Cancelled");
                                return;
                            }
                            String msg = e.getMessage() != null ? e.getMessage() : "Stem failed";
                            if (msg.indexOf("Could not validate certificate") >= 0
                                    || msg.indexOf("CertificateNotYetValid") >= 0
                                    || msg.indexOf("not valid until") >= 0) {
                                msg = "Clock is wrong — Settings → Date & Time → Sync now, then retry Stem Player.";
                            }
                            statusView.setText(msg);
                            host.toast(msg);
                        }
                    });
                }
            }
        });
    }

    /**
     * Resolve stem MP3s for one track (user / any cache home → Lalal only if missing).
     * Prefer requested premix mode; fall back to the other mode’s ready folder.
     * Was: only durableStemDir+legacy for one premix flag (missed overflow / other mode).
     * 2026-07-19
     */
    private List<LalalClient.StemFile> resolveStemsForTrack(File src, boolean premix,
            String key, android.content.Context ctx, int gen) throws Exception {
        File readyDir = LalalClient.findReadyStemDir(ctx, src, premix, host.appCacheDir());
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("name", src != null ? src.getName() : "");
            d.put("premix", premix);
            d.put("cacheHit", readyDir != null);
            d.put("readyDir", readyDir != null ? readyDir.getAbsolutePath() : "");
            d.put("stableKey", src != null ? LalalClient.cacheKeyStable(src) : "");
            d.put("pathKey", src != null ? LalalClient.cacheKeyFor(src) : "");
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.resolveStemsForTrack",
                    readyDir != null ? "cache-hit load local" : "cache-miss will separateToMp3",
                    "LOCAL",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        if (readyDir != null) {
            if (LalalClient.userStemsReady(src)
                    && readyDir.equals(LalalClient.userStemsDir(src))) {
                return LalalClient.loadUserStems(src, premix);
            }
            List<LalalClient.StemFile> cached = LalalClient.loadCached(readyDir, premix);
            if (cached != null && !cached.isEmpty()) return cached;
            cached = LalalClient.loadStemDirFlexible(readyDir);
            if (cached != null && !cached.isEmpty()) return cached;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("name", src != null ? src.getName() : "");
            d.put("callingSeparate", true);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.resolveStemsForTrack", "separateToMp3 begin", "LOCAL", d);
        } catch (Exception ignored) {}
        // #endregion
        if (key == null || key.length() < 8) {
            throw new IOException("Lalal key missing for " + src.getName());
        }
        File durable = LalalClient.durableStemDir(ctx, src, premix);
        File work = LalalClient.workStemDir(ctx, src, premix);
        LalalClient client = new LalalClient(key);
        client.setCancelled(cancelled);
        return client.separateToMp3(src, work, durable, premix, new LalalClient.Progress() {
            @Override
            public void onProgress(final String phase, final int percent, final String detail) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != jobGen.get() || cancelled.get()) return;
                        statusView.setText(phaseLabel(phase, percent, detail));
                        loading = !"ready".equals(phase);
                        refreshFace();
                    }
                });
            }
        });
    }

    /**
     * @deprecated Prefer findReadyStemDir + loadCached. Kept unused — loadCachedIfReady removed.
     * 2026-07-19
     */
    @SuppressWarnings("unused")
    private static List<LalalClient.StemFile> loadCachedIfReady(File dir, boolean premix) {
        if (dir == null) return null;
        if (!LalalClient.cacheReady(dir) && !LalalClient.cacheReadyFlexible(dir)) return null;
        List<LalalClient.StemFile> loaded = LalalClient.loadCached(dir, premix);
        if (loaded != null && !loaded.isEmpty()) return loaded;
        loaded = LalalClient.loadStemDirFlexible(dir);
        return (loaded != null && !loaded.isEmpty()) ? loaded : null;
    }

    /**
     * Open one StemMixer per song — all timelines start together at gain 0.
     * Cycle/focus only steers which song the wheel writes; never replaceZoneStem.
     * Rate 1.0 for now (SoundTouch multi-song deferred). Was: single mixer + swap.
     * Reversal: load song0 only + replaceZoneStem on cycle.
     * 2026-07-19
     */
    private void beginMixers() {
        releaseMixers();
        final int n = session.songCount();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("songCount", n);
            d.put("rootNull", root == null);
            StemSession.SongState s0 = session.song(0);
            d.put("song0stems", s0 != null && s0.stems != null ? s0.stems.size() : -1);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.beginMixers", "beginMixers enter", "C", d);
        } catch (Exception ignored) {}
        // #endregion
        if (n < 1 || root == null) {
            statusView.setText("No songs ready");
            loading = false;
            refreshFace();
            return;
        }
        for (int i = 0; i < n; i++) {
            StemSession.SongState st = session.song(i);
            if (st == null || st.stems == null || st.stems.isEmpty()) {
                statusView.setText("Missing stems (song " + (i + 1) + ")");
                loading = false;
                refreshFace();
                return;
            }
        }
        StemSession.SongState song0 = session.song(0);
        int dur = song0.track != null ? probeDurationMs(song0.track) : 180_000;
        float bpm = StemBpm.estimateFromDurationMs(dur);
        for (int i = 0; i < n; i++) {
            StemSession.SongState st = session.song(i);
            if (st != null) {
                st.bpm = bpm;
                st.screwRate = 1f; // tempo match deferred — playable first. 2026-07-19
            }
        }
        mixers = new StemMixer[n];
        final AtomicInteger readyLeft = new AtomicInteger(n);
        final AtomicBoolean loadFailed = new AtomicBoolean(false);
        try {
            for (int i = 0; i < n; i++) {
                final int songIndex = i;
                final StemSession.SongState st = session.song(i);
                final StemMixer m = new StemMixer(root.getContext());
                mixers[i] = m;
                m.setBpm(bpm);
                m.setListener(new StemMixer.Listener() {
                    @Override
                    public void onReady() {
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("songIndex", songIndex);
                            d.put("bpm", m.getBpm());
                            d.put("left", readyLeft.get());
                            com.solar.launcher.Debug543e15Log.log(
                                    "StemPlayerHost.beginMixers", "mixer onReady", "A", d);
                        } catch (Exception ignored) {}
                        // #endregion
                        if (loadFailed.get()) return;
                        if (readyLeft.decrementAndGet() == 0) {
                            onAllMixersReady();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!loadFailed.compareAndSet(false, true)) return;
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("songIndex", songIndex);
                            d.put("msg", message != null ? message : "");
                            com.solar.launcher.Debug543e15Log.log(
                                    "StemPlayerHost.beginMixers", "mixer onError", "C", d);
                        } catch (Exception ignored) {}
                        // #endregion
                        loading = false;
                        ready = false;
                        statusView.setText(message != null ? message : "Play error");
                        statusView.setTextColor(0xFFB0B0B8);
                        refreshFace();
                    }

                    @Override
                    public void onComplete() {
                        // Song 1 ending marks the jam finished (Center replays all). 2026-07-19
                        if (songIndex != 0) return;
                        songFinished = true;
                        ready = true;
                        loading = false;
                        wheelLoopMode = false;
                        clearZoneLoopCtrl();
                        statusView.setText("Finished — Center to play again");
                        statusView.setTextColor(0xFFB0B0B8);
                        refreshFace();
                    }
                });
                // bassBody=null — deferred off critical path. 2026-07-19
                m.load(st.stems, null);
            }
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("loadCalled", true);
                d.put("mixerCount", n);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.beginMixers", "load() issued", "A", d);
            } catch (Exception ignored) {}
            // #endregion
        } catch (Exception e) {
            loadFailed.set(true);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("err", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.beginMixers", "load threw", "C", d);
            } catch (Exception ignored) {}
            // #endregion
            loading = false;
            statusView.setText(e.getMessage() != null ? e.getMessage() : "Mixer failed");
            refreshFace();
        }
        statusView.setText(n > 1 ? "Buffering " + n + " songs…" : "Buffering stems…");
    }

    /** Best-effort duration for BPM guess (MediaMetadataRetriever). 2026-07-19 */
    private static int probeDurationMs(File track) {
        if (track == null || !track.isFile()) return 180_000;
        android.media.MediaMetadataRetriever mmr = null;
        try {
            mmr = new android.media.MediaMetadataRetriever();
            mmr.setDataSource(track.getAbsolutePath());
            String d = mmr.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                int ms = Integer.parseInt(d);
                if (ms > 1000) return ms;
            }
        } catch (Exception ignored) {
        } finally {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (Exception ignored) {}
            }
        }
        return 180_000;
    }

    /**
     * All song mixers prepared — start every timeline at gains 0 (wheel raises volume).
     * Layman: everything is playing quietly; turn pads up to hear the mix.
     * Was: one mixer play(). Reversal: mixer.play() only for song0.
     * 2026-07-19
     */
    private void onAllMixersReady() {
        ready = true;
        loading = false;
        songFinished = false;
        syncAllSongGainsToMixers();
        if (mixers != null) {
            for (int i = 0; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                if (m == null) continue;
                try { m.play(); } catch (Exception ignored) {}
            }
            StemMixer lead = mixers[0];
            StemSession.SongState st = session.song(0);
            if (st != null && lead != null) st.bpm = lead.getBpm();
        }
        main.removeCallbacks(mashDriftRunnable);
        if (mixers != null && mixers.length > 1) {
            main.postDelayed(mashDriftRunnable, 1000);
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("ready", ready);
            d.put("multi", session.isMulti());
            d.put("mixerCount", mixers != null ? mixers.length : 0);
            d.put("playing", true);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.onAllMixersReady", "all timelines started gains0", "A", d);
        } catch (Exception ignored) {}
        // #endregion
        refreshFace();
        statusView.setText(session.isMulti()
                ? "Ready — pads layer songs · wheel raises volume"
                : "Ready — tap a stem · wheel raises volume");
        statusView.setTextColor(0xFFB0B0B8);
        updateInteractedTrackTitle();
        host.setStatusTitle(session.isMulti() ? "Stem Mashup" : "Stem Player");
        // Brief Ready line, then live pad status. 2026-07-19
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ready && !loading) updateStatusLine();
            }
        }, 1200);
    }

    private void refreshFace() {
        if (face == null) return;
        float[] g = new float[4];
        for (int i = 0; i < 4; i++) {
            // Face beads = gain for the song this pad currently steers. 2026-07-19
            StemMixer m = mixerAt(session.songIndexForZone(i));
            if (m != null) {
                g[i] = m.getGain(i);
            } else {
                StemSession.SongState st = session.song(session.songIndexForZone(i));
                g[i] = st != null ? st.gains[i] : 0f;
            }
        }
        StemMixer faceMixer = activeMixer();
        float bars = faceMixer != null ? faceMixer.getLoopBars() : savedLoopBars;
        boolean stutter = faceMixer != null && faceMixer.isStuttering();
        int[] songs = new int[4];
        // Song digits only for mashup (2–3); single-track face stays clean. 2026-07-19
        for (int z = 0; z < 4; z++) {
            songs[z] = session.isMulti() ? session.displaySongNumber(z) : 0;
        }
        // Volume beads follow gain whenever wheel is in volume mode — even if a loop runs.
        // Was: audioLoop||wheelLoopMode hid gain LEDs on the focused arm. Reversal: that OR.
        // 2026-07-19
        boolean showLoopBars = StemControls.faceShowsLoopBars(wheelLoopMode, centerHoldVolume);
        face.setState(g, activeZone, armed, showLoopBars, bars, loading,
                songs, stutter || stemStutterArmed);
    }

    /**
     * Title + status chrome for the song on the focused pad.
     * Layman: show which track you are mixing on this pad.
     * 2026-07-19
     */
    private void updateInteractedTrackTitle() {
        if (titleView == null) return;
        String songName = "";
        if (activeZone >= 0) {
            songName = session.trackDisplayNameForZone(activeZone);
        }
        if (songName.length() == 0 && track != null) {
            songName = stripTrackExt(track.getName());
        }
        if (songName.length() == 0) {
            songName = session.isMulti() ? "Stem Mashup" : "Stem Player";
        }
        titleView.setText(songName);
        titleView.setSelected(true);
        try {
            host.setStatusTitle(songName);
        } catch (Exception ignored) {}
    }

    private void updateStatusLine() {
        if (statusView == null || !ready) return;
        StemMixer m = activeMixer();
        String trackBit = "";
        if (activeZone >= 0) {
            String tn = session.trackDisplayNameForZone(activeZone);
            if (tn.length() > 0) trackBit = tn + " · ";
        }
        if (stemStutterArmed && m != null && m.isStuttering()) {
            statusView.setText(trackBit + "Stutter " + StemBpm.chopLabel(stutterChopStep)
                    + " · wheel = slice · release = continue");
            statusView.setTextColor(zoneColor(activeZone));
            return;
        }
        if (centerHoldVolume && m != null) {
            String name = LalalClient.labelForZone(activeZone);
            int pct = Math.round(m.getGain(activeZone) * 100f);
            statusView.setText(trackBit + name + "  " + pct + "% · hold Center = volume · release = loop");
            statusView.setTextColor(zoneColor(activeZone));
            return;
        }
        if (wheelLoopMode && m != null) {
            statusView.setText(trackBit + "Loop " + formatBars(m.getLoopBars())
                    + " — CW shorter · hold Center = vol · tap Center = stop");
            statusView.setTextColor(0xFFFFFFFF);
            return;
        }
        if (activeZone < 0) {
            statusView.setText("Ready — tap a stem pad · wheel raises volume");
            statusView.setTextColor(0xFFB0B0B8);
            return;
        }
        String name = LalalClient.labelForZone(activeZone);
        int pct = m != null ? Math.round(m.getGain(activeZone) * 100f) : 0;
        String songBit = session.isMulti()
                ? (" · song " + session.displaySongNumber(activeZone)) : "";
        statusView.setText(trackBit + name + "  " + pct + "%" + songBit
                + " · hold = stutter · CW louder · Center = loop");
        statusView.setTextColor(zoneColor(activeZone));
    }

    /** Drop file extension for on-face track titles. 2026-07-19 */
    private static String stripTrackExt(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    private static boolean isPrevKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    private static boolean isNextKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /**
     * Center dial only — ENTER / DPAD_CENTER (not MEDIA_PLAY_PAUSE).
     * Play (85) is Melody (bottom). If Y1 wheel click only ever sends 85, STEM_KEY logs will show
     * and Center dial never fires — then we revisit the collision.
     * 2026-07-19
     */
    private static boolean isStemCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == 66
                || keyCode == 23;
    }

    private static int zoneColor(int z) {
        if (z < 0 || z >= StemFaceView.STEM_COLORS.length) return 0xFFB0B0B8;
        return StemFaceView.STEM_COLORS[z];
    }

    private static String formatBars(float bars) {
        if (bars == (int) bars) return String.valueOf((int) bars) + " bar";
        return bars + " bar";
    }

    /**
     * Status chip — phase + overall % + step detail (so mix is not frozen at 96%).
     * 2026-07-19
     */
    private static String phaseLabel(String phase, int percent, String detail) {
        String head;
        if ("upload".equals(phase)) head = "Uploading";
        else if ("split".equals(phase)) head = "Separating stems";
        else if ("download".equals(phase)) head = "Downloading stems";
        else if ("mix".equals(phase)) head = "Mixing Melody pad";
        else if ("publish".equals(phase)) head = "Saving stems";
        else if ("ready".equals(phase)) head = "Ready";
        else head = phase != null ? phase : "";
        StringBuilder sb = new StringBuilder();
        sb.append(head);
        if (!"ready".equals(phase)) {
            sb.append("… ").append(percent).append('%');
        }
        if (detail != null && detail.length() > 0 && !"ready".equals(phase)) {
            sb.append(" · ").append(detail);
        }
        return sb.toString();
    }

    private static TextView label(Context ctx, String text, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(false);
        return tv;
    }

    private static int dp(Context ctx, int v) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
