package com.solar.launcher.radio;

/** Wheel scrub context — FM tuning vs rewind buffer vs inactive. */
public enum RadioScrubMode {
  NONE,
  TUNE_FM,
  REWIND_BUFFER;

  /** 2026-07-06 — FM NP single OK: enter MHz tune; second OK or Back exits to volume wheel. */
  public RadioScrubMode toggleFmTuneOnCenterOk() {
    return this == TUNE_FM ? NONE : TUNE_FM;
  }
}
