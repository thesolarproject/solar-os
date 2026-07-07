package com.solar.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 2026-07-06 — RDS PS should update FM queue row title. */
public class PlaybackCoordinatorFmMetaTest {

  @Test
  public void updateCurrentFmMeta_replacesQueueTitle() {
    PlaybackCoordinator pc = new PlaybackCoordinator();
    pc.startRadioStation(PlayQueue.QueueItem.fmStation(101100, "101.1"));
    pc.updateCurrentFmMeta(101100, "KEXP-FM");
    PlayQueue.QueueItem cur = pc.unifiedQueue().current();
    if (cur == null) throw new AssertionError("no current");
    assertEquals("KEXP-FM", cur.streamMeta());
  }
}
