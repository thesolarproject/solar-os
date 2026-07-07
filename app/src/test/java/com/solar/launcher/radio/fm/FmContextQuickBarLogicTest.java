package com.solar.launcher.radio.fm;

import org.junit.Test;

/** FM global overlay chip rules — 1 station NP, 2+ Queue. 2026-07-06 */
public class FmContextQuickBarLogicTest {

  /** Mirrors MainActivity.buildContextQuickBar singleTrack + opensQueueList. */
  private static boolean singleTrackChip(boolean fmActive, boolean hasQueue, int queueSize) {
    return (fmActive && queueSize <= 1) || (!fmActive && hasQueue && queueSize == 1);
  }

  private static boolean opensQueueList(boolean fmActive, int queueSize) {
    if (fmActive) return queueSize > 1;
    return queueSize > 1;
  }

  @Test
  public void fm_oneStation_showsNowPlayingChip() {
    if (!singleTrackChip(true, true, 1)) throw new AssertionError("NP chip");
    if (opensQueueList(true, 1)) throw new AssertionError("no queue tier");
  }

  @Test
  public void fm_multiStation_showsQueueChip() {
    if (singleTrackChip(true, true, 3)) throw new AssertionError("queue chip not single");
    if (!opensQueueList(true, 3)) throw new AssertionError("queue tier");
  }

  @Test
  public void music_singleTrack_unchanged() {
    if (!singleTrackChip(false, true, 1)) throw new AssertionError("music NP");
    if (singleTrackChip(false, true, 2)) throw new AssertionError("music queue");
  }
}
