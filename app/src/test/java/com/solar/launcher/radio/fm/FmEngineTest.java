package com.solar.launcher.radio.fm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FmEngineTest {

  @Test
  public void vendorShortToKhz_mtk875_is87500() {
    assertEquals(87500, FmEngine.vendorShortToKhz((short) 875));
  }

  @Test
  public void vendorShortToKhz_mtk1011_is101100() {
    assertEquals(101100, FmEngine.vendorShortToKhz((short) 1011));
  }

  @Test
  public void jjImport_parsesCsv() {
    java.util.List<Integer> freqs = FmJjPresetImport.parseMhzCsv("87.5,101.1,98.3");
    assertEquals(3, freqs.size());
    assertEquals(87500, (int) freqs.get(0));
    assertEquals(101100, (int) freqs.get(1));
    assertEquals(98300, (int) freqs.get(2));
  }

  @Test
  public void nextBandStepKhz_wrapsAtBandEdge() {
    com.solar.launcher.radio.FmBandPlan us = com.solar.launcher.radio.FmBandPlan.fromRegionCode("US");
    int atMax = us.maxKhz();
    assertEquals(us.minKhz(), FmEngine.nextBandStepKhz(atMax, true, us));
    assertEquals(us.maxKhz(), FmEngine.nextBandStepKhz(us.minKhz(), false, us));
  }

  @Test
  public void jjImport_emptyCsv() {
    assertTrue(FmJjPresetImport.parseMhzCsv("").isEmpty());
  }
}
