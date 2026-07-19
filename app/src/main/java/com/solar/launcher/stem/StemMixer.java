package com.solar.launcher.stem;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Synced MediaPlayers for Stem Player — pad-local loop + hold stutter + optional bass body.
 * Layman: only pads you put in the loop jump back; hold a stem key for a quick hip-hop stutter.
 * Technical: loopCtrl mask seeks joined zones only; holdStutter seeks one zone on a timer;
 * optional bass_body player shares zone-2 gain. Was: seekAll on loop. Reversal: that seekAll path.
 * 2026-07-19
 */
public final class StemMixer {
    public static final int STEM_COUNT = 4;
    private static final int DEFAULT_MS_PER_BAR = 2000;
    /** Default hip-hop stutter slice when hold-stem fires (~1/8 bar @ 120). */
    public static final int DEFAULT_STUTTER_MS = 250;
    /** Floor for chop seeks — sub-80ms seek storms crash OMX on Y1 (API 17). 2026-07-19 */
    public static final int MIN_STUTTER_MS = 80;

    public interface Listener {
        void onReady();
        void onError(String message);
        void onComplete();
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer[] players = new MediaPlayer[0];
    private int[] playerZones = new int[0];
    private int playerCount;
    private MediaPlayer bassBodyPlayer;
    private final float[] gains = new float[] { 0f, 0f, 0f, 0f };
    private final boolean[] loopCtrl = new boolean[STEM_COUNT];
    private Listener listener;
    private int preparedCount;
    private int expectedPrepare;
    private boolean started;
    private boolean released;
    private boolean looping;
    private int loopStartMs;
    private int loopEndMs;
    private float loopBars = StemControls.DEFAULT_LOOP_BARS;
    private int msPerBar = DEFAULT_MS_PER_BAR;
    private float bpm = StemBpm.DEFAULT_BPM;
    /** Cross-song tempo rate (Song1=1). Applied via IJK SoundTouch when ≠1. 2026-07-19 */
    private float targetRate = 1f;
    /** Paths for IJK reload when rate needs SoundTouch. */
    private String[] playerPaths = new String[0];
    private String bassBodyPath;
    private tv.danmaku.ijk.media.player.IjkMediaPlayer[] ijkPlayers;
    private tv.danmaku.ijk.media.player.IjkMediaPlayer ijkBassBody;
    private boolean usingIjk;

    /** Hold-stem stutter — one zone at a time. */
    private int stutterZone = -1;
    private int stutterSliceMs;
    private int stutterAnchorMs;

    private final Runnable driftFix = new Runnable() {
        @Override
        public void run() {
            if (released || !started) return;
            try {
                MediaPlayer lead = leadPlayer();
                if (lead == null || !lead.isPlaying()) {
                    main.postDelayed(this, 800);
                    return;
                }
                int pos = lead.getCurrentPosition();
                for (int i = 0; i < playerCount; i++) {
                    MediaPlayer p = players[i];
                    if (p == null || p == lead) continue;
                    int z = playerZones[i];
                    // Don't yank free-running or stuttering pads into lead while looping.
                    if (looping && z >= 0 && z < STEM_COUNT && !loopCtrl[z]) continue;
                    if (z == stutterZone) continue;
                    try {
                        if (!p.isPlaying()) {
                            int pd = p.getDuration();
                            if (pd > 0 && pos >= pd - 80) continue;
                        }
                        int d = Math.abs(p.getCurrentPosition() - pos);
                        if (d > 50) {
                            p.seekTo(pos);
                            if (!p.isPlaying() && started && !released) p.start();
                        }
                    } catch (Exception ignored) {}
                }
                syncBassBodyToLead(pos);
            } catch (Exception ignored) {}
            main.postDelayed(this, 800);
        }
    };

    private final Runnable loopTick = new Runnable() {
        @Override
        public void run() {
            if (released || !looping || !started) return;
            try {
                for (int i = 0; i < playerCount; i++) {
                    int z = playerZones[i];
                    if (!shouldSeekZoneOnLoopWrap(true, z >= 0 && z < STEM_COUNT && loopCtrl[z],
                            z == stutterZone)) {
                        continue;
                    }
                    try {
                        if (usingIjk && ijkPlayers != null && ijkPlayers[i] != null) {
                            tv.danmaku.ijk.media.player.IjkMediaPlayer ip = ijkPlayers[i];
                            if (!ip.isPlaying()) continue;
                            if ((int) ip.getCurrentPosition() >= loopEndMs - 20) {
                                ip.seekTo(loopStartMs);
                            }
                        } else {
                            MediaPlayer p = players[i];
                            if (p == null || !p.isPlaying()) continue;
                            if (p.getCurrentPosition() >= loopEndMs - 20) {
                                p.seekTo(loopStartMs);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (loopCtrl[2] && stutterZone != 2) {
                    try {
                        if (usingIjk && ijkBassBody != null && ijkBassBody.isPlaying()
                                && (int) ijkBassBody.getCurrentPosition() >= loopEndMs - 20) {
                            ijkBassBody.seekTo(loopStartMs);
                        } else if (bassBodyPlayer != null && bassBodyPlayer.isPlaying()
                                && bassBodyPlayer.getCurrentPosition() >= loopEndMs - 20) {
                            bassBodyPlayer.seekTo(loopStartMs);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
            main.postDelayed(this, 40);
        }
    };

    private final Runnable stutterTick = new Runnable() {
        @Override
        public void run() {
            if (released || stutterZone < 0 || !started) return;
            try {
                seekZone(stutterZone, stutterAnchorMs);
            } catch (Exception e) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("zone", stutterZone);
                    d.put("err", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                    com.solar.launcher.Debug543e15Log.log(
                            "StemMixer.stutterTick", "seek failed — stop chop", "F1", d);
                } catch (Exception ignored) {}
                // #endregion
                clearStutterInternal();
                return;
            }
            stutterTickCount++;
            // #region agent log
            if (stutterTickCount == 1 || stutterTickCount % 25 == 0) {
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("zone", stutterZone);
                    d.put("sliceMs", stutterSliceMs);
                    d.put("ticks", stutterTickCount);
                    com.solar.launcher.Debug543e15Log.log(
                            "StemMixer.stutterTick", "chop tick", "F1", d);
                } catch (Exception ignored) {}
            }
            // #endregion
            main.postDelayed(this, Math.max(MIN_STUTTER_MS, stutterSliceMs));
        }
    };

    private int stutterTickCount;

    public StemMixer(Context context) {
        this.app = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setBpm(float bpmValue) {
        bpm = bpmValue > 30f ? bpmValue : StemBpm.DEFAULT_BPM;
        msPerBar = StemBpm.msPerBar(bpm);
    }

    public float getBpm() {
        return bpm;
    }

    public int getMsPerBar() {
        return msPerBar;
    }

    public float getTargetRate() {
        return targetRate;
    }

    /**
     * Match this song to Song 1’s tempo (pitch-preserving when IJK pads are active).
     * Layman: remember how fast this song should run vs Song 1.
     * Technical: stores rate; applies IjkMediaPlayer.setSpeed when usingIjk.
     * Full MP→IJK migrate deferred (MT6572 memory) — drift sync covers playhead.
     * Was: always 1.0. Reversal: ignore setTargetRate.
     * 2026-07-19
     */
    public void setTargetRate(float rate) {
        float r = rate > 0.1f ? rate : 1f;
        if (r < StemBpm.MIN_RATE) r = StemBpm.MIN_RATE;
        if (r > StemBpm.MAX_RATE) r = StemBpm.MAX_RATE;
        targetRate = r;
        if (usingIjk) applyIjkSpeed(targetRate);
    }

    /** Apply SoundTouch speed on live IJK pads. 2026-07-19 */
    private void applyIjkSpeed(float speed) {
        if (ijkPlayers != null) {
            for (int i = 0; i < ijkPlayers.length; i++) {
                if (ijkPlayers[i] == null) continue;
                try {
                    ijkPlayers[i].setSpeed(speed);
                } catch (Exception ignored) {}
            }
        }
        if (ijkBassBody != null) {
            try {
                ijkBassBody.setSpeed(speed);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Build IJK stem pads with soundtouch=1 at targetRate (songs 2–3 when BPM differs).
     * Call instead of MediaPlayer load when StemTempoSync.needsSoundTouch(rate).
     * 2026-07-19
     */
    public void loadWithSoundTouch(List<LalalClient.StemFile> stems, File bassBodyWav, float rate)
            throws IOException {
        float r = rate > 0.1f ? rate : 1f;
        if (r < StemBpm.MIN_RATE) r = StemBpm.MIN_RATE;
        if (r > StemBpm.MAX_RATE) r = StemBpm.MAX_RATE;
        targetRate = r;
        releasePlayersOnly();
        preparedCount = 0;
        started = false;
        looping = false;
        usingIjk = true;
        clearStutterInternal();
        for (int i = 0; i < STEM_COUNT; i++) {
            gains[i] = 0f;
            loopCtrl[i] = false;
        }
        if (stems == null || stems.isEmpty()) {
            throw new IOException("Need stem files");
        }
        List<LalalClient.StemFile> ok = new ArrayList<LalalClient.StemFile>();
        boolean[] zoneHit = new boolean[STEM_COUNT];
        for (int i = 0; i < stems.size(); i++) {
            LalalClient.StemFile s = stems.get(i);
            if (s == null || s.file == null || !s.file.isFile()) continue;
            int z = s.zone;
            if (z < 0 || z >= STEM_COUNT) z = 3;
            zoneHit[z] = true;
            ok.add(s);
        }
        if (!zoneHit[0] || !zoneHit[1] || !zoneHit[2] || !zoneHit[3]) {
            throw new IOException("Need vocals, drums, bass, and at least one Melody/Other stem");
        }
        boolean wantBody = bassBodyWav != null && bassBodyWav.isFile() && bassBodyWav.length() > 1000;
        playerCount = ok.size();
        expectedPrepare = playerCount + (wantBody ? 1 : 0);
        players = new MediaPlayer[0];
        playerZones = new int[playerCount];
        playerPaths = new String[playerCount];
        ijkPlayers = new tv.danmaku.ijk.media.player.IjkMediaPlayer[playerCount];
        for (int i = 0; i < playerCount; i++) {
            final int index = i;
            final LalalClient.StemFile stem = ok.get(i);
            final int zone = stem.zone >= 0 && stem.zone < STEM_COUNT ? stem.zone : 3;
            playerZones[i] = zone;
            playerPaths[i] = stem.file.getAbsolutePath();
            tv.danmaku.ijk.media.player.IjkMediaPlayer ijk =
                    com.solar.launcher.video.SolarIjkPlayerFactory.create();
            StemSoundTouch.applyStemPlayerOptions(ijk);
            ijk.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijk.setDataSource(playerPaths[i]);
            final float speed = targetRate;
            ijk.setOnPreparedListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(tv.danmaku.ijk.media.player.IMediaPlayer mp) {
                    preparedCount++;
                    try {
                        ijk.setSpeed(speed);
                    } catch (Exception ignored) {}
                    applyGain(zone);
                    if (preparedCount >= expectedPrepare) {
                        refineMsPerBar();
                        if (listener != null) listener.onReady();
                    }
                }
            });
            ijk.setOnCompletionListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(tv.danmaku.ijk.media.player.IMediaPlayer mp) {
                    if (released) return;
                    if (stutterZone == zone) {
                        try {
                            ijk.seekTo(stutterAnchorMs);
                            ijk.start();
                        } catch (Exception ignored) {}
                        return;
                    }
                    if (looping && zone >= 0 && zone < STEM_COUNT && loopCtrl[zone]) {
                        try {
                            ijk.seekTo(loopStartMs);
                            ijk.start();
                        } catch (Exception ignored) {}
                        return;
                    }
                    if (zone == 0 && listener != null) listener.onComplete();
                }
            });
            ijkPlayers[index] = ijk;
            ijk.prepareAsync();
        }
        if (wantBody) {
            bassBodyPath = bassBodyWav.getAbsolutePath();
            ijkBassBody = com.solar.launcher.video.SolarIjkPlayerFactory.create();
            StemSoundTouch.applyStemPlayerOptions(ijkBassBody);
            ijkBassBody.setAudioStreamType(AudioManager.STREAM_MUSIC);
            ijkBassBody.setDataSource(bassBodyPath);
            ijkBassBody.setOnPreparedListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(tv.danmaku.ijk.media.player.IMediaPlayer mp) {
                    preparedCount++;
                    try {
                        ijkBassBody.setSpeed(targetRate);
                    } catch (Exception ignored) {}
                    applyGain(2);
                    if (preparedCount >= expectedPrepare) {
                        refineMsPerBar();
                        if (listener != null) listener.onReady();
                    }
                }
            });
            ijkBassBody.prepareAsync();
        } else {
            bassBodyPath = null;
        }
    }

    /** Which zones join the A–B loop (pad-local). Others free-run. */
    public void setLoopCtrlMask(boolean[] mask) {
        for (int i = 0; i < STEM_COUNT; i++) {
            loopCtrl[i] = mask != null && i < mask.length && mask[i];
        }
    }

    public void setLoopCtrlZone(int zone, boolean on) {
        if (zone < 0 || zone >= STEM_COUNT) return;
        loopCtrl[zone] = on;
    }

    public boolean isLoopCtrlZone(int zone) {
        return zone >= 0 && zone < STEM_COUNT && loopCtrl[zone];
    }

    /**
     * Load stems; optional bassBodyWav plays with Bass gain (zone 2).
     * 2026-07-19
     */
    /**
     * Pad-local loop: seek this zone on A–B wrap?
     * Free-run and stutter pads are left alone. 2026-07-19
     */
    public static boolean shouldSeekZoneOnLoopWrap(boolean looping, boolean inLoopCtrl,
            boolean isStutterZone) {
        return looping && inLoopCtrl && !isStutterZone;
    }

    public void load(List<LalalClient.StemFile> stems) throws IOException {
        load(stems, null);
    }

    public void load(List<LalalClient.StemFile> stems, File bassBodyWav) throws IOException {
        releasePlayersOnly();
        usingIjk = false;
        preparedCount = 0;
        started = false;
        looping = false;
        targetRate = 1f;
        clearStutterInternal();
        for (int i = 0; i < STEM_COUNT; i++) {
            gains[i] = 0f;
            loopCtrl[i] = false;
        }
        if (stems == null || stems.isEmpty()) {
            throw new IOException("Need stem files");
        }
        List<LalalClient.StemFile> ok = new ArrayList<LalalClient.StemFile>();
        boolean[] zoneHit = new boolean[STEM_COUNT];
        for (int i = 0; i < stems.size(); i++) {
            LalalClient.StemFile s = stems.get(i);
            if (s == null || s.file == null || !s.file.isFile()) continue;
            int z = s.zone;
            if (z < 0 || z >= STEM_COUNT) z = 3;
            zoneHit[z] = true;
            ok.add(s);
        }
        if (!zoneHit[0] || !zoneHit[1] || !zoneHit[2] || !zoneHit[3]) {
            throw new IOException("Need vocals, drums, bass, and at least one Melody/Other stem");
        }
        boolean wantBody = bassBodyWav != null && bassBodyWav.isFile() && bassBodyWav.length() > 1000;
        playerCount = ok.size();
        expectedPrepare = playerCount + (wantBody ? 1 : 0);
        players = new MediaPlayer[playerCount];
        playerZones = new int[playerCount];
        playerPaths = new String[playerCount];
        for (int i = 0; i < playerCount; i++) {
            final int index = i;
            final LalalClient.StemFile stem = ok.get(i);
            final int zone = stem.zone >= 0 && stem.zone < STEM_COUNT ? stem.zone : 3;
            playerZones[i] = zone;
            playerPaths[i] = stem.file.getAbsolutePath();
            MediaPlayer mp = new MediaPlayer();
            players[i] = mp;
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(stem.file.getAbsolutePath());
            wirePrepared(mp, zone);
            wireCompletion(mp, zone, index, stem.id);
            wireError(mp);
            mp.prepareAsync();
        }
        if (wantBody) {
            bassBodyPath = bassBodyWav.getAbsolutePath();
            bassBodyPlayer = new MediaPlayer();
            bassBodyPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            bassBodyPlayer.setDataSource(bassBodyWav.getAbsolutePath());
            wirePrepared(bassBodyPlayer, 2);
            wireCompletion(bassBodyPlayer, 2, -1, "bass_body");
            wireError(bassBodyPlayer);
            bassBodyPlayer.prepareAsync();
        } else {
            bassBodyPath = null;
        }
    }

    private void wirePrepared(MediaPlayer mp, final int zone) {
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                preparedCount++;
                applyGain(zone);
                if (preparedCount >= expectedPrepare) {
                    refineMsPerBar();
                    if (listener != null) listener.onReady();
                }
            }
        });
    }

    private void wireCompletion(MediaPlayer mp, final int zone, final int index, final String id) {
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (released) return;
                if (stutterZone == zone) {
                    try {
                        mediaPlayer.seekTo(stutterAnchorMs);
                        mediaPlayer.start();
                    } catch (Exception ignored) {}
                    return;
                }
                if (looping && zone >= 0 && zone < STEM_COUNT && loopCtrl[zone]) {
                    try {
                        mediaPlayer.seekTo(loopStartMs);
                        mediaPlayer.start();
                    } catch (Exception ignored) {}
                    return;
                }
                if (zone == 0 && !looping) {
                    pause();
                    if (listener != null) listener.onComplete();
                }
            }
        });
    }

    private void wireError(MediaPlayer mp) {
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if (listener != null) {
                    listener.onError("Stem play error " + what + "/" + extra);
                }
                return true;
            }
        });
    }

    private MediaPlayer leadPlayer() {
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] == 0 && players[i] != null) return players[i];
        }
        return playerCount > 0 ? players[0] : null;
    }

    private void refineMsPerBar() {
        try {
            MediaPlayer lead = leadPlayer();
            if (lead != null) {
                int dur = lead.getDuration();
                bpm = StemBpm.estimateFromDurationMs(dur);
                msPerBar = StemBpm.msPerBar(bpm);
            }
        } catch (Exception ignored) {}
    }

    private void syncBassBodyToLead(int pos) {
        if (bassBodyPlayer == null || stutterZone == 2) return;
        if (looping && !loopCtrl[2]) return;
        try {
            int d = Math.abs(bassBodyPlayer.getCurrentPosition() - pos);
            if (d > 50) {
                bassBodyPlayer.seekTo(pos);
                if (!bassBodyPlayer.isPlaying() && started && !released) bassBodyPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    public void play() {
        if (released) return;
        try {
            if (usingIjk && ijkPlayers != null) {
                for (int i = 0; i < ijkPlayers.length; i++) {
                    if (ijkPlayers[i] != null) {
                        ijkPlayers[i].seekTo(0);
                        ijkPlayers[i].start();
                    }
                }
                if (ijkBassBody != null) {
                    ijkBassBody.seekTo(0);
                    ijkBassBody.start();
                }
            } else {
                for (int i = 0; i < playerCount; i++) {
                    MediaPlayer p = players[i];
                    if (p != null) {
                        p.seekTo(0);
                        p.start();
                    }
                }
                if (bassBodyPlayer != null) {
                    bassBodyPlayer.seekTo(0);
                    bassBodyPlayer.start();
                }
            }
            started = true;
            main.removeCallbacks(driftFix);
            main.postDelayed(driftFix, 800);
        } catch (Exception e) {
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    public void pause() {
        stopStutter();
        if (usingIjk && ijkPlayers != null) {
            for (int i = 0; i < ijkPlayers.length; i++) {
                try {
                    if (ijkPlayers[i] != null && ijkPlayers[i].isPlaying()) ijkPlayers[i].pause();
                } catch (Exception ignored) {}
            }
            try {
                if (ijkBassBody != null && ijkBassBody.isPlaying()) ijkBassBody.pause();
            } catch (Exception ignored) {}
            return;
        }
        for (int i = 0; i < playerCount; i++) {
            try {
                MediaPlayer p = players[i];
                if (p != null && p.isPlaying()) p.pause();
            } catch (Exception ignored) {}
        }
        try {
            if (bassBodyPlayer != null && bassBodyPlayer.isPlaying()) bassBodyPlayer.pause();
        } catch (Exception ignored) {}
    }

    public void resume() {
        if (usingIjk && ijkPlayers != null) {
            for (int i = 0; i < ijkPlayers.length; i++) {
                try {
                    if (ijkPlayers[i] != null) ijkPlayers[i].start();
                } catch (Exception ignored) {}
            }
            try {
                if (ijkBassBody != null) ijkBassBody.start();
            } catch (Exception ignored) {}
            started = true;
            return;
        }
        for (int i = 0; i < playerCount; i++) {
            try {
                MediaPlayer p = players[i];
                if (p != null) p.start();
            } catch (Exception ignored) {}
        }
        try {
            if (bassBodyPlayer != null) bassBodyPlayer.start();
        } catch (Exception ignored) {}
        started = true;
    }

    public boolean togglePlayPause() {
        if (isPlaying()) {
            pause();
            return false;
        }
        resume();
        return true;
    }

    public boolean isPlaying() {
        try {
            if (usingIjk && ijkPlayers != null && ijkPlayers.length > 0 && ijkPlayers[0] != null) {
                return ijkPlayers[0].isPlaying();
            }
            MediaPlayer lead = leadPlayer();
            return lead != null && lead.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public int getPositionMs() {
        try {
            if (usingIjk && ijkPlayers != null && ijkPlayers.length > 0 && ijkPlayers[0] != null) {
                return (int) ijkPlayers[0].getCurrentPosition();
            }
            MediaPlayer lead = leadPlayer();
            return lead != null ? lead.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDurationMs() {
        try {
            if (usingIjk && ijkPlayers != null && ijkPlayers.length > 0 && ijkPlayers[0] != null) {
                return (int) ijkPlayers[0].getDuration();
            }
            MediaPlayer lead = leadPlayer();
            return lead != null ? lead.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void startLoop(float bars) {
        float b = bars > 0f ? bars : StemControls.DEFAULT_LOOP_BARS;
        loopBars = b;
        int pos = getPositionMs();
        int dur = getDurationMs();
        int len = Math.max(200, Math.round(b * msPerBar));
        loopStartMs = pos;
        loopEndMs = pos + len;
        if (dur > 0 && loopEndMs > dur) {
            loopEndMs = dur;
            loopStartMs = Math.max(0, dur - len);
        }
        looping = true;
        main.removeCallbacks(loopTick);
        main.post(loopTick);
    }

    public void setLoopBars(float bars) {
        loopBars = bars > 0f ? bars : StemControls.DEFAULT_LOOP_BARS;
        if (!looping) return;
        int len = Math.max(200, Math.round(loopBars * msPerBar));
        int dur = getDurationMs();
        loopEndMs = loopStartMs + len;
        if (dur > 0 && loopEndMs > dur) {
            loopEndMs = dur;
        }
    }

    public float getLoopBars() {
        return loopBars;
    }

    public boolean isLooping() {
        return looping;
    }

    public void clearLoop() {
        looping = false;
        main.removeCallbacks(loopTick);
        for (int i = 0; i < STEM_COUNT; i++) loopCtrl[i] = false;
    }

    /**
     * Hold-stem stutter — hip-hop chop on one pad while key is held.
     * Layman: mash the pad, it chatters on the beat; let go and it keeps going.
     * Was: min delay 40ms + wheel called full restart (seek storm → Y1 native death).
     * Reversal: Math.max(40,…) + startHoldStutter on every wheel notch.
     * 2026-07-19
     */
    public void startHoldStutter(int zone, int sliceMs) {
        if (released || zone < 0 || zone >= STEM_COUNT) return;
        // Same zone already chopping — only resize slice (wheel), do not re-seek storm. 2026-07-19
        if (stutterZone == zone && stutterSliceMs > 0) {
            setStutterSliceMs(sliceMs);
            return;
        }
        stopStutter();
        stutterZone = zone;
        stutterTickCount = 0;
        stutterSliceMs = sliceMs > MIN_STUTTER_MS ? sliceMs : DEFAULT_STUTTER_MS;
        if (stutterSliceMs < MIN_STUTTER_MS) stutterSliceMs = MIN_STUTTER_MS;
        stutterAnchorMs = positionForZone(zone);
        if (looping && loopCtrl[zone]) {
            // Keep stutter inside A–B window.
            if (stutterAnchorMs < loopStartMs || stutterAnchorMs >= loopEndMs) {
                stutterAnchorMs = loopStartMs;
            }
            int maxSlice = Math.max(MIN_STUTTER_MS, loopEndMs - stutterAnchorMs);
            if (stutterSliceMs > maxSlice) stutterSliceMs = maxSlice;
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("zone", zone);
            d.put("sliceMs", stutterSliceMs);
            d.put("anchor", stutterAnchorMs);
            com.solar.launcher.Debug543e15Log.log(
                    "StemMixer.startHoldStutter", "chop start", "F1", d);
        } catch (Exception ignored) {}
        // #endregion
        seekZone(zone, stutterAnchorMs);
        main.removeCallbacks(stutterTick);
        main.post(stutterTick);
    }

    /**
     * Resize active chop without resetting the anchor (wheel while held).
     * 2026-07-19
     */
    public void setStutterSliceMs(int sliceMs) {
        if (stutterZone < 0) return;
        int s = sliceMs > MIN_STUTTER_MS ? sliceMs : MIN_STUTTER_MS;
        if (looping && loopCtrl[stutterZone]) {
            int maxSlice = Math.max(MIN_STUTTER_MS, loopEndMs - stutterAnchorMs);
            if (s > maxSlice) s = maxSlice;
        }
        stutterSliceMs = s;
    }

    public void stopStutter() {
        if (stutterZone >= 0) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("zone", stutterZone);
                d.put("ticks", stutterTickCount);
                com.solar.launcher.Debug543e15Log.log(
                        "StemMixer.stopStutter", "chop stop", "F1", d);
            } catch (Exception ignored) {}
            // #endregion
        }
        clearStutterInternal();
    }

    public boolean isStuttering() {
        return stutterZone >= 0;
    }

    public int getStutterZone() {
        return stutterZone;
    }

    private void clearStutterInternal() {
        main.removeCallbacks(stutterTick);
        stutterZone = -1;
        stutterSliceMs = 0;
        stutterAnchorMs = 0;
        stutterTickCount = 0;
    }

    private int positionForZone(int zone) {
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] != zone) continue;
            try {
                if (usingIjk && ijkPlayers != null && ijkPlayers[i] != null) {
                    return (int) ijkPlayers[i].getCurrentPosition();
                }
                if (players.length > i && players[i] != null) {
                    return players[i].getCurrentPosition();
                }
            } catch (Exception ignored) {}
        }
        if (zone == 2) {
            try {
                if (usingIjk && ijkBassBody != null) {
                    return (int) ijkBassBody.getCurrentPosition();
                }
                if (bassBodyPlayer != null) return bassBodyPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return getPositionMs();
    }

    private void seekZone(int zone, int ms) {
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] != zone) continue;
            try {
                if (usingIjk && ijkPlayers != null && ijkPlayers[i] != null) {
                    ijkPlayers[i].seekTo(ms);
                    if (started && !ijkPlayers[i].isPlaying()) ijkPlayers[i].start();
                } else if (players != null && players.length > i && players[i] != null) {
                    MediaPlayer p = players[i];
                    p.seekTo(ms);
                    if (started && !p.isPlaying()) p.start();
                }
            } catch (IllegalStateException ise) {
                // MediaPlayer in bad state — abort chop rather than native death. 2026-07-19
                throw ise;
            } catch (Exception ignored) {}
        }
        if (zone == 2) {
            try {
                if (usingIjk && ijkBassBody != null) {
                    ijkBassBody.seekTo(ms);
                    if (started && !ijkBassBody.isPlaying()) ijkBassBody.start();
                } else if (bassBodyPlayer != null) {
                    bassBodyPlayer.seekTo(ms);
                    if (started && !bassBodyPlayer.isPlaying()) bassBodyPlayer.start();
                }
            } catch (Exception ignored) {}
        }
    }

    /** Sync this mixer’s playhead toward masterMs (multi-song drift). 2026-07-19 */
    public void seekAllPlaying(int ms) {
        for (int i = 0; i < playerCount; i++) {
            int z = playerZones[i];
            if (z == stutterZone) continue;
            if (looping && z >= 0 && z < STEM_COUNT && !loopCtrl[z]) continue;
            try {
                if (usingIjk && ijkPlayers != null && ijkPlayers[i] != null) {
                    ijkPlayers[i].seekTo(ms);
                } else if (players.length > i && players[i] != null) {
                    players[i].seekTo(ms);
                }
            } catch (Exception ignored) {}
        }
        if (stutterZone != 2) {
            try {
                if (usingIjk && ijkBassBody != null) ijkBassBody.seekTo(ms);
                else if (bassBodyPlayer != null) bassBodyPlayer.seekTo(ms);
            } catch (Exception ignored) {}
        }
    }

    public void setGain(int zone, float gain) {
        if (zone < 0 || zone >= STEM_COUNT) return;
        gains[zone] = StemControls.clampGain(gain);
        applyGain(zone);
    }

    public float getGain(int zone) {
        if (zone < 0 || zone >= STEM_COUNT) return 0f;
        return gains[zone];
    }

    public float nudgeGainSteps(int zone, int steps) {
        float g = StemControls.nudgeGain(getGain(zone), steps);
        setGain(zone, g);
        return g;
    }

    public float nudgeGain(int zone, float delta) {
        setGain(zone, getGain(zone) + delta);
        return getGain(zone);
    }

    private void applyGain(int zone) {
        float g = gains[zone];
        if (usingIjk && ijkPlayers != null) {
            for (int i = 0; i < ijkPlayers.length; i++) {
                if (playerZones[i] != zone) continue;
                if (ijkPlayers[i] == null) continue;
                try {
                    ijkPlayers[i].setVolume(g, g);
                } catch (Exception ignored) {}
            }
            if (zone == 2 && ijkBassBody != null) {
                float bg = g * StemBassBody.BODY_GAIN_K;
                try {
                    ijkBassBody.setVolume(bg, bg);
                } catch (Exception ignored) {}
            }
            return;
        }
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] != zone) continue;
            MediaPlayer p = players[i];
            if (p == null) continue;
            try {
                p.setVolume(g, g);
            } catch (Exception ignored) {}
        }
        if (zone == 2 && bassBodyPlayer != null) {
            float bg = g * StemBassBody.BODY_GAIN_K;
            try {
                bassBodyPlayer.setVolume(bg, bg);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Swap one pad’s stem file mid-session — DEPRECATED for mashup (causes restart).
     * Host now keeps one StemMixer per song always running; cycle = control routing only.
     * Kept for possible lab tooling. Was: mashup cycle path. Reversal: call from swapPadStem.
     * 2026-07-19
     */
    public void replaceZoneStem(int zone, File stemFile) throws IOException {
        if (released || zone < 0 || zone >= STEM_COUNT) {
            throw new IOException("replaceZoneStem bad zone");
        }
        if (stemFile == null || !stemFile.isFile() || stemFile.length() < 100) {
            throw new IOException("replaceZoneStem missing file");
        }
        if (usingIjk) {
            // Rate≠1 path deferred — mashup uses MediaPlayer @ 1.0 for now. 2026-07-19
            throw new IOException("replaceZoneStem needs MediaPlayer path");
        }
        // Drop every player currently on this zone (Melody may have several). 2026-07-19
        java.util.ArrayList<MediaPlayer> keepP = new java.util.ArrayList<MediaPlayer>();
        java.util.ArrayList<Integer> keepZ = new java.util.ArrayList<Integer>();
        java.util.ArrayList<String> keepPaths = new java.util.ArrayList<String>();
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] == zone) {
                MediaPlayer old = players[i];
                players[i] = null;
                if (old != null) {
                    try { old.stop(); } catch (Exception ignored) {}
                    try { old.release(); } catch (Exception ignored) {}
                }
            } else {
                keepP.add(players[i]);
                keepZ.add(Integer.valueOf(playerZones[i]));
                keepPaths.add(playerPaths != null && i < playerPaths.length
                        ? playerPaths[i] : null);
            }
        }
        // Bass body rides zone 2 — drop it when swapping bass. 2026-07-19
        if (zone == 2 && bassBodyPlayer != null) {
            try { bassBodyPlayer.stop(); } catch (Exception ignored) {}
            try { bassBodyPlayer.release(); } catch (Exception ignored) {}
            bassBodyPlayer = null;
            bassBodyPath = null;
        }
        final int newIndex = keepP.size();
        MediaPlayer mp = new MediaPlayer();
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mp.setDataSource(stemFile.getAbsolutePath());
        keepP.add(mp);
        keepZ.add(Integer.valueOf(zone));
        keepPaths.add(stemFile.getAbsolutePath());
        players = keepP.toArray(new MediaPlayer[keepP.size()]);
        playerZones = new int[keepZ.size()];
        for (int i = 0; i < keepZ.size(); i++) playerZones[i] = keepZ.get(i).intValue();
        playerPaths = keepPaths.toArray(new String[keepPaths.size()]);
        playerCount = players.length;
        expectedPrepare = playerCount + (bassBodyPlayer != null ? 1 : 0);
        // Don't fire onReady again for a mid-session swap — just start when prepared. 2026-07-19
        final boolean wasStarted = started;
        final int leadPos = getPositionMs();
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                applyGain(zone);
                try {
                    if (wasStarted) {
                        mediaPlayer.seekTo(leadPos);
                        mediaPlayer.start();
                    }
                } catch (Exception ignored) {}
            }
        });
        wireCompletion(mp, zone, newIndex, stemFile.getName());
        wireError(mp);
        mp.prepareAsync();
    }

    /**
     * Stem file currently driving a zone (first player on that pad).
     * 2026-07-19
     */
    public File stemFileForZone(int zone) {
        if (zone < 0 || zone >= STEM_COUNT) return null;
        for (int i = 0; i < playerCount; i++) {
            if (playerZones[i] != zone) continue;
            if (playerPaths != null && i < playerPaths.length && playerPaths[i] != null) {
                return new File(playerPaths[i]);
            }
        }
        return null;
    }

    public void release() {
        released = true;
        looping = false;
        clearStutterInternal();
        main.removeCallbacks(driftFix);
        main.removeCallbacks(loopTick);
        releasePlayersOnly();
        listener = null;
    }

    private void releasePlayersOnly() {
        for (int i = 0; i < players.length; i++) {
            MediaPlayer p = players[i];
            players[i] = null;
            if (p == null) continue;
            try { p.stop(); } catch (Exception ignored) {}
            try { p.release(); } catch (Exception ignored) {}
        }
        if (bassBodyPlayer != null) {
            try { bassBodyPlayer.stop(); } catch (Exception ignored) {}
            try { bassBodyPlayer.release(); } catch (Exception ignored) {}
            bassBodyPlayer = null;
        }
        if (ijkPlayers != null) {
            for (int i = 0; i < ijkPlayers.length; i++) {
                tv.danmaku.ijk.media.player.IjkMediaPlayer p = ijkPlayers[i];
                ijkPlayers[i] = null;
                if (p == null) continue;
                try { p.stop(); } catch (Exception ignored) {}
                try { p.release(); } catch (Exception ignored) {}
            }
        }
        if (ijkBassBody != null) {
            try { ijkBassBody.stop(); } catch (Exception ignored) {}
            try { ijkBassBody.release(); } catch (Exception ignored) {}
            ijkBassBody = null;
        }
        players = new MediaPlayer[0];
        ijkPlayers = null;
        usingIjk = false;
        playerCount = 0;
        expectedPrepare = 0;
        preparedCount = 0;
    }
}
