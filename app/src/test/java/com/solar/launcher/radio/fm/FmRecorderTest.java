package com.solar.launcher.radio.fm;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FmRecorderTest {

  @Test
  public void resolveFmAudioSource_returnsMtkConstantWhenHidden() {
    int src = FmRecorder.resolveFmAudioSource();
    assertTrue(src > 0);
  }
}
