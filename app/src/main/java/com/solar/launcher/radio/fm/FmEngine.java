package com.solar.launcher.radio.fm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 2026-07-06 — FM hardware facade: native JNI (JJ path) with optional MTK service fallback.
 * Layman: powers the radio chip, plays sound, tunes stations.
 * Technical: FMRadioNative + FmAudioRouter; AIDL bind only when native class missing.
 */
public final class FmEngine {
  private static final String FM_PACKAGE = "com.mediatek.FMRadio";
  private static final String FM_SERVICE = "com.mediatek.FMRadio.FMRadioService";
  private static final String FM_SERVICE_ACTION = "com.mediatek.FMRadio.IFMRadioService";
  private static final String PREF_ENGINE_MODE = "solar_fm_engine_mode";
  private static final String MODE_SERVICE = "service";
  private static final long BIND_TIMEOUT_MS = 5000L;

  public interface ScanCallback {
    void onStationFound(int freqKhz, int signal, boolean stereo);

    void onScanComplete();

    void onError(String reason);
  }

  private final Context appCtx;
  private final FmNativeLoader nativeLoader;
  private final FmAudioRouter audioRouter;
  private final boolean useServiceFallback;

  private boolean deviceOpen;
  private boolean powerUp;
  private float currentFreqMhz = 87.5f;
  private String lastError = "";

  private Object fmService;
  private ServiceConnection connection;
  private boolean bound;
  private volatile boolean scanCancelled;

  public FmEngine(Context ctx) {
    appCtx = ctx.getApplicationContext();
    nativeLoader = new FmNativeLoader(appCtx);
    audioRouter = new FmAudioRouter(appCtx);
    SharedPreferences prefs = appCtx.getSharedPreferences("radio_settings", Context.MODE_PRIVATE);
    useServiceFallback =
        MODE_SERVICE.equals(prefs.getString(PREF_ENGINE_MODE, ""))
            || !nativeLoader.isReady();
    if (!nativeLoader.isReady() && lastError.isEmpty()) {
      lastError = nativeLoader.loadError();
    }
  }

  /** True when MTK FM package is installed (native or service path). */
  public boolean isAvailable() {
    return nativeLoader.isReady() || isFmPackageInstalled();
  }

  public boolean isPowerUp() {
    return powerUp;
  }

  public float currentFreqMhz() {
    return currentFreqMhz;
  }

  public int currentFreqKhz() {
    return Math.round(currentFreqMhz * 1000f);
  }

  public String lastError() {
    return lastError != null ? lastError : "";
  }

  public boolean isSpeakerOn() {
    return audioRouter.isSpeakerOn();
  }

  public void setSpeaker(boolean useSpeaker) {
    audioRouter.setSpeaker(useSpeaker);
  }

  /**
   * Open device, power up, route audio, and tune — full FM playback start.
   * @return false when prep, native, or audio routing fails
   */
  public synchronized boolean playStation(int freqKhz) {
    lastError = "";
    // 2026-07-06 — FM chip blocked while airplane mode is on (MTK /dev/fm).
    FmAirplaneModeHelper.beginSolarSession(appCtx);
    float mhz = khzToMhz(freqKhz);
    boolean ok;
    if (useServiceFallback) {
      ok = playStationViaService(freqKhz, mhz);
    } else {
      try {
        ok = powerUpInternal(mhz) && tuneInternal(mhz);
      } catch (Throwable t) {
        lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
        ok = false;
      }
    }
    if (!ok) {
      FmAirplaneModeHelper.endSolarSession(appCtx);
    }
    return ok;
  }

  public synchronized boolean openDevice() {
    if (useServiceFallback) {
      return ensureServiceBound() && invokeServiceBool("openDevice");
    }
    return openDeviceNative();
  }

  public synchronized boolean powerUp(int freqKhz) {
    if (useServiceFallback) {
      float mhz = khzToMhz(freqKhz);
      if (!ensureServiceBound() || !invokeServiceBool("openDevice")) return false;
      return invokeServiceBool("powerUp", float.class, mhz);
    }
    return powerUpInternal(khzToMhz(freqKhz));
  }

  public synchronized boolean powerDown() {
    scanCancelled = true;
    boolean ok = true;
    if (useServiceFallback) {
      ok = invokeServiceBool("powerDown");
      unbindServiceQuiet();
      powerUp = false;
      deviceOpen = false;
    } else if (nativeLoader.isReady() && powerUp) {
      try {
        audioRouter.stop();
        setMuteNative(true);
        nativeLoader.invokeStatic("powerdown", new Class<?>[] {int.class}, new Object[] {0});
        nativeLoader.invokeStatic("closedev", new Class<?>[] {}, new Object[] {});
      } catch (Throwable ignored) {}
      powerUp = false;
      deviceOpen = false;
    }
    // 2026-07-06 — Restore airplane/Wi‑Fi/BT snapshot when FM powers down.
    FmAirplaneModeHelper.endSolarSession(appCtx);
    return ok;
  }

  public synchronized boolean tune(int freqKhz) {
    float mhz = khzToMhz(freqKhz);
    if (useServiceFallback) {
      return invokeServiceBool("tune", float.class, mhz);
    }
    return tuneInternal(mhz);
  }

  public synchronized boolean mute(boolean muted) {
    if (useServiceFallback) {
      Object result = invokeService("setMute", boolean.class, muted);
      if (result instanceof Integer) return ((Integer) result) == 0;
      if (result instanceof Boolean) return (Boolean) result;
      return result != null;
    }
    setMuteNative(muted);
    return true;
  }

  public synchronized boolean startScan(final ScanCallback callback) {
    scanCancelled = false;
    if (useServiceFallback) {
      return startScanViaService(callback);
    }
    if (!powerUp) {
      if (callback != null) callback.onError("Turn on the radio first.");
      return false;
    }
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                try {
                  short[] raw =
                      (short[]) nativeLoader.invokeStatic("autoscan", new Class<?>[] {}, new Object[] {});
                  if (scanCancelled) return;
                  if (raw == null || raw.length == 0) {
                    if (callback != null) {
                      callback.onError(lastError.isEmpty() ? "No stations found" : lastError);
                      callback.onScanComplete();
                    }
                    return;
                  }
                  for (short s : raw) {
                    if (scanCancelled) return;
                    int khz = vendorShortToKhz(s);
                    if (callback != null) callback.onStationFound(khz, 0, false);
                  }
                  if (callback != null) callback.onScanComplete();
                } catch (Throwable t) {
                  lastError = "AutoScan failed: " + t.getMessage();
                  if (callback != null) callback.onError(lastError);
                }
              }
            },
            "FmAutoScan")
        .start();
    return true;
  }

  public synchronized void stopScan() {
    scanCancelled = true;
    if (useServiceFallback) {
      invokeService("stopScan");
      invokeService("cancelScan");
      return;
    }
    try {
      nativeLoader.invokeStatic("stopscan", new Class<?>[] {}, new Object[] {});
    } catch (Throwable ignored) {}
  }

  /** Program service name from RDS, or null when unavailable. */
  public synchronized String getRdsPs() {
    if (useServiceFallback) return getRdsPsFromService();
    return bytesToString(invokeNativeBytes("getPS"));
  }

  /** Radio text from RDS. */
  public synchronized String getRdsRt() {
    if (useServiceFallback) return getRdsRtFromService();
    String rt = bytesToString(invokeNativeBytes("getLRText"));
    if (rt != null) return rt;
    return bytesToString(invokeNativeBytes("getRT"));
  }

  /**
   * 2026-07-06 — FM signal strength from chip; 0 when unavailable.
   * Layman: how loud the station is on the dial.
   * Technical: native getRssi / service getRssi reflection.
   */
  public synchronized int getRssi() {
    if (!powerUp) return 0;
    if (useServiceFallback) {
      Object r = invokeService("getRssi");
      if (r == null) r = invokeService("getRSSI");
      if (r instanceof Integer) return (Integer) r;
    }
    Integer r = invokeNativeInt("getRssi");
    if (r != null) return r;
    r = invokeNativeInt("getrssi");
    return r != null ? r : 0;
  }

  /**
   * 2026-07-06 — Car-stereo seek: hardware step first, else band walk with RSSI/RDS.
   * Layman: jump to the next station that actually comes in.
   * Technical: JNI seek(float,bool) then stepped tune until signal or band wrap.
   *
   * @return tuned kHz, or -1 on failure
   */
  public synchronized int seekStationKhz(int fromKhz, boolean forward, com.solar.launcher.radio.FmBandPlan plan) {
    if (!powerUp || plan == null) return -1;
    Integer hw = tryHardwareSeek(forward);
    if (hw != null && hw > 0) {
      int k = plan.clampKhz(hw);
      currentFreqMhz = k / 1000f;
      return k;
    }
    return seekByBandWalk(fromKhz, forward, plan);
  }

  /** Next/prev grid MHz on the band plan — wraps at ends (unit-testable). 2026-07-06 */
  static int nextBandStepKhz(int khz, boolean forward, com.solar.launcher.radio.FmBandPlan plan) {
    int step = plan.stepKhz();
    int next = forward ? khz + step : khz - step;
    if (next > plan.maxKhz()) next = plan.minKhz();
    if (next < plan.minKhz()) next = plan.maxKhz();
    return plan.clampKhz(next);
  }

  public synchronized void release() {
    stopScan();
    powerDown();
    unbindServiceQuiet();
  }

  // --- Native path ---

  private boolean powerUpInternal(float mhz) {
    if (!nativeLoader.isReady()) {
      lastError = "FMRadioNative driver missing";
      return false;
    }
    if (powerUp && Math.abs(currentFreqMhz - mhz) < 0.001f) {
      return true;
    }
    if (powerUp) {
      if (!tuneInternal(mhz)) return false;
      return true;
    }
    FmHardwarePrep.prepareBlocking();
    try {
      try {
        nativeLoader.invokeStatic("closedev", new Class<?>[] {}, new Object[] {});
      } catch (Throwable ignored) {}
      deviceOpen = false;

      Boolean opened = (Boolean) nativeLoader.invokeStatic("opendev", new Class<?>[] {}, new Object[] {});
      deviceOpen = opened != null && opened;
      if (!deviceOpen) {
        FmHardwarePrep.prepareBlocking();
        opened = (Boolean) nativeLoader.invokeStatic("opendev", new Class<?>[] {}, new Object[] {});
        deviceOpen = opened != null && opened;
      }
      if (!deviceOpen) {
        lastError = "Failed to open /dev/fm (hardware busy or blocked)";
        return false;
      }

      Boolean powered =
          (Boolean) nativeLoader.invokeStatic("powerup", new Class<?>[] {float.class}, new Object[] {mhz});
      powerUp = powered != null && powered;
      if (!powerUp) {
        try {
          nativeLoader.invokeStatic("switchAntenna", new Class<?>[] {int.class}, new Object[] {1});
          powered =
              (Boolean) nativeLoader.invokeStatic("powerup", new Class<?>[] {float.class}, new Object[] {mhz});
          powerUp = powered != null && powered;
        } catch (Throwable ex) {
          lastError = "Earphones required; antenna bypass failed.";
          return false;
        }
      }
      if (!powerUp) {
        lastError = "Power up rejected by hardware.";
        return false;
      }
      currentFreqMhz = mhz;
      setMuteNative(false);
      try {
        nativeLoader.invokeStatic("rdsset", new Class<?>[] {boolean.class}, new Object[] {true});
      } catch (Throwable ignored) {}
      audioRouter.start();
      return true;
    } catch (Throwable t) {
      lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
      return false;
    }
  }

  private boolean tuneInternal(float mhz) {
    if (!powerUp) {
      return powerUpInternal(mhz);
    }
    try {
      Boolean ok =
          (Boolean) nativeLoader.invokeStatic("tune", new Class<?>[] {float.class}, new Object[] {mhz});
      if (ok != null && ok) {
        currentFreqMhz = mhz;
        return true;
      }
      lastError = "Tune failed";
      return false;
    } catch (Throwable t) {
      lastError = t.getMessage();
      return false;
    }
  }

  private boolean openDeviceNative() {
    try {
      Boolean ok = (Boolean) nativeLoader.invokeStatic("opendev", new Class<?>[] {}, new Object[] {});
      deviceOpen = ok != null && ok;
      return deviceOpen;
    } catch (Throwable t) {
      lastError = t.getMessage();
      return false;
    }
  }

  private void setMuteNative(boolean mute) {
    try {
      nativeLoader.invokeStatic("setmute", new Class<?>[] {boolean.class}, new Object[] {mute});
    } catch (Throwable ignored) {}
  }

  private byte[] invokeNativeBytes(String name) {
    if (!powerUp || !nativeLoader.isReady()) return null;
    try {
      return (byte[]) nativeLoader.invokeStatic(name, new Class<?>[] {}, new Object[] {});
    } catch (Throwable ignored) {
      return null;
    }
  }

  private Integer invokeNativeInt(String name) {
    if (!powerUp || !nativeLoader.isReady()) return null;
    try {
      Object r = nativeLoader.invokeStatic(name, new Class<?>[] {}, new Object[] {});
      if (r instanceof Integer) return (Integer) r;
      if (r instanceof Short) return (int) (Short) r;
      if (r instanceof Byte) return (int) (Byte) r;
    } catch (Throwable ignored) {}
    return null;
  }

  /** MTK/JJ hardware seek — null when method missing or seek failed. 2026-07-06 */
  private Integer tryHardwareSeek(boolean forward) {
    if (useServiceFallback) {
      Object r =
          invokeService(
              "seek",
              new Class<?>[] {float.class, boolean.class},
              new Object[] {currentFreqMhz, forward});
      if (r instanceof Float) {
        float mhz = (Float) r;
        if (mhz > 0f) return Math.round(mhz * 1000f);
      }
      if (r instanceof Integer) {
        int k = (Integer) r;
        if (k > 10000) return k;
        if (k > 0) return k * 100;
      }
      return null;
    }
    if (!nativeLoader.isReady()) return null;
    try {
      Object r =
          nativeLoader.invokeStatic(
              "seek",
              new Class<?>[] {float.class, boolean.class},
              new Object[] {currentFreqMhz, forward});
      if (r instanceof Float) {
        float mhz = (Float) r;
        if (mhz > 0f) return Math.round(mhz * 1000f);
      }
    } catch (Throwable ignored) {}
    try {
      String method = forward ? "seekforward" : "seekbackward";
      Object r = nativeLoader.invokeStatic(method, new Class<?>[] {}, new Object[] {});
      if (r instanceof Float) {
        float mhz = (Float) r;
        if (mhz > 0f) return Math.round(mhz * 1000f);
      }
      if (r instanceof Boolean && (Boolean) r) {
        return currentFreqKhz();
      }
    } catch (Throwable ignored) {}
    return null;
  }

  /** Step the band until RSSI/RDS says a station is present — bounded by timeout. 2026-07-06 */
  private int seekByBandWalk(int startKhz, boolean forward, com.solar.launcher.radio.FmBandPlan plan) {
    final long deadline = android.os.SystemClock.elapsedRealtime() + 8000L;
    int khz = plan.clampKhz(startKhz);
    int steps = ((plan.maxKhz() - plan.minKhz()) / plan.stepKhz()) + 2;
    int baseline = getRssi();
    for (int i = 0; i < steps && android.os.SystemClock.elapsedRealtime() < deadline; i++) {
      khz = nextBandStepKhz(khz, forward, plan);
      if (!tune(khz)) continue;
      try {
        Thread.sleep(55L);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        break;
      }
      int rssi = getRssi();
      if (rssi >= 2 && rssi > baseline) return khz;
      if (rssi >= 8) return khz;
      String ps = getRdsPs();
      if (ps != null && !ps.isEmpty()) return khz;
      if (khz == startKhz && i > 0) break;
    }
    int fallback = nextBandStepKhz(startKhz, forward, plan);
    tune(fallback);
    return fallback;
  }

  // --- Service fallback (debug / missing native) ---

  private boolean playStationViaService(int freqKhz, float mhz) {
    if (!ensureServiceBound()) {
      lastError = "FM service not bound";
      return false;
    }
    if (!invokeServiceBool("openDevice")) {
      lastError = "openDevice failed";
      return false;
    }
    boolean powered = invokeServiceBool("isPowerUp");
    boolean ok;
    if (powered) {
      ok = invokeServiceBool("tune", float.class, mhz);
    } else {
      ok = invokeServiceBool("powerUp", float.class, mhz);
    }
    if (ok) {
      currentFreqMhz = mhz;
      powerUp = true;
    }
    return ok;
  }

  private boolean startScanViaService(final ScanCallback callback) {
    if (!ensureServiceBound()) {
      if (callback != null) callback.onError("FM service not bound");
      return false;
    }
    Object result = invokeService("startScan");
    if (result instanceof int[]) {
      int[] freqs = (int[]) result;
      if (callback != null) {
        for (int f : freqs) {
          callback.onStationFound(freqFromVendor(f), 0, false);
        }
        callback.onScanComplete();
      }
      return true;
    }
    if (invokeServiceBool("autoScan")) return true;
    if (callback != null) callback.onError("scan not supported");
    return false;
  }

  private String getRdsPsFromService() {
    Object ps = invokeService("getPS");
    if (ps instanceof String) {
      String s = ((String) ps).trim();
      return s.isEmpty() ? null : s;
    }
    ps = invokeService("getPs");
    if (ps instanceof String) {
      String s = ((String) ps).trim();
      return s.isEmpty() ? null : s;
    }
    return null;
  }

  private String getRdsRtFromService() {
    Object rt = invokeService("getRadioText");
    if (rt instanceof String) {
      String s = ((String) rt).trim();
      return s.isEmpty() ? null : s;
    }
    rt = invokeService("getRT");
    if (rt instanceof String) {
      String s = ((String) rt).trim();
      return s.isEmpty() ? null : s;
    }
    return null;
  }

  private boolean isFmPackageInstalled() {
    try {
      appCtx.getPackageManager().getPackageInfo(FM_PACKAGE, 0);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static float khzToMhz(int freqKhz) {
    return freqKhz / 1000f;
  }

  /** MTK autoscan short (875) → kHz (87500); JJ divides by 10 for MHz. */
  static int vendorShortToKhz(short vendor) {
    if (vendor > 10000) return vendor;
    return vendor * 100;
  }

  static int freqFromVendor(int vendorFreq) {
    return vendorShortToKhz((short) vendorFreq);
  }

  private static String bytesToString(byte[] raw) {
    if (raw == null || raw.length == 0) return null;
    try {
      String s = new String(raw, "UTF-8").trim();
      return s.isEmpty() ? null : s;
    } catch (Exception e) {
      return null;
    }
  }

  private boolean ensureServiceBound() {
    if (fmService != null) return true;
    if (Looper.myLooper() == Looper.getMainLooper()) {
      final boolean[] ok = new boolean[] {false};
      final CountDownLatch done = new CountDownLatch(1);
      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  ok[0] = ensureServiceBoundBlocking();
                  done.countDown();
                }
              },
              "FmEngineBind")
          .start();
      try {
        done.await(BIND_TIMEOUT_MS + 500L, TimeUnit.MILLISECONDS);
      } catch (InterruptedException ignored) {}
      return ok[0];
    }
    return ensureServiceBoundBlocking();
  }

  private boolean ensureServiceBoundBlocking() {
    if (fmService != null) return true;
    synchronized (this) {
      if (fmService != null) return true;
      final CountDownLatch latch = new CountDownLatch(1);
      connection =
          new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
              try {
                Context fmCtx =
                    appCtx.createPackageContext(
                        FM_PACKAGE, Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                Class<?> stub =
                    Class.forName(
                        "com.mediatek.FMRadio.IFMRadioService$Stub", true, fmCtx.getClassLoader());
                Method asInterface = stub.getMethod("asInterface", IBinder.class);
                fmService = asInterface.invoke(null, binder);
                bound = true;
              } catch (Exception e) {
                fmService = null;
              } finally {
                latch.countDown();
              }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
              fmService = null;
              bound = false;
            }
          };
      try {
        Intent intent = new Intent(FM_SERVICE_ACTION);
        intent.setComponent(new ComponentName(FM_PACKAGE, FM_SERVICE));
        boolean requested = appCtx.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!requested) {
          connection = null;
          return false;
        }
        latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        return false;
      }
      return fmService != null;
    }
  }

  private void unbindServiceQuiet() {
    if (bound && connection != null) {
      try {
        appCtx.unbindService(connection);
      } catch (Exception ignored) {}
      bound = false;
      connection = null;
      fmService = null;
    }
  }

  private Object invokeService(String method, Class<?> type, Object arg) {
    return invokeService(method, new Class<?>[] {type}, new Object[] {arg});
  }

  private Object invokeService(String method) {
    return invokeService(method, new Class<?>[] {}, new Object[] {});
  }

  private Object invokeService(String method, Class<?>[] types, Object[] args) {
    if (!ensureServiceBound() || fmService == null) return null;
    try {
      Method m = fmService.getClass().getMethod(method, types);
      return m.invoke(fmService, args);
    } catch (Exception e) {
      return null;
    }
  }

  private boolean invokeServiceBool(String method) {
    return invokeServiceBool(method, new Class<?>[] {}, new Object[] {});
  }

  private boolean invokeServiceBool(String method, Class<?> type, Object arg) {
    return invokeServiceBool(method, new Class<?>[] {type}, new Object[] {arg});
  }

  private boolean invokeServiceBool(String method, Class<?>[] types, Object[] args) {
    Object result = invokeService(method, types, args);
    if (result instanceof Boolean) return (Boolean) result;
    if (result instanceof Integer) return ((Integer) result) >= 0;
    return result != null;
  }
}
