package com.solar.launcher.radio.fm;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import com.solar.launcher.RootShell;

/**
 * 2026-07-06 — FM needs airplane mode off; restore user prefs when FM stops.
 * Layman: turns off flight mode for the radio, then puts it back how you had it.
 * Technical: session snapshot of airplane/Wi‑Fi/BT; Settings API + root + broadcast fallback.
 * Reversal: delete calls; FM works only when user disables airplane manually again.
 */
public final class FmAirplaneModeHelper {

  /** Who opened FM — Solar chip vs stock MTK app (different exit hooks). */
  public enum SessionOwner {
    NONE,
    SOLAR,
    MTK_FALLBACK
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
  private static final long RADIO_SETTLE_MS = 350L;

  private static volatile SessionOwner owner = SessionOwner.NONE;
  private static volatile Snapshot snapshot;

  private FmAirplaneModeHelper() {}

  /** True when an FM airplane session is active (Solar or MTK). */
  public static boolean isSessionActive() {
    return owner != SessionOwner.NONE;
  }

  /** Solar in-app FM — idempotent while session already open. */
  public static synchronized void beginSolarSession(Context ctx) {
    beginSession(ctx, SessionOwner.SOLAR);
  }

  /** Stock MTK FM fallback — Solar process stays alive in background. */
  public static synchronized void beginMtkFallbackSession(Context ctx) {
    beginSession(ctx, SessionOwner.MTK_FALLBACK);
  }

  /** End Solar FM session (power down, stop radio, tune failure rollback). */
  public static synchronized void endSolarSession(Context ctx) {
    endSession(ctx, SessionOwner.SOLAR);
  }

  /** End MTK fallback when Solar regains window focus. */
  public static synchronized void endMtkFallbackSession(Context ctx) {
    endSession(ctx, SessionOwner.MTK_FALLBACK);
  }

  /** Pure logic — restore airplane only when it was on at session start. */
  static boolean shouldRestoreAirplaneOnEnd(Snapshot snap) {
    return snap != null && snap.airplaneWasOn;
  }

  /** Pure logic — build snapshot from booleans (unit tests). */
  static Snapshot captureSnapshot(boolean airplaneOn, boolean wifiOn, boolean btOn) {
    return new Snapshot(airplaneOn, wifiOn, btOn);
  }

  private static synchronized void beginSession(Context ctx, SessionOwner requested) {
    if (ctx == null || requested == SessionOwner.NONE) return;
    if (owner != SessionOwner.NONE) return;
    Context app = ctx.getApplicationContext();
    snapshot = readSnapshot(app);
    owner = requested;
    if (snapshot.airplaneWasOn) {
      setAirplaneMode(app, false);
      settle();
      applyWifiBluetooth(app, snapshot.wifiWasEnabled, snapshot.bluetoothWasEnabled);
    }
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
    applyWifiBluetooth(app, snap.wifiWasEnabled, snap.bluetoothWasEnabled);
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

  private static void setAirplaneMode(Context ctx, boolean on) {
    int value = on ? 1 : 0;
    boolean applied = false;
    try {
      Settings.Global.putInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, value);
      applied = true;
    } catch (Exception ignored) {}
    if (!applied) {
      try {
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, value);
        applied = true;
      } catch (Exception ignored) {}
    }
    if (!applied) {
      RootShell.run("settings put global " + KEY_AIRPLANE + " " + value);
    }
    broadcastAirplaneMode(ctx, on);
  }

  private static void broadcastAirplaneMode(Context ctx, boolean on) {
    try {
      Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
      intent.putExtra("state", on);
      ctx.sendBroadcast(intent);
    } catch (Exception ignored) {}
    if (RootShell.canRun()) {
      RootShell.runAsync(
          "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state "
              + (on ? "true" : "false"));
    }
  }

  @SuppressWarnings("deprecation")
  private static void applyWifiBluetooth(Context ctx, boolean wifiOn, boolean btOn) {
    try {
      WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
      if (wm != null && wm.isWifiEnabled() != wifiOn) {
        wm.setWifiEnabled(wifiOn);
      }
    } catch (Exception ignored) {}
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
    } catch (InterruptedException ignored) {}
  }
}
