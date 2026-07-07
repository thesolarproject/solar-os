package com.solar.launcher.radio.fm;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;

import java.lang.reflect.Method;

/**
 * 2026-07-06 — Routes FM chip audio to speaker/earphones via MediaTek MediaPlayer URL.
 * Layman: turns the silent FM chip into sound you can hear.
 * Technical: {@code MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM} on STREAM_FM + AudioSystem.setForceUse.
 */
public final class FmAudioRouter {
  public static final String PREF_FM_SPEAKER = "radio.fm_speaker";

  private static final int STREAM_FM_FALLBACK = 10;
  private static final String FM_DATASOURCE = "MEDIATEK://MEDIAPLAYER_PLAYERTYPE_FM";
  /** AudioSystem.FOR_MEDIA — route FM output. */
  private static final int FORCE_USE_FOR_MEDIA = 5;

  private final Context appCtx;
  private MediaPlayer fmPlayer;
  private boolean speakerOn;

  public FmAudioRouter(Context ctx) {
    appCtx = ctx.getApplicationContext();
    speakerOn = appCtx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE)
        .getBoolean(PREF_FM_SPEAKER, false);
  }

  public boolean isSpeakerOn() {
    return speakerOn;
  }

  /** Persist + apply speaker routing (JJ setSpeaker). */
  public void setSpeaker(boolean useSpeaker) {
    speakerOn = useSpeaker;
    appCtx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE)
        .edit()
        .putBoolean(PREF_FM_SPEAKER, useSpeaker)
        .apply();
    applyForceUse(useSpeaker);
  }

  /** Start MTK FM audio pump after native powerup succeeds. */
  public synchronized void start() {
    stop();
    try {
      fmPlayer = new MediaPlayer();
      fmPlayer.setDataSource(FM_DATASOURCE);
      fmPlayer.setAudioStreamType(resolveStreamFm());
      fmPlayer.prepare();
      fmPlayer.start();
      applyForceUse(speakerOn);
    } catch (Throwable t) {
      stop();
      throw new RuntimeException("FM audio routing failed: " + t.getMessage(), t);
    }
  }

  /** Release MediaPlayer when FM powers down. */
  public synchronized void stop() {
    if (fmPlayer == null) return;
    try {
      if (fmPlayer.isPlaying()) fmPlayer.stop();
    } catch (Throwable ignored) {}
    try {
      fmPlayer.release();
    } catch (Throwable ignored) {}
    fmPlayer = null;
  }

  private static int resolveStreamFm() {
    try {
      return (Integer) AudioManager.class.getDeclaredField("STREAM_FM").get(null);
    } catch (Exception e) {
      return STREAM_FM_FALLBACK;
    }
  }

  private static void applyForceUse(boolean useSpeaker) {
    try {
      Method setForceUse =
          Class.forName("android.media.AudioSystem")
              .getDeclaredMethod("setForceUse", int.class, int.class);
      setForceUse.setAccessible(true);
      setForceUse.invoke(null, FORCE_USE_FOR_MEDIA, useSpeaker ? 1 : 0);
    } catch (Throwable ignored) {}
  }
}
