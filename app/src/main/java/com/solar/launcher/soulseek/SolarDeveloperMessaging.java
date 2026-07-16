package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.DeviceFeatures;

import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated developer support thread: one local conversation, three wire recipients.
 * Auto diagnostic PMs are never stored here.
 *
 * <p>2026-07-16 — Performance-safe GitHub shipping:
 * <ul>
 *   <li>User message → GitHub user-report <b>once per conversation arm</b> (includes message text).</li>
 *   <li>Further user replies in the same arm only fan-out on Soulseek (no Cloudflare).</li>
 *   <li>Bare {@code solar_diag} from a developer re-arms and full-pulls logs to GitHub.</li>
 *   <li>{@code solar_diag_*} probes never ship to GitHub — silent recon PM only.</li>
 *   <li>Opening the thread never ships.</li>
 * </ul>
 */
public final class SolarDeveloperMessaging {

    /**
     * When true, the next user message to Solar Development ships a full user-report.
     * Cleared after ship; re-armed by bare {@code solar_diag} from a developer.
     */
    public static final String PREF_USER_REPORT_ARMED = "solar_dev_user_report_armed";

    private SolarDeveloperMessaging() {}

    /** Next user message may create a GitHub issue (default armed on fresh install). */
    public static boolean isUserReportArmed(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_USER_REPORT_ARMED, true);
    }

    public static void setUserReportArmed(SharedPreferences prefs, boolean armed) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_USER_REPORT_ARMED, armed).apply();
    }

    /** After a successful (or attempted) user-report ship — block until bare solar_diag. */
    public static void consumeUserReportArm(SharedPreferences prefs) {
        setUserReportArmed(prefs, false);
    }

    /** Bare solar_diag from developer — allow the next user message to ship again. */
    public static void rearmUserReport(SharedPreferences prefs) {
        setUserReportArmed(prefs, true);
    }

    /** Per-recipient PM fan-out outcome — all targets must succeed for a clean send. */
    public static final class FanOutResult {
        public final String[] recipients;
        private final boolean[] succeeded;

        FanOutResult(String[] recipients, boolean[] succeeded) {
            this.recipients = recipients != null ? recipients : new String[0];
            this.succeeded = succeeded != null ? succeeded : new boolean[0];
        }

        /** True when every non-empty recipient got the PM. */
        public boolean allSucceeded() {
            if (recipients.length == 0) return false;
            for (int i = 0; i < recipients.length; i++) {
                String r = recipients[i];
                if (r == null || r.isEmpty()) continue;
                if (i >= succeeded.length || !succeeded[i]) return false;
            }
            return true;
        }

        public boolean anySucceeded() {
            if (recipients.length == 0) return false;
            for (int i = 0; i < recipients.length; i++) {
                String r = recipients[i];
                if (r == null || r.isEmpty()) continue;
                if (i < succeeded.length && succeeded[i]) return true;
            }
            return false;
        }

        /** Wire accounts that still need delivery. */
        public String[] failedRecipients() {
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < recipients.length; i++) {
                if (recipients[i] == null || recipients[i].isEmpty()) continue;
                if (i >= succeeded.length || !succeeded[i]) out.add(recipients[i]);
            }
            return out.toArray(new String[out.size()]);
        }

        /** Merge main-client and diag-session attempts (either path counts as success). */
        FanOutResult merge(FanOutResult other) {
            if (other == null) return this;
            boolean[] merged = new boolean[recipients.length];
            for (int i = 0; i < recipients.length; i++) {
                boolean ok = i < succeeded.length && succeeded[i];
                if (!ok && other.recipients != null) {
                    for (int j = 0; j < other.recipients.length; j++) {
                        if (recipients[i] != null
                                && recipients[i].equalsIgnoreCase(other.recipients[j])
                                && j < other.succeeded.length && other.succeeded[j]) {
                            ok = true;
                            break;
                        }
                    }
                }
                merged[i] = ok;
            }
            return new FanOutResult(recipients, merged);
        }

        static FanOutResult from(String[] recipients, boolean[] perRecipient) {
            return new FanOutResult(recipients, perRecipient);
        }

        static FanOutResult allFailed(String[] recipients) {
            boolean[] s = new boolean[recipients != null ? recipients.length : 0];
            return new FanOutResult(recipients, s);
        }
    }

    public static List<SoulseekMessaging.Message> thread(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> raw = SoulseekMessaging.thread(
                ctx, prefs, SolarDeveloperAccounts.VIRTUAL_PEER);
        if (raw == null) return raw;
        java.util.ArrayList<SoulseekMessaging.Message> out =
                new java.util.ArrayList<SoulseekMessaging.Message>();
        for (SoulseekMessaging.Message m : raw) {
            if (m == null || SolarDeveloperAccounts.isAutoDiagnosticText(m.text)) continue;
            out.add(m);
        }
        return out;
    }

    public static void appendIncoming(Context ctx, SharedPreferences prefs, String fromDev,
            int msgId, int timestamp, String text) {
        if (ctx == null || text == null) return;
        if (SolarDeveloperAccounts.isAutoDiagnosticText(text)) return;
        // Strip bare solar_diag / solar_diag_* so mixed human text remains readable.
        String visible = SolarDeveloperAccounts.stripDiagCommands(text);
        if (visible == null || visible.trim().isEmpty()) return;
        String stored = SolarDeveloperAccounts.packDevIncoming(fromDev, visible.trim());
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                msgId, timestamp, SolarDeveloperAccounts.VIRTUAL_PEER, stored, true));
    }

    /**
     * Opening Solar Development is free — no diagnostic HTTPS work.
     * Ships only on armed user send or bare {@code solar_diag} pull.
     */
    public static void onThreadOpened(Context ctx, SharedPreferences prefs) {
        // Intentionally empty: ship-on-open flooded solar-diag and tanked Y1/Y2 performance.
        SolarDevelopmentBootstrap.ensureVirtualInboxPlaceholder(ctx, prefs);
    }

    /**
     * Store locally + fan-out Soulseek PMs. GitHub user-report ships only when armed
     * (first message of an arm, or first after a bare {@code solar_diag} re-arm).
     * Subsequent chat messages stay local/wire-only so conversation stays snappy.
     */
    public static void sendUserMessage(final Context ctx, final SharedPreferences prefs,
            final SoulseekClient client, final String text,
            final SoulseekClient.MessageSendCallback callback) {
        if (ctx == null || text == null || text.trim().isEmpty()) {
            if (callback != null) callback.onError("Unavailable");
            return;
        }
        final String trimmed = text.trim();
        final String body = formatUserSupportWireBody(ctx, prefs, trimmed);
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                (int) (System.currentTimeMillis() & 0x7fffffff),
                (int) (System.currentTimeMillis() / 1000L),
                SolarDeveloperAccounts.VIRTUAL_PEER, trimmed, false));
        SoulseekAccount acct = SoulseekAccount.load(prefs, ctx);
        final String[] devs = SolarDeveloperAccounts.wireRecipientsForSender(
                acct != null ? acct.username : null);
        final boolean shipReport = isUserReportArmed(prefs);
        if (shipReport) {
            // Consume immediately so rapid double-sends cannot double-ship.
            consumeUserReportArm(prefs);
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1) Optional full diagnostics → GitHub (once per arm; includes message text).
                if (shipReport) {
                    SolarDiagnosticReporter.shipUserReport(ctx, prefs, trimmed, null);
                }

                // 2) Soulseek fan-out to SolarDev / thesolarphone / ThesolarY1.
                FanOutResult result = sendWireFanOut(ctx, prefs, client, devs, body);
                if (!result.allSucceeded()) {
                    SolarDeveloperOutbox.enqueue(ctx, body, result.failedRecipients());
                    SolarDeveloperOutbox.flushSoon(ctx, prefs, client);
                }
                if (callback == null) return;
                // Local thread already has the message — queue silently, never toast failure.
                callback.onSent();
            }
        }, "SolarDevPM").start();
    }

    /**
     * Magical invisible diag for PMs from <b>any</b> Soulseek account:
     * <ul>
     *   <li>Bare {@code solar_diag} → full GitHub log ship; re-arms user-report.</li>
     *   <li>{@code solar_diag_*} probes → silent recon auto-reply to <b>SolarDeveloper only</b>
     *       (never back to the account that sent the probe).</li>
     *   <li>Tokens are stripped; command-only messages never appear in any thread.</li>
     * </ul>
     *
     * @param fromUser wire peer that sent the PM (any account)
     * @return result describing visibility / remaining text for the normal PM path
     */
    public static DiagInboundResult handleInboundDiagMagic(final Context ctx,
            final SharedPreferences prefs, final SoulseekClient client, final String fromUser,
            final int msgId, final int timestamp, final String text) {
        if (ctx == null || text == null) {
            return DiagInboundResult.passThrough(text);
        }
        // Our own auto acks / impact echoes — never re-process or show.
        String lower = text.trim().toLowerCase(java.util.Locale.US);
        if (lower.startsWith("solar diag -") || lower.startsWith("solar diag-")
                || lower.startsWith("solar_diag:")) {
            return DiagInboundResult.fullyHidden();
        }
        final SolarDiagProbes.Parsed parsed = SolarDiagProbes.parse(text);
        final boolean cmdWork = parsed.barePull || parsed.hasProbes();
        if (!cmdWork) {
            // No magic tokens — normal PM (may still be auto-text via other markers).
            if (SolarDeveloperAccounts.isAutoDiagnosticText(text)) {
                return DiagInboundResult.fullyHidden();
            }
            return DiagInboundResult.passThrough(text);
        }
        final String sender = fromUser != null ? fromUser : "?";
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (parsed.barePull) {
                    rearmUserReport(prefs);
                    // Confirmation / ship attribution still goes through SolarDeveloper path.
                    String replyTo = SolarDeveloperAccounts.isDeveloper(sender)
                            ? sender : SolarDeveloperAccounts.SOLAR_DEV;
                    SolarDiagnosticReporter.shipOnRemoteDiagCommand(
                            ctx, prefs, replyTo, null);
                }
                if (parsed.hasProbes()) {
                    String replyBody = SolarDiagProbes.buildProbeReplyForKeys(
                            ctx, prefs, parsed.probeKeys);
                    if (replyBody != null && !replyBody.isEmpty()) {
                        // Attribute which peer triggered the probe (hidden auto line).
                        String attributed = "via " + sender + "\n" + replyBody;
                        String wire = SolarDeveloperAccounts.formatAutoMessage(attributed);
                        // Always SolarDeveloper wire accounts — never reply to the probe sender.
                        String[] devs = SolarDeveloperAccounts.developerUsernames();
                        FanOutResult r = sendWireFanOut(ctx, prefs, client, devs, wire);
                        if (!r.allSucceeded()) {
                            SolarDeveloperOutbox.enqueue(ctx, wire, r.failedRecipients());
                            SolarDeveloperOutbox.flushSoon(ctx, prefs, client);
                        }
                    }
                }
            }
        }, "SolarDiagMagic").start();

        // Command-only → invisible everywhere. Mixed → visible remainder only (tokens gone).
        if (parsed.strippedText == null || parsed.strippedText.trim().isEmpty()) {
            return DiagInboundResult.fullyHidden();
        }
        return DiagInboundResult.visibleRemainder(parsed.strippedText.trim());
    }

    /** @deprecated use {@link #handleInboundDiagMagic} — kept for older call sites. */
    public static boolean handleDeveloperInbound(final Context ctx, final SharedPreferences prefs,
            final SoulseekClient client, final String fromDev, final int msgId,
            final int timestamp, final String text) {
        DiagInboundResult r = handleInboundDiagMagic(ctx, prefs, client, fromDev, msgId, timestamp,
                text);
        if (r.fullyHidden) return true;
        if (r.visibleText != null && !r.visibleText.isEmpty()
                && SolarDeveloperAccounts.isDeveloper(fromDev)) {
            appendIncoming(ctx, prefs, fromDev, msgId, timestamp, r.visibleText);
            return false;
        }
        return r.fullyHidden;
    }

    /** Outcome of magical diag handling for the PM UI path. */
    public static final class DiagInboundResult {
        public final boolean fullyHidden;
        public final boolean hadDiagTokens;
        /** Stripped human text to show/store under the original peer (null when hidden). */
        public final String visibleText;

        private DiagInboundResult(boolean fullyHidden, boolean hadDiagTokens, String visibleText) {
            this.fullyHidden = fullyHidden;
            this.hadDiagTokens = hadDiagTokens;
            this.visibleText = visibleText;
        }

        static DiagInboundResult fullyHidden() {
            return new DiagInboundResult(true, true, null);
        }

        static DiagInboundResult visibleRemainder(String text) {
            return new DiagInboundResult(false, true, text);
        }

        static DiagInboundResult passThrough(String text) {
            return new DiagInboundResult(false, false, text);
        }
    }

    /** Every wire PM attributes the Reach user — not only the first message in a session. */
    static String formatUserSupportWireBody(Context ctx, SharedPreferences prefs, String text) {
        String user = "?";
        try {
            if (prefs != null || ctx != null) {
                SoulseekAccount acct = SoulseekAccount.load(prefs, ctx);
                if (acct != null && acct.username != null && !acct.username.isEmpty()) {
                    user = acct.username;
                }
            }
        } catch (Exception ignored) {}
        String model = "?";
        try {
            model = DeviceFeatures.deviceModelLabel();
        } catch (Exception ignored) {}
        return "[Solar support] user=" + user + " device=" + model + "\n" + text;
    }

    /**
     * Fan-out to hidden dev inboxes — main Reach client first; diag session fallback.
     * Pacing kept modest so three recipients don't freeze the UI thread (work is off-UI).
     */
    static FanOutResult sendWireFanOut(Context ctx, SharedPreferences prefs, SoulseekClient client,
            String[] recipients, String body) {
        if (recipients == null || recipients.length == 0 || body == null || body.isEmpty()) {
            return FanOutResult.allFailed(recipients);
        }
        if (client != null && client.isLoggedIn()) {
            FanOutResult result = sendViaMainClient(client, recipients, body);
            if (result.allSucceeded()) return result;
            String[] failed = result.failedRecipients();
            return result.merge(sendViaDiagSession(ctx, prefs, failed, body));
        } else {
            FanOutResult result = sendViaDiagSession(ctx, prefs, recipients, body);
            if (result.allSucceeded()) return result;
            String[] failed = result.failedRecipients();
            if (failed.length == 0 || client == null || !client.isLoggedIn()) {
                return result;
            }
            return result.merge(sendViaMainClient(client, failed, body));
        }
    }

    /** Sequential paced PM fan-out on the already-connected Reach session. */
    static FanOutResult sendViaMainClient(final SoulseekClient client, String[] recipients,
            final String body) {
        if (client == null || !client.isLoggedIn() || recipients == null || body.isEmpty()) {
            return FanOutResult.allFailed(recipients);
        }
        final boolean[] perRecipient = new boolean[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            if (i > 0) {
                // 8s between PMs — enough for server rate limits without 75s stalls.
                try { Thread.sleep(8_000L); } catch (InterruptedException ignored) {}
            }
            final String to = recipients[i];
            if (to == null || to.isEmpty()) {
                perRecipient[i] = true;
                continue;
            }
            try {
                client.sendPrivateMessageSync(to, body);
                perRecipient[i] = true;
            } catch (Exception ignored) {}
        }
        return FanOutResult.from(recipients, perRecipient);
    }

    /**
     * PM fan-out via -diag session (Nicotine+ client id 160 — server relays to legacy inboxes).
     */
    static FanOutResult sendViaDiagSession(Context ctx, SharedPreferences prefs,
            String[] recipients, String body) {
        if (ctx == null || prefs == null || body == null || body.isEmpty()) {
            return FanOutResult.allFailed(recipients);
        }
        return SolarDiagSessionManager.sendToRecipients(ctx, prefs, recipients, body);
    }

    /** Silent cross-forward so all three dev inboxes stay in sync (via diag session). */
    public static void forwardToOtherDevs(Context ctx, SharedPreferences prefs,
            String fromDev, String text) {
        if (ctx == null || text == null || fromDev == null) return;
        String body = "Forwarded from " + fromDev + ": " + text;
        FanOutResult result = sendViaDiagSession(ctx, prefs,
                SolarDeveloperAccounts.forwardTargets(fromDev), body);
        if (!result.allSucceeded()) {
            SolarDeveloperOutbox.enqueue(ctx, body, result.failedRecipients());
            SolarDeveloperOutbox.flushSoon(ctx, prefs, null);
        }
    }

    public static SoulseekMessaging.Message lastVisibleMessage(Context ctx, SharedPreferences prefs) {
        List<SoulseekMessaging.Message> t = thread(ctx, prefs);
        if (t == null || t.isEmpty()) return null;
        return t.get(t.size() - 1);
    }
}
