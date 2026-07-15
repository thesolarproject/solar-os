package com.solar.launcher.radio.fm;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import com.solar.launcher.RootShell;

import java.util.Locale;

/**
 * 2026-07-06 — FM needs airplane mode off; restore user prefs when FM stops.
 * 2026-07-15 — Always force-off with Settings + root + broadcast (MTK /dev/fm blocked in flight).
 * 2026-07-15 — Force Wi‑Fi OFF for whole Solar FM session (RF); restore snapshot on exit.
 * Also arms for third-party packages with "FM" in the name (stock MTK, Innioasis, etc.).
 * Layman: turns off flight mode and Wi‑Fi for the radio, then puts them back how you had them.
 * Technical: session snapshot of airplane/Wi‑Fi/BT; multi-path setAirplaneMode; package matcher.
 * Reversal: delete third-party arm; only Solar/MTK explicit sessions; Settings-only put;
 * re-enable Wi‑Fi during session when airplane was previously on.
 */
public final class FmAirplaneModeHelper {
  private static final String TAG = "FmAirplane";

  /** Who opened FM — Solar chip vs external FM app. */
  public enum SessionOwner {
    NONE,
    SOLAR,
    /** Stock MTK FMRadio or any third-party package that looks like FM. */
    EXTERNAL_FM
  }

  /** Immutable connectivity snapshot at FM session start. */
  public static final class Snapshot {
    public final boolean airplaneWasOn;
    public final boolean wifiWasEnabled;
    public final boolean bluetoothWasEnabled;

    Snapshot(boolean airplaneWasOn, boolean wifiWasEnabled, boolean bluetoothWasEnabled) {
      this.airplaneWasOn = airplaneWasOn;
      this.wifiWasEnabled = wifiWasEnabled;
      this.bluetoothWasEnabled = bluetoothWasEnabled;
    }
  }

  private static final String KEY_AIRPLANE = "airplane_mode_on";
  private static final long RADIO_SETTLE_MS = 400L;

  private static volatile SessionOwner owner = SessionOwner.NONE;
  private static volatile Snapshot snapshot;

  private FmAirplaneModeHelper() {}

  /** True when an FM airplane session is active (Solar or external FM). */
  public static boolean isSessionActive() {
    return owner != SessionOwner.NONE;
  }

  /** Solar in-app FM — idempotent while session already open. */
  public static synchronized void beginSolarSession(Context ctx) {
    beginSession(ctx, SessionOwner.SOLAR);
  }

  /**
   * Stock MTK FM fallback or any third-party FM-named app.
   * 2026-07-15 — Was MTK-only name; same path for packages with FM in the name.
   */
  public static synchronized void beginMtkFallbackSession(Context ctx) {
    beginSession(ctx, SessionOwner.EXTERNAL_FM);
  }

  /** Alias for third-party / stock external FM apps. */
  public static synchronized void beginExternalFmSession(Context ctx) {
    beginSession(ctx, SessionOwner.EXTERNAL_FM);
  }

  /** End Solar FM session (power down, stop radio, tune failure rollback). */
  public static synchronized void endSolarSession(Context ctx) {
    endSession(ctx, SessionOwner.SOLAR);
  }

  /** End external FM when Solar regains window focus. */
  public static synchronized void endMtkFallbackSession(Context ctx) {
    endSession(ctx, SessionOwner.EXTERNAL_FM);
  }

  public static synchronized void endExternalFmSession(Context ctx) {
    endSession(ctx, SessionOwner.EXTERNAL_FM);
  }

  /**
   * 2026-07-15 — True when package looks like an FM radio app (user: “FM in the name”).
   * Layman: stock MediaTek FM, Innioasis FM, or any app package with fmradio / .fm.
   * Tech: lowercase package match; avoids messenger-style false positives.
   */
  public static boolean isFmLikePackage(String packageName) {
    if (packageName == null || packageName.length() == 0) return false;
    String p = packageName.toLowerCase(Locale.US);
    if ("com.mediatek.fmradio".equals(p) || "com.innioasis.fm".equals(p)
            || "com.android.fmradio".equals(p)) {
      return true;
    }
    if (p.contains("fmradio") || p.contains("fm_radio") || p.contains("fm-radio")) return true;
    if (p.contains(".fm.") || p.contains("-fm.") || p.contains("_fm.") || p.endsWith(".fm")) {
      return true;
    }
    // Last path segment starts with fm (com.foo.fmapp)
    int lastDot = p.lastIndexOf('.');
    if (lastDot >= 0 && lastDot + 1 < p.length()) {
      String last = p.substring(lastDot + 1);
      if (last.startsWith("fm") && last.length() <= 12) return true;
    }
    return false;
  }

  /**
   * When Solar loses focus to an FM-like app, open airplane session (no-op if already armed).
   * Call from handoff arm path.
   */
  public static void ensureSessionForForegroundPackage(Context ctx, String packageName) {
    if (ctx == null || !isFmLikePackage(packageName)) return;
    beginExternalFmSession(ctx);
  }

  /** Pure logic — restore airplane only when it was on at session start. */
  static boolean shouldRestoreAirplaneOnEnd(Snapshot snap) {
    return snap != null && snap.airplaneWasOn;
  }

  /**
   * 2026-07-15 — FM always wants Wi‑Fi off while the chip is live.
   * Pure: always true; snapshot still records prior Wi‑Fi for restore on exit.
   */
  static boolean shouldForceWifiOffDuringSession() {
    return true;
  }

  /** Pure logic — build snapshot from booleans (unit tests). */
  static Snapshot captureSnapshot(boolean airplaneOn, boolean wifiOn, boolean btOn) {
    return new Snapshot(airplaneOn, wifiOn, btOn);
  }

  private static synchronized void beginSession(Context ctx, SessionOwner requested) {
    if (ctx == null || requested == SessionOwner.NONE) return;
    Context app = ctx.getApplicationContext();
    // Already in a session: still force airplane off + Wi‑Fi off if OS flipped them back.
    if (owner != SessionOwner.NONE) {
      if (isAirplaneOn(app)) {
        Log.i(TAG, "session already open; force airplane off again");
        setAirplaneMode(app, false);
        settle();
      }
      if (shouldForceWifiOffDuringSession() && isWifiEnabled(app)) {
        applyWifi(app, false);
      }
      return;
    }
    snapshot = readSnapshot(app);
    owner = requested;
    // 2026-07-15 — Always force airplane OFF for FM (Y2 often boots with air=1; chip RF blocked).
    // Was: only when snapshot said on — left edge cases where settings said off but RF still down.
    boolean wasOn = snapshot != null && snapshot.airplaneWasOn;
    setAirplaneMode(app, false);
    settle();
    if (isAirplaneOn(app)) {
      setAirplaneMode(app, false);
      settle();
    }
    // 2026-07-15 — Keep Bluetooth if the user had it (headphones / A2DP); always kill Wi‑Fi.
    // Was: re-enabled Wi‑Fi when leaving airplane — dual audio / RF noise with FM chip.
    if (wasOn && snapshot != null && snapshot.bluetoothWasEnabled) {
      applyBluetooth(app, true);
    }
    if (shouldForceWifiOffDuringSession()) {
      applyWifi(app, false);
    }
    Log.i(TAG, "FM airplane session begin owner=" + requested
            + " wasOn=" + wasOn
            + " nowOn=" + isAirplaneOn(app)
            + " wifiForcedOff=" + shouldForceWifiOffDuringSession());
  }

  private static synchronized void endSession(Context ctx, SessionOwner requested) {
    if (ctx == null || owner != requested) return;
    Context app = ctx.getApplicationContext();
    Snapshot snap = snapshot;
    owner = SessionOwner.NONE;
    snapshot = null;
    if (snap == null) return;
    if (shouldRestoreAirplaneOnEnd(snap)) {
      setAirplaneMode(app, true);
      settle();
    }
    // Restore both radios to pre-FM snapshot (airplane restore may have killed them first).
    applyWifiBluetooth(app, snap.wifiWasEnabled, snap.bluetoothWasEnabled);
    Log.i(TAG, "FM airplane session end owner=" + requested
            + " restoredAirplane=" + shouldRestoreAirplaneOnEnd(snap)
            + " restoreWifi=" + snap.wifiWasEnabled
            + " restoreBt=" + snap.bluetoothWasEnabled);
  }

  private static Snapshot readSnapshot(Context ctx) {
    return captureSnapshot(isAirplaneOn(ctx), isWifiEnabled(ctx), isBluetoothEnabled(ctx));
  }

  private static boolean isAirplaneOn(Context ctx) {
    try {
      int v =
          Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
      return v != 0;
    } catch (Exception ignored) {}
    try {
      int v =
          Settings.System.getInt(ctx.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
      return v != 0;
    } catch (Exception ignored) {}
    return false;
  }

  @SuppressWarnings("deprecation")
  private static boolean isWifiEnabled(Context ctx) {
    try {
      WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
      return wm != null && wm.isWifiEnabled();
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean isBluetoothEnabled(Context ctx) {
    try {
      BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
      return ba != null && ba.isEnabled();
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * 2026-07-15 — Force airplane on/off via Settings API + root + sticky broadcast.
   * Layman: turn flight mode the way the phone UI would, plus a root kick if needed.
   * Was: Settings put only, root only if put failed — MTK often needs all three.
   */
  private static void setAirplaneMode(Context ctx, boolean on) {
    int value = on ? 1 : 0;
    try {
      Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, value);
    } catch (Exception ignored) {}
    try {
      Settings.System.putInt(ctx.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, value);
    } catch (Exception ignored) {}
    // Always try root — WRITE_SETTINGS may no-op without privilege on some ROMs.
    // 2026-07-15 — allowA5=true: FM chip needs airplane off even when general su is skipped on A5.
    RootShell.run(
        "settings put global " + KEY_AIRPLANE + " " + value
            + "; settings put system " + KEY_AIRPLANE + " " + value
            + "; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state "
            + (on ? "true" : "false"),
        true /* allowA5 */);
    broadcastAirplaneMode(ctx, on);
  }

  private static void broadcastAirplaneMode(Context ctx, boolean on) {
    try {
      Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
      intent.putExtra("state", on);
      ctx.sendBroadcast(intent);
    } catch (Exception ignored) {}
    // Non-root sticky broadcast already sent above via allowA5 su; app broadcast too.
  }

  @SuppressWarnings("deprecation")
  private static void applyWifiBluetooth(Context ctx, boolean wifiOn, boolean btOn) {
    applyWifi(ctx, wifiOn);
    applyBluetooth(ctx, btOn);
  }

  @SuppressWarnings("deprecation")
  private static void applyWifi(Context ctx, boolean wifiOn) {
    try {
      WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
      if (wm != null && wm.isWifiEnabled() != wifiOn) {
        wm.setWifiEnabled(wifiOn);
      }
    } catch (Exception ignored) {}
    // Root kick when WRITE_SETTINGS / setWifiEnabled no-ops on locked-down builds.
    try {
      RootShell.run("svc wifi " + (wifiOn ? "enable" : "disable"), true /* allowA5 */);
    } catch (Exception ignored) {}
  }

  private static void applyBluetooth(Context ctx, boolean btOn) {
    try {
      BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
      if (ba == null) return;
      if (btOn && !ba.isEnabled()) {
        ba.enable();
      } else if (!btOn && ba.isEnabled()) {
        ba.disable();
      }
    } catch (Exception ignored) {}
  }

  private static void settle() {
    try {
      Thread.sleep(RADIO_SETTLE_MS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
