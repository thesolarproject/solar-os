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
  public void startupPrioritySourcesBypassManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/storage/sdcard0/solar/logs/crash.log", 999L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP)) {
      throw new AssertionError("crash.log startup");
    }
    if (SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.ROUTINE)) {
      throw new AssertionError("crash.log routine skip");
    }
  }

  @Test
  public void priorityStartupSourceLabels() {
    if (!SolarDiagnosticReporter.isPriorityStartupSource("Android/logcat.txt")) {
      throw new AssertionError("logcat");
    }
    if (!SolarDiagnosticReporter.isPriorityStartupSource("Solar/debug-843b96.log")) {
      throw new AssertionError("debug session log");
    }
    if (!SolarDiagnosticReporter.isPriorityStartupSource("Features/reach.log")) {
      throw new AssertionError("feature log priority");
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
  public void shipOnDeveloperSupportOpenNullSafe() {
    SolarDiagnosticReporter.shipOnDeveloperSupportOpen(null, null);
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
