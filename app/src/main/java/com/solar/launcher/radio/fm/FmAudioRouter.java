package com.solar.launcher.radio.fm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * 2026-07-15 — FM audio pipe matching working MTK stock + JJ + Innioasis devices.
 * Layman: open the chip’s sound pipe and send it only where the user picked
 * (wired headphones, Bluetooth, or speaker).
 *
 * Technical (from reference FM apps):
 * - Stock {@code FmRadioService}: STREAM_MUSIC +
 *   {@code MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM} (or THIRDPARTY:// on some ROMs),
 *   {@code prepare()} then {@code start()}, force-use FOR_PROPRIETARY(=1).
 * - JJ: same URI, STREAM_FM (10), force-use usage 5.
 * - Sequence: mute chip → start MediaPlayer → setRds/unmute (engine), re-apply output.
 *
 * Was: STREAM_FM-first only + MEDIATEK only + usage-5 only → silent on some Y1 builds.
 */
public final class FmAudioRouter {
  public static final String PREF_FM_SPEAKER = "radio.fm_speaker";
  public static final String PREF_FM_OUTPUT = "radio.fm_output";

  private static final String TAG = "FmAudioRouter";
  private static final int STREAM_FM_FALLBACK = 10;
  /** Stock android_packages_apps_FMRadio + JJ. */
  private static final String URI_MEDIATEK = "MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM";
  /** Older / Vanzo MTK tree in reference/MTK_FM-RADIO-master. */
  private static final String URI_THIRDPARTY = "THIRDPARTY://MEDIAPLAYER_PLAYERTYPE_FM";

  /** Stock FmRadioService.FOR_PROPRIETARY (= AOSP FOR_MEDIA). */
  private static final int FORCE_USE_PROPRIETARY = 1;
  /** JJ setForceUse first arg. */
  private static final int FORCE_USE_MEDIA_JJ = 5;
  private static final int FORCE_NONE = 0;
  private static final int FORCE_SPEAKER = 1;
  private static final int FORCE_HEADPHONES = 2;
  private static final int FORCE_BT_A2DP = 4;

  /** Broadcasts stock sends so music/ATV/FM-TX release audio (optional peers). */
  private static final String[] STOP_OTHER_ACTIONS = {
    "com.android.music.musicservicecommand.pause",
    "com.android.music.musicservicecommand",
    "com.mediatek.FMTransmitter.FMTransmitterService.ACTION_TOFMSERVICE_POWERDOWN",
  };

  public enum Output {
    WIRED,
    BLUETOOTH,
    SPEAKER;

    static Output fromPref(String raw) {
      if (raw == null) return WIRED;
      String s = raw.trim().toLowerCase(java.util.Locale.US);
      if ("speaker".equals(s)) return SPEAKER;
      if ("bluetooth".equals(s) || "bt".equals(s)) return BLUETOOTH;
      return WIRED;
    }

    String prefValue() {
      switch (this) {
        case SPEAKER:
          return "speaker";
        case BLUETOOTH:
          return "bluetooth";
        default:
          return "wired";
      }
    }

    Output next() {
      switch (this) {
        case WIRED:
          return BLUETOOTH;
        case BLUETOOTH:
          return SPEAKER;
        default:
          return WIRED;
      }
    }
  }

  private final Context appCtx;
  private final AudioManager audioManager;
  private MediaPlayer fmPlayer;
  private Output output = Output.WIRED;
  private int activeStream = AudioManager.STREAM_MUSIC;
  private String lastError = "";
  private String lastUri = "";
  private AudioManager.OnAudioFocusChangeListener focusListener;

  public FmAudioRouter(Context ctx) {
    appCtx = ctx.getApplicationContext();
    audioManager = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);
    output = loadOutput(appCtx);
  }

  static Output loadOutput(Context ctx) {
    SharedPreferences prefs = ctx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE);
    if (prefs.contains(PREF_FM_OUTPUT)) {
      return Output.fromPref(prefs.getString(PREF_FM_OUTPUT, "wired"));
    }
    boolean speaker = prefs.getBoolean(PREF_FM_SPEAKER, false);
    Output o = speaker ? Output.SPEAKER : Output.WIRED;
    prefs.edit().putString(PREF_FM_OUTPUT, o.prefValue()).apply();
    return o;
  }

  public Output getOutput() {
    return output;
  }

  public boolean isSpeakerOn() {
    return output == Output.SPEAKER;
  }

  public String lastError() {
    return lastError != null ? lastError : "";
  }

  /** Diagnostics: which URI/stream succeeded. */
  public String lastRouteDiag() {
    return "uri=" + lastUri + " stream=" + activeStream + " out=" + output;
  }

  public int activeStreamType() {
    return activeStream;
  }

  public synchronized boolean isPlaying() {
    try {
      return fmPlayer != null && fmPlayer.isPlaying();
    } catch (Throwable t) {
      return false;
    }
  }

  public void setSpeaker(boolean useSpeaker) {
    setOutput(useSpeaker ? Output.SPEAKER : Output.WIRED);
  }

  public void setOutput(Output out) {
    if (out == null) out = Output.WIRED;
    output = out;
    appCtx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_FM_OUTPUT, out.prefValue())
        .putBoolean(PREF_FM_SPEAKER, out == Output.SPEAKER)
        .apply();
    applyOutputRoute();
  }

  public Output cycleOutput() {
    Output next = output.next();
    setOutput(next);
    return next;
  }

  /**
   * Start FM MediaPlayer after native powerup (stock enableFmAudio + JJ fallbacks).
   * Tries URI × stream matrix: stock (MUSIC+MEDIATEK) first, then JJ STREAM_FM.
   */
  public synchronized boolean start() {
    lastError = "";
    lastUri = "";
    stopQuiet();
    requestFocus();
    applyOutputRoute(); // before prepare — stock sets speaker before enableFmAudio
    notifyStopOtherApps();

    int streamFm = resolveStreamFm();
    // Matrix: stock path first (Y1 FMRadio.apk), then JJ, then THIRDPARTY URI.
    String[] uris = new String[] {URI_MEDIATEK, URI_THIRDPARTY};
    int[] streams = new int[] {AudioManager.STREAM_MUSIC, streamFm};

    for (String uri : uris) {
      for (int stream : streams) {
        if (tryStart(uri, stream)) {
          activeStream = stream;
          lastUri = uri;
          ensureAudibleVolume(stream);
          if (stream != AudioManager.STREAM_MUSIC) {
            ensureAudibleVolume(AudioManager.STREAM_MUSIC);
          }
          applyOutputRoute();
          Log.i(TAG, "FM audio OK " + lastRouteDiag());
          return true;
        }
        stopQuiet();
      }
    }
    abandonFocus();
    if (lastError == null || lastError.isEmpty()) {
      lastError = "FM MediaPlayer failed all URI/stream combos";
    }
    Log.e(TAG, "FM audio FAIL " + lastError);
    return false;
  }

  public synchronized void stop() {
    stopQuiet();
    applyForcePair(FORCE_NONE);
    abandonFocus();
  }

  /**
   * 2026-07-15 — Route FM audio to the user-chosen sink only.
   * Layman: headphones on → headphones (unless you picked Speaker). Speaker only when chosen.
   * Technical: stock FmRadioService uses FOR_PROPRIETARY + FORCE_NONE for earphone mode and
   * FORCE_SPEAKER only for speaker mode. Wired jack always wins over BT unless SPEAKER.
   * Was: FORCE_HEADPHONES always for WIRED could leak to speaker on some MTK policies.
   */
  public void applyOutputRoute() {
    boolean wired = isWiredHeadsetOn();
    boolean bt = isBtA2dpOrHeadsetConnected();
    int force;
    // Explicit user choice: Speaker — only case that forces the built-in speaker.
    if (output == Output.SPEAKER) {
      force = FORCE_SPEAKER;
    } else if (wired) {
      // Stock earphone mode: FORCE_NONE so policy prefers the jack (antenna + audio).
      // Do NOT force SPEAKER even if a stale pref said so mid-route — SPEAKER is above.
      force = FORCE_NONE;
      // Keep logical mode in sync when jack is in and user did not pick speaker.
      if (output != Output.WIRED) {
        output = Output.WIRED;
        appCtx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_FM_OUTPUT, Output.WIRED.prefValue())
            .putBoolean(PREF_FM_SPEAKER, false)
            .apply();
      }
    } else if (output == Output.BLUETOOTH && bt) {
      force = FORCE_BT_A2DP;
    } else if (output == Output.BLUETOOTH && !bt) {
      // No BT sink — stay off speaker (FORCE_NONE), not SPEAKER.
      force = FORCE_NONE;
    } else {
      // WIRED preferred but no jack: FORCE_NONE (device default), never invent speaker.
      force = FORCE_NONE;
    }
    applyForcePair(force);
    Log.i(
        TAG,
        "applyOutputRoute out="
            + output
            + " force="
            + force
            + " wired="
            + wired
            + " bt="
            + bt);
  }

  /**
   * 2026-07-15 — Headset plug: stock switches to earphone mode (FORCE_NONE), not speaker.
   * Layman: plug in headphones → radio comes out the cable unless Speaker mode is locked on.
   */
  public void onHeadsetPlug(boolean pluggedIn) {
    if (pluggedIn && output != Output.SPEAKER) {
      setOutput(Output.WIRED);
    } else {
      applyOutputRoute();
    }
  }

  private void applyForcePair(int forceConfig) {
    // Stock FOR_PROPRIETARY first, JJ usage second.
    setForceUse(FORCE_USE_PROPRIETARY, forceConfig);
    setForceUse(FORCE_USE_MEDIA_JJ, forceConfig);
  }

  private static void setForceUse(int usage, int config) {
    try {
      Method m =
          Class.forName("android.media.AudioSystem")
              .getDeclaredMethod("setForceUse", int.class, int.class);
      m.setAccessible(true);
      m.invoke(null, usage, config);
    } catch (Throwable ignored) {}
  }

  private void notifyStopOtherApps() {
    // Best-effort: stock sends power-down intents so music yields.
    for (String action : STOP_OTHER_ACTIONS) {
      try {
        Intent i = new Intent(action);
        if (action.endsWith("musicservicecommand")) {
          i.putExtra("command", "pause");
        }
        appCtx.sendBroadcast(i);
      } catch (Throwable ignored) {}
    }
  }

  /** True when a wired headset/earphones are plugged in (FM antenna + audio). */
  public boolean isWiredHeadsetOn() {
    if (audioManager == null) return false;
    try {
      return audioManager.isWiredHeadsetOn();
    } catch (Throwable t) {
      return false;
    }
  }

  public boolean isBtA2dpOrHeadsetConnected() {
    try {
      BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
      if (bt == null || !bt.isEnabled()) return false;
      int a2dp = bt.getProfileConnectionState(BluetoothProfile.A2DP);
      int hs = bt.getProfileConnectionState(BluetoothProfile.HEADSET);
      return a2dp == BluetoothProfile.STATE_CONNECTED
          || a2dp == BluetoothProfile.STATE_CONNECTING
          || hs == BluetoothProfile.STATE_CONNECTED
          || hs == BluetoothProfile.STATE_CONNECTING;
    } catch (Throwable t) {
      return false;
    }
  }

  private void stopQuiet() {
    if (fmPlayer == null) return;
    try {
      if (fmPlayer.isPlaying()) fmPlayer.stop();
    } catch (Throwable ignored) {}
    try {
      fmPlayer.release();
    } catch (Throwable ignored) {}
    fmPlayer = null;
  }

  /**
   * Stock initFmPlayer + enableFmAudio: setDataSource → stream type → prepare → start.
   */
  private boolean tryStart(String uri, int streamType) {
    try {
      fmPlayer = new MediaPlayer();
      try {
        fmPlayer.setWakeMode(appCtx, android.os.PowerManager.PARTIAL_WAKE_LOCK);
      } catch (Throwable ignored) {}
      fmPlayer.setDataSource(uri);
      fmPlayer.setAudioStreamType(streamType);
      // Stock: prepare() then start() — no prepareWithoutScan on Y1 path.
      try {
        fmPlayer.prepare();
      } catch (Throwable prepareEx) {
        // Some builds expose prepareWithoutScan as a fallback.
        try {
          Method m = MediaPlayer.class.getMethod("prepareWithoutScan");
          m.invoke(fmPlayer);
        } catch (Throwable t2) {
          lastError = "prepare " + uri + " stream=" + streamType + ": " + prepareEx.getMessage();
          stopQuiet();
          return false;
        }
      }
      fmPlayer.start();
      if (!fmPlayer.isPlaying()) {
        lastError = "start not playing " + uri + " stream=" + streamType;
        stopQuiet();
        return false;
      }
      return true;
    } catch (Throwable t) {
      lastError = "route " + uri + " stream=" + streamType + ": " + t.getMessage();
      stopQuiet();
      return false;
    }
  }

  private boolean requestFocus() {
    if (audioManager == null) return false;
    if (focusListener == null) {
      focusListener =
          new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
              if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                try {
                  if (fmPlayer != null && fmPlayer.isPlaying()) fmPlayer.pause();
                } catch (Throwable ignored) {}
              } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                try {
                  if (fmPlayer != null) fmPlayer.start();
                } catch (Throwable ignored) {}
              }
            }
          };
    }
    try {
      int r =
          audioManager.requestAudioFocus(
              focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
      return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    } catch (Throwable t) {
      return false;
    }
  }

  private void abandonFocus() {
    if (audioManager == null || focusListener == null) return;
    try {
      audioManager.abandonAudioFocus(focusListener);
    } catch (Throwable ignored) {}
  }

  private void ensureAudibleVolume(int streamType) {
    if (audioManager == null) return;
    try {
      int max = audioManager.getStreamMaxVolume(streamType);
      if (max <= 0) return;
      int cur = audioManager.getStreamVolume(streamType);
      if (cur > 0) return;
      int music = 0;
      try {
        music = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
      } catch (Throwable ignored) {}
      int target = music > 0 ? Math.min(music, max) : Math.max(1, max / 2);
      audioManager.setStreamVolume(streamType, target, 0);
    } catch (Throwable ignored) {}
  }

  private static int resolveStreamFm() {
    try {
      return (Integer) AudioManager.class.getDeclaredField("STREAM_FM").get(null);
    } catch (Exception e) {
      return STREAM_FM_FALLBACK;
    }
  }
}
