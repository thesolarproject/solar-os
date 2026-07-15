package com.solar.launcher.radio.fm;

import org.junit.Test;

/**
 * 2026-07-15 — FM output enum cycle + pref parse (Wired / Bluetooth / Speaker).
 * ponytail: pure logic only — no device AudioSystem.
 */
public class FmAudioOutputTest {

  @Test
  public void cycleOrderWiredBluetoothSpeaker() {
    FmAudioRouter.Output o = FmAudioRouter.Output.WIRED;
    o = o.next();
    if (o != FmAudioRouter.Output.BLUETOOTH) throw new AssertionError("wired→bt " + o);
    o = o.next();
    if (o != FmAudioRouter.Output.SPEAKER) throw new AssertionError("bt→speaker " + o);
    o = o.next();
    if (o != FmAudioRouter.Output.WIRED) throw new AssertionError("speaker→wired " + o);
  }

  @Test
  public void fromPrefRecognizesAliases() {
    if (FmAudioRouter.Output.fromPref("speaker") != FmAudioRouter.Output.SPEAKER) {
      throw new AssertionError("speaker");
    }
    if (FmAudioRouter.Output.fromPref("bluetooth") != FmAudioRouter.Output.BLUETOOTH) {
      throw new AssertionError("bluetooth");
    }
    if (FmAudioRouter.Output.fromPref("bt") != FmAudioRouter.Output.BLUETOOTH) {
      throw new AssertionError("bt alias");
    }
    if (FmAudioRouter.Output.fromPref("wired") != FmAudioRouter.Output.WIRED) {
      throw new AssertionError("wired");
    }
    if (FmAudioRouter.Output.fromPref(null) != FmAudioRouter.Output.WIRED) {
      throw new AssertionError("null→wired");
    }
  }

  @Test
  public void prefValueRoundTrip() {
    for (FmAudioRouter.Output o :
        new FmAudioRouter.Output[] {
          FmAudioRouter.Output.WIRED,
          FmAudioRouter.Output.BLUETOOTH,
          FmAudioRouter.Output.SPEAKER
        }) {
      if (FmAudioRouter.Output.fromPref(o.prefValue()) != o) {
        throw new AssertionError("round-trip " + o);
      }
    }
  }
}
