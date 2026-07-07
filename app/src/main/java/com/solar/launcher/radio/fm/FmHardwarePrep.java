package com.solar.launcher.radio.fm;

import com.solar.launcher.RootShell;

/**
 * 2026-07-06 — Free FM chip (/dev/fm) before Solar opens it (JJ Launcher pattern).
 * Layman: kicks other radio apps off the hardware and unlocks the FM device node.
 * Technical: root killall competing FM processes + chmod 666 /dev/fm + 300ms settle.
 */
final class FmHardwarePrep {

  private static final long SETTLE_MS = 300L;

  private FmHardwarePrep() {}

  /** Run prep when root is available; no-op otherwise (caller may retry opendev). */
  static void prepareIfRooted() {
    if (!RootShell.canRun()) return;
    RootShell.runAsync("killall com.mediatek.FMRadio 2>/dev/null; "
        + "killall com.innioasis.fm 2>/dev/null; "
        + "killall com.android.fmradio 2>/dev/null; "
        + "chmod 666 /dev/fm 2>/dev/null");
    try {
      Thread.sleep(SETTLE_MS);
    } catch (InterruptedException ignored) {}
  }

  /** Blocking prep for power-up path (background thread only). */
  static void prepareBlocking() {
    if (!RootShell.canRun()) return;
    RootShell.run("killall com.mediatek.FMRadio 2>/dev/null; "
        + "killall com.innioasis.fm 2>/dev/null; "
        + "killall com.android.fmradio 2>/dev/null; "
        + "chmod 666 /dev/fm 2>/dev/null");
    try {
      Thread.sleep(SETTLE_MS);
    } catch (InterruptedException ignored) {}
  }
}
