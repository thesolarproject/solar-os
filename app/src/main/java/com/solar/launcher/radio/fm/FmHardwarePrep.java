package com.solar.launcher.radio.fm;

import android.os.Process;
import android.util.Log;

import com.solar.launcher.RootShell;

/**
 * 2026-07-15 — Root-owned claim of the MTK FM chip before Solar opens it.
 * Layman: with root we always free the radio for the user — kill whatever holds it, unlock
 * the device node, clear flight mode, then Solar can play.
 * Technical: allowA5 su; never kill our own PID; fuser -k other holders of /dev/fm;
 * force-stop FM packages (static + pm list); chmod 666 (Y1 ships 660 system:media);
 * airplane 0; verify -rw. Device probe: Y1 = crw-rw---- until claim; Y2 often airplane=1.
 * Was: soft killall + canRun() skipped A5 → “hardware busy” everywhere; hardFree could
 * SIGKILL Solar if we re-claimed while holding the fd.
 * Reversal: PREP_CMD killall-only; RootShell without allowA5; no self-PID guard.
 */
final class FmHardwarePrep {
  private static final String TAG = "FmHardwarePrep";
  private static final long SETTLE_MS = 400L;

  private static volatile String lastDiag = "";

  private FmHardwarePrep() {}

  /** Last root prep stdout for logcat / adb tests. */
  static String lastDiag() {
    return lastDiag != null ? lastDiag : "";
  }

  /**
   * Build root claim script. Injects app PID so we never fuser-kill ourselves.
   * Order: free holders (not us) → force-stop FM apps → chmod 666 → airplane off → verify.
   */
  private static String claimCmd() {
    int self = Process.myPid();
    // BusyBox fuser on Y1/Y2: -k kills; no -v. $$ under su is the shell, not the app — use self.
    return ""
        // 1) Kill OTHER processes with /dev/fm open — never fuser -k (would SIGKILL Solar too).
        + "SELF=" + self + "; "
        + "for p in $(fuser /dev/fm 2>/dev/null) $(busybox fuser /dev/fm 2>/dev/null) "
        + "$(fuser /dev/radio 2>/dev/null); do "
        + "  [ -z \"$p\" ] && continue; "
        + "  [ \"$p\" = \"$SELF\" ] && continue; "
        + "  kill -9 \"$p\" 2>/dev/null; "
        + "done; "
        // 2) Stop stock / vendor FM apps (known packages + anything with fmradio in name)
        + "am force-stop com.mediatek.FMRadio 2>/dev/null; "
        + "am force-stop com.innioasis.fm 2>/dev/null; "
        + "am force-stop com.android.fmradio 2>/dev/null; "
        + "am force-stop com.mediatek.fmradio 2>/dev/null; "
        + "for pkg in $(pm list packages 2>/dev/null | sed 's/^package://' | grep -iE 'fmradio|\\.fm$|\\.fm\\.|_fm\\.|-fm\\.'); do "
        + "  case \"$pkg\" in com.solar.*|com.solar.launcher*) continue;; esac; "
        + "  am force-stop \"$pkg\" 2>/dev/null; "
        + "done; "
        // 3) Process-name leftovers (not Solar)
        + "killall -9 com.mediatek.FMRadio 2>/dev/null; "
        + "killall -9 com.innioasis.fm 2>/dev/null; "
        + "killall -9 com.android.fmradio 2>/dev/null; "
        + "busybox pkill -9 -f 'com.mediatek.FMRadio' 2>/dev/null; "
        + "pkill -9 -f 'com.mediatek.FMRadio' 2>/dev/null; "
        // 4) Unlock node for launcher UID (Y1 boots as 660 system:media → app EACCES)
        + "chmod 666 /dev/fm 2>/dev/null; "
        + "chmod 666 /dev/radio 2>/dev/null; "
        + "chown system:media /dev/fm 2>/dev/null; "
        + "chmod 666 /dev/fm 2>/dev/null; "
        // 5) FM driver enable props (MTK)
        + "setprop fmradio.driver.enable 1 2>/dev/null; "
        + "setprop persist.radio.fm.enable 1 2>/dev/null; "
        // 6) Flight mode off (chip RF blocked while on — Y2 often stuck at 1)
        + "settings put global airplane_mode_on 0 2>/dev/null; "
        + "settings put system airplane_mode_on 0 2>/dev/null; "
        + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false 2>/dev/null; "
        // 7) Echo state for log capture + cheap verify
        + "echo CLAIM_DONE self=$SELF; "
        + "ls -l /dev/fm 2>/dev/null; "
        + "settings get global airplane_mode_on 2>/dev/null; "
        + "if [ -r /dev/fm ] && [ -w /dev/fm ]; then echo CLAIM_RW_OK; else echo CLAIM_RW_FAIL; fi; "
        + "fuser /dev/fm 2>/dev/null; echo HOLDERS_END";
  }

  /** Lightweight re-assert right before opendev (no force-stop — fast). */
  private static String softUnlockCmd() {
    return "chmod 666 /dev/fm 2>/dev/null; "
        + "chmod 666 /dev/radio 2>/dev/null; "
        + "settings put global airplane_mode_on 0 2>/dev/null; "
        + "settings put system airplane_mode_on 0 2>/dev/null; "
        + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false 2>/dev/null; "
        + "echo SOFT_UNLOCK; ls -l /dev/fm 2>/dev/null; "
        + "settings get global airplane_mode_on 2>/dev/null";
  }

  /** Fire-and-forget claim (UI thread). */
  static void prepareIfRooted() {
    RootShell.runAsync(claimCmd(), true /* allowA5 */);
    settle(SETTLE_MS);
  }

  /**
   * Blocking claim for power-up (background thread only).
   * 2026-07-15 — JJ-first: killall + chmod 666 + 300ms (proven Y1), then full claim if needed.
   * Layman: free the radio the simple way JJ does, then hammer if still locked.
   * @return true when root left CLAIM_DONE / CLAIM_RW_OK or exited 0 at least once
   */
  static boolean prepareBlocking() {
    lastDiag = "";
    // JJ powerUp prep: kill stock FM apps + chmod 666 /dev/fm + short settle.
    String jj =
        "killall com.mediatek.FMRadio 2>/dev/null; "
            + "killall com.innioasis.fm 2>/dev/null; "
            + "killall com.android.fmradio 2>/dev/null; "
            + "am force-stop com.mediatek.FMRadio 2>/dev/null; "
            + "chmod 666 /dev/fm 2>/dev/null; "
            + "settings put global airplane_mode_on 0 2>/dev/null; "
            + "echo JJ_CLAIM; ls -l /dev/fm 2>/dev/null";
    boolean okJj = RootShell.run(jj, true /* allowA5 */);
    String jjCap = RootShell.runCapture(jj, true);
    if (jjCap != null && jjCap.length() > 0) lastDiag = jjCap;
    settle(300L); // JJ Thread.sleep(300)

    String cmd = claimCmd();
    boolean ok1 = RootShell.run(cmd, true /* allowA5 */);
    String cap = RootShell.runCapture(cmd, true /* allowA5 */);
    if (cap != null && cap.length() > 0) {
      lastDiag = (lastDiag == null || lastDiag.isEmpty()) ? cap : (lastDiag + "\n" + cap);
    }
    settle(SETTLE_MS);
    String soft = RootShell.runCapture(softUnlockCmd(), true);
    if (soft != null && soft.length() > 0) {
      lastDiag = (lastDiag == null || lastDiag.isEmpty()) ? soft : (lastDiag + "\n" + soft);
    }
    settle(100L);
    boolean claimed =
        okJj
            || ok1
            || (lastDiag != null
                && (lastDiag.contains("CLAIM_DONE")
                    || lastDiag.contains("CLAIM_RW_OK")
                    || lastDiag.contains("JJ_CLAIM")
                    || lastDiag.contains("SOFT_UNLOCK")));
    Log.i(
        TAG,
        "prepareBlocking jj="
            + okJj
            + " ok1="
            + ok1
            + " claimed="
            + claimed
            + " diag="
            + truncate(lastDiag, 160));
    return claimed;
  }

  /**
   * Fast re-assert of chmod + airplane immediately before each opendev attempt.
   * Layman: make sure the radio node is still openable and flight mode is still off.
   */
  static void softUnlockBlocking() {
    String cap = RootShell.runCapture(softUnlockCmd(), true /* allowA5 */);
    if (cap != null && cap.length() > 0) {
      lastDiag = (lastDiag == null || lastDiag.isEmpty()) ? cap : (lastDiag + "\n" + cap);
    }
    settle(80L);
  }

  /**
   * Emergency free when opendev still fails after prepareBlocking.
   * Layman: last-ditch “kick everyone else off the radio” — never kill Solar.
   */
  static void hardFreeBlocking() {
    int self = Process.myPid();
    String hard =
        "SELF="
            + self
            + "; "
            // List-then-kill holders except us (safer than blind fuser -k on some busybox builds)
            + "for p in $(fuser /dev/fm 2>/dev/null) $(busybox fuser /dev/fm 2>/dev/null); do "
            + "  [ -z \"$p\" ] && continue; "
            + "  [ \"$p\" = \"$SELF\" ] && continue; "
            + "  kill -9 \"$p\" 2>/dev/null; "
            + "done; "
            // Slow path: scan /proc fds (skip self, kernel threads, low PIDs that are init)
            + "for p in $(ls /proc 2>/dev/null); do "
            + "  case \"$p\" in *[!0-9]*|\"$SELF\") continue;; esac; "
            + "  [ \"$p\" -lt 100 ] 2>/dev/null && continue; "
            + "  [ -d /proc/$p/fd ] || continue; "
            + "  ls -l /proc/$p/fd 2>/dev/null | grep -q '/dev/fm' && kill -9 $p 2>/dev/null; "
            + "done; "
            + "am force-stop com.mediatek.FMRadio 2>/dev/null; "
            + "am force-stop com.innioasis.fm 2>/dev/null; "
            + "am force-stop com.android.fmradio 2>/dev/null; "
            + "chmod 666 /dev/fm 2>/dev/null; "
            + "chmod 666 /dev/radio 2>/dev/null; "
            + "settings put global airplane_mode_on 0 2>/dev/null; "
            + "settings put system airplane_mode_on 0 2>/dev/null; "
            + "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false 2>/dev/null; "
            + "echo HARD_FREE_DONE self=$SELF; ls -l /dev/fm 2>/dev/null; "
            + "settings get global airplane_mode_on 2>/dev/null; "
            + "if [ -r /dev/fm ] && [ -w /dev/fm ]; then echo CLAIM_RW_OK; else echo CLAIM_RW_FAIL; fi";
    String cap = RootShell.runCapture(hard, true);
    if (cap != null && cap.length() > 0) lastDiag = cap;
    settle(SETTLE_MS);
    Log.i(TAG, "hardFreeBlocking diag=" + truncate(lastDiag, 180));
  }

  private static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() > max ? s.substring(0, max) : s;
  }

  private static void settle(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
