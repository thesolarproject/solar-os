package com.solar.launcher.soulseek;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class SolarDiagnosticReporterTest {

  @Test
  public void splitContentIntoChunks() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 25000; i++) sb.append('x');
    List<String> chunks = SolarDiagnosticReporter.splitContent(sb.toString(), 12000);
    if (chunks.size() < 3) throw new AssertionError("chunks=" + chunks.size());
  }

  @Test
  public void diagMarkerPresentInWireFormat() {
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(
            SolarDeveloperAccounts.DIAG_MARKER + "user: test\nbody")) {
      throw new AssertionError("marker detect");
    }
  }

  @Test
  public void startupPriorityShipsWhenFingerprintNew() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/storage/sdcard0/solar/logs/crash.log", 999L);
    // New content fingerprint → ship even if mtime matches prior ship.
    if (!SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "100:abc")) {
      throw new AssertionError("crash.log new fingerprint");
    }
    if (SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.ROUTINE)) {
      throw new AssertionError("crash.log routine skip");
    }
  }

  @Test
  public void startupPrioritySkipsUnchangedFingerprint() throws Exception {
    // 2026-07-16 — Stop re-shipping identical crash tails every boot.
    JSONObject manifest = new JSONObject();
    String path = "/storage/sdcard0/solar/logs/crash.log";
    manifest.put(path, 999L);
    manifest.put(SolarDiagnosticReporter.fpKey(path), "42:deadbeef");
    if (SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, path, 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "42:deadbeef")) {
      throw new AssertionError("same fingerprint must skip");
    }
    if (!SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, path, 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "43:cafebabe")) {
      throw new AssertionError("changed fingerprint must ship");
    }
  }

  @Test
  public void contentFingerprintStableForSameText() {
    String a = SolarDiagnosticReporter.contentFingerprint("hello crash stack");
    String b = SolarDiagnosticReporter.contentFingerprint("hello crash stack");
    if (!a.equals(b)) throw new AssertionError("stable fp");
    String c = SolarDiagnosticReporter.contentFingerprint("hello crash stack!");
    if (a.equals(c)) throw new AssertionError("different content different fp");
  }

  @Test
  public void priorityStartupSourceLabels() {
    // 2026-07-16 — Startup priority is crash/error/storage only (performance).
    if (!SolarDiagnosticReporter.isPriorityStartupSource("SolarLog/crash.log")) {
      throw new AssertionError("crash.log");
    }
    if (!SolarDiagnosticReporter.isPriorityStartupSource("SolarLog/error.log")) {
      throw new AssertionError("error.log");
    }
    if (SolarDiagnosticReporter.isPriorityStartupSource("Android/logcat.txt")) {
      throw new AssertionError("logcat should not force startup ship");
    }
    if (SolarDiagnosticReporter.isPriorityStartupSource("Features/reach.log")) {
      throw new AssertionError("feature logs should not force startup ship");
    }
  }

  @Test
  public void supportOpenShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.SUPPORT_OPEN)) {
      throw new AssertionError("support open fresh bundle");
    }
  }

  @Test
  public void remotePullShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.REMOTE_PULL)) {
      throw new AssertionError("remote pull full bundle");
    }
  }

  @Test
  public void userReportShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("user report full bundle");
    }
  }

  @Test
  public void wifiOffShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.WIFI_OFF)) {
      throw new AssertionError("wifi_off flush bundle");
    }
  }

  @Test
  public void runBeforeWifiDisableNullSafe() {
    SolarDiagnosticReporter.runBeforeWifiDisable(null, true, null);
  }

  @Test
  public void shipOnDeveloperSupportOpenNullSafe() {
    SolarDiagnosticReporter.shipOnDeveloperSupportOpen(null, null);
  }

  @Test
  public void shipUserReportNullSafe() {
    SolarDiagnosticReporter.shipUserReport(null, null, "hello", null);
  }

  @Test
  public void shipOnRemoteDiagCommandNullSafe() {
    SolarDiagnosticReporter.shipOnRemoteDiagCommand(null, null, "SolarDev", null);
  }

  @Test
  public void isEnabledDefaultsTrueWhenMissing() {
    // SharedPreferences not available in pure unit test — document contract via constant path.
    // Behavior: prefs.getBoolean(PREF, true) — verified in device/integration when prefs present.
    if (!"solar_diag_auto_report".equals(SolarDiagnosticReporter.PREF_DIAG_AUTO_REPORT)) {
      throw new AssertionError("pref key drift");
    }
  }
}
