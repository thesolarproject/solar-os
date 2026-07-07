package com.solar.launcher.radio.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 2026-07-06 — FM airplane session snapshot logic (no Android mocks). */
public class FmAirplaneModeHelperTest {

  @Test
  public void shouldRestoreAirplane_whenWasOnAtStart() {
    FmAirplaneModeHelper.Snapshot snap =
        FmAirplaneModeHelper.captureSnapshot(true, false, true);
    assertTrue(FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(snap));
  }

  @Test
  public void shouldNotRestoreAirplane_whenWasOffAtStart() {
    FmAirplaneModeHelper.Snapshot snap =
        FmAirplaneModeHelper.captureSnapshot(false, true, false);
    assertFalse(FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(snap));
  }

  @Test
  public void shouldNotRestoreAirplane_whenSnapshotNull() {
    assertFalse(FmAirplaneModeHelper.shouldRestoreAirplaneOnEnd(null));
  }

  @Test
  public void captureSnapshot_preservesFlags() {
    FmAirplaneModeHelper.Snapshot snap =
        FmAirplaneModeHelper.captureSnapshot(true, true, false);
    assertTrue(snap.airplaneWasOn);
    assertTrue(snap.wifiWasEnabled);
    assertFalse(snap.bluetoothWasEnabled);
  }

  @Test
  public void sessionOwner_noneByDefault() {
    assertEquals(FmAirplaneModeHelper.SessionOwner.NONE, FmAirplaneModeHelper.SessionOwner.NONE);
    assertFalse(FmAirplaneModeHelper.isSessionActive());
  }
}
