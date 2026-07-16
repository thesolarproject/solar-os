package com.solar.launcher.soulseek;

import org.junit.Test;

public class SolarDeveloperAccountsTest {

  @Test
  public void developerUsernamesMatchCaseInsensitive() {
    if (!SolarDeveloperAccounts.isDeveloper("SolarDev")) throw new AssertionError("SolarDev");
    if (!SolarDeveloperAccounts.isDeveloper("thesolarphone")) throw new AssertionError("phone");
    if (!SolarDeveloperAccounts.isDeveloper("ThesolarY1")) throw new AssertionError("Y1");
    if (SolarDeveloperAccounts.isDeveloper("randomuser")) throw new AssertionError("not dev");
  }

  @Test
  public void diagUsernameTruncatesToTwentyChars() {
    String u = SolarDeveloperAccounts.deriveDiagUsername("Y1-verylongusername-99");
    if (u.length() > 20) throw new AssertionError("len=" + u.length());
    if (!u.endsWith("-diag")) throw new AssertionError("suffix: " + u);
  }

  @Test
  public void hideFromReachUiCoversDevAndDiagNotVirtualPeer() {
    if (!SolarDeveloperAccounts.hideFromReachUi("SolarDev")) throw new AssertionError("dev");
    if (!SolarDeveloperAccounts.hideFromReachUi("user-diag")) throw new AssertionError("diag");
    if (SolarDeveloperAccounts.hideFromReachUi(SolarDeveloperAccounts.VIRTUAL_PEER)) {
      throw new AssertionError("virtual peer visible in UI");
    }
    if (SolarDeveloperAccounts.hideFromReachUi("Y1-foo-bar-03")) {
      throw new AssertionError("normal user");
    }
  }

  @Test
  public void virtualPeerConstant() {
    if (!SolarDeveloperAccounts.isVirtualPeer(SolarDeveloperAccounts.VIRTUAL_PEER)) {
      throw new AssertionError("virtual");
    }
  }

  @Test
  public void resolveContactInputMapsFriendlyNames() {
    if (!SolarDeveloperAccounts.VIRTUAL_PEER.equals(
            SolarDeveloperAccounts.resolveContactInput("Solar Development"))) {
      throw new AssertionError("display name");
    }
    if (!SolarDeveloperAccounts.VIRTUAL_PEER.equals(
            SolarDeveloperAccounts.resolveContactInput("solar dev"))) {
      throw new AssertionError("solar dev alias");
    }
    if (SolarDeveloperAccounts.resolveContactInput("SolarDev") != null) {
      throw new AssertionError("wire dev should not map to proxy");
    }
    if (!SolarDeveloperAccounts.isAggregatedDeveloperQuery("Solar Development")) {
      throw new AssertionError("aggregated query");
    }
  }

  @Test
  public void matchesDeveloperSearchQueryIncludesWireNames() {
    if (!SolarDeveloperAccounts.matchesDeveloperSearchQuery("SolarDev")) {
      throw new AssertionError("SolarDev search");
    }
    if (!SolarDeveloperAccounts.matchesDeveloperSearchQuery("thesolarphone")) {
      throw new AssertionError("phone search");
    }
    if (!SolarDeveloperAccounts.matchesDeveloperSearchQuery("ThesolarY1")) {
      throw new AssertionError("Y1 search");
    }
    if (SolarDeveloperAccounts.matchesDeveloperSearchQuery("randomuser")) {
      throw new AssertionError("not dev");
    }
  }

  @Test
  public void previewTextStripsDevFromMarker() {
    String packed = SolarDeveloperAccounts.packDevIncoming("SolarDev", "Hello");
    String preview = SolarDeveloperAccounts.previewText(packed);
    if (!"Hello".equals(preview)) throw new AssertionError("preview=" + preview);
  }

  @Test
  public void autoDiagnosticHidesSolarDiagCommandsAndConfirmations() {
    // Diagnostic command/ack lines stay out of the Solar Development conversation UI.
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(
            SolarDeveloperAccounts.DIAG_MARKER + "file: crash.log\n")) {
      throw new AssertionError("marker body");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText("solar_diag")) {
      throw new AssertionError("bare command");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText("Please run solar_diag now")) {
      throw new AssertionError("soft command");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText("solar_diag: sent (issue #12)")) {
      throw new AssertionError("confirmation");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText("solar_diag: failed (retry later)")) {
      throw new AssertionError("failure ack");
    }
    // Wire-visible header at start of message.
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(
            "solar diag - alice@Y1: youtube fail id=abc")) {
      throw new AssertionError("prefix header");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(
            "SOLAR DIAG - wifi disconnecting")) {
      throw new AssertionError("prefix case-insensitive");
    }
    String formatted = SolarDeveloperAccounts.formatDiagConfirmation(true, 7);
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(formatted)) {
      throw new AssertionError("formatDiagConfirmation");
    }
    if (!formatted.startsWith(SolarDeveloperAccounts.AUTO_MSG_PREFIX)) {
      throw new AssertionError("confirmation must start with AUTO_MSG_PREFIX: " + formatted);
    }
    if (SolarDeveloperAccounts.isAutoDiagnosticText("Thanks for Solar!")) {
      throw new AssertionError("normal chat must stay visible");
    }
    // Mid-sentence mention of a song title must not hide the message.
    if (SolarDeveloperAccounts.isAutoDiagnosticText("I like the band Solar Band a lot")) {
      throw new AssertionError("normal chat with band name must stay visible");
    }
    if (!SolarDeveloperAccounts.isDiagRemotePullCommand("please run solar_diag")) {
      throw new AssertionError("pull command");
    }
    if (SolarDeveloperAccounts.isDiagRemotePullCommand("solar_diag: sent (issue #1)")) {
      throw new AssertionError("confirmation must not re-trigger pull");
    }
    if (SolarDeveloperAccounts.isDiagRemotePullCommand(
            SolarDeveloperAccounts.formatDiagConfirmation(true, 3))) {
      throw new AssertionError("marked confirmation not a pull command");
    }
    if (SolarDeveloperAccounts.isDiagRemotePullCommand(
            "solar diag - alice@A5: deezer fail id=1")) {
      throw new AssertionError("impact ping not a pull command");
    }
  }

  @Test
  public void experimentDisabledAfterSupportUiRemoval() {
    android.content.SharedPreferences prefs = new android.content.SharedPreferences() {
      @Override public java.util.Map<String, ?> getAll() { return null; }
      @Override public String getString(String key, String defValue) { return defValue; }
      @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return defValues; }
      @Override public int getInt(String key, int defValue) { return defValue; }
      @Override public long getLong(String key, long defValue) { return defValue; }
      @Override public float getFloat(String key, float defValue) { return defValue; }
      @Override public boolean getBoolean(String key, boolean defValue) { return defValue; }
      @Override public boolean contains(String key) { return false; }
      @Override public Editor edit() { return null; }
      @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
      @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    };
    // 2026-07-16 — Report Issue / Solar Development is always on (not experiment-gated).
    if (!SolarDeveloperAccounts.isExperimentEnabled(prefs)) {
      throw new AssertionError("developer support should be always on");
    }
  }

  @Test
  public void devIncomingPackParseRoundTrip() {
    String packed = SolarDeveloperAccounts.packDevIncoming("SolarDev", "Hello there");
    if (!SolarDeveloperAccounts.isDevIncoming(packed)) throw new AssertionError("marked");
    SolarDeveloperAccounts.DevIncoming parsed = SolarDeveloperAccounts.parseDevIncoming(packed);
    if (!"SolarDev".equals(parsed.fromDev)) throw new AssertionError("from=" + parsed.fromDev);
    if (!"Hello there".equals(parsed.body)) throw new AssertionError("body=" + parsed.body);
    if (!"Hello there".equals(SolarDeveloperAccounts.displayBody(packed))) {
      throw new AssertionError("display");
    }
    SolarDeveloperAccounts.DevIncoming plain =
            SolarDeveloperAccounts.parseDevIncoming("plain text");
    if (!plain.fromDev.isEmpty()) throw new AssertionError("plain from");
    if (!"plain text".equals(plain.body)) throw new AssertionError("plain body");
  }

  @Test
  public void poweredOffNoticeIsAutoDiagnosticHidden() {
    String off = SolarDeveloperAccounts.formatPoweredOffNotice("alice", false);
    String restart = SolarDeveloperAccounts.formatPoweredOffNotice("alice", true);
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(off)) {
      throw new AssertionError("power off should hide from conversation");
    }
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(restart)) {
      throw new AssertionError("restart should hide from conversation");
    }
    if (!off.contains("has powered off")) throw new AssertionError("off body=" + off);
    if (!restart.contains("is restarting")) throw new AssertionError("restart body=" + restart);
  }

  @Test
  public void impactPingIsAutoDiagnosticHidden() {
    String ping = SolarDeveloperAccounts.formatImpactPing("alice", "A5", "youtube failed: 404");
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(ping)) {
      throw new AssertionError("impact ping should hide from conversation");
    }
    if (!ping.startsWith(SolarDeveloperAccounts.AUTO_MSG_PREFIX)) {
      throw new AssertionError("must start with header: " + ping);
    }
    if (!ping.contains("alice@A5:")) throw new AssertionError("ping=" + ping);
    if (!ping.contains("youtube failed")) throw new AssertionError("msg missing");
  }

  @Test
  public void mediaInfoFormatIncludesMetadata() {
    // Unit-test sample strings only — not production hide rules.
    SolarDeveloperImpactPing.MediaInfo info = SolarDeveloperImpactPing.MediaInfo
            .of("deezer")
            .id("123")
            .title("Example Title")
            .artist("Example Artist")
            .album("Demo")
            .reason("cdn 403");
    String line = info.formatLine();
    if (!line.contains("deezer fail")) throw new AssertionError(line);
    if (!line.contains("id=123")) throw new AssertionError(line);
    if (!line.contains("Example Title")) throw new AssertionError(line);
    if (!line.contains("Example Artist")) throw new AssertionError(line);
    if (!line.contains("cdn 403")) throw new AssertionError(line);
  }

  @Test
  public void wireRecipientsDevSenderSkipsSelf() {
    String[] t = SolarDeveloperAccounts.wireRecipientsForSender("SolarDev");
    if (t.length != 2) throw new AssertionError("count=" + t.length);
    for (String u : t) {
      if ("SolarDev".equalsIgnoreCase(u)) throw new AssertionError("self included");
    }
    String[] all = SolarDeveloperAccounts.wireRecipientsForSender("normaluser");
    if (all.length != 3) throw new AssertionError("user fanout=" + all.length);
  }
}
