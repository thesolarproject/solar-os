package com.solar.launcher.soulseek;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.DeviceFeatures;

import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated developer support thread: one local conversation, three wire recipients.
 * Auto diagnostic PMs are never stored here.
 * 2026-07-16 — User messages fan-out on Soulseek and trigger a full diagnostic ship
 * (user report) to the solar-diag Cloudflare worker → GitHub issue. Opening the thread
 * alone does not ship (avoids the open-storm that tanked device performance).
 */
public final class SolarDeveloperMessaging {

    private SolarDeveloperMessaging() {}

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
        if (ctx == null || text == null || SolarDeveloperAccounts.isAutoDiagnosticText(text)) return;
        String stored = SolarDeveloperAccounts.packDevIncoming(fromDev, text);
        SoulseekMessaging.append(ctx, prefs, new SoulseekMessaging.Message(
                msgId, timestamp, SolarDeveloperAccounts.VIRTUAL_PEER, stored, true));
    }

    /**
     * Opening Solar Development is free — no diagnostic HTTPS work.
     * Ships only when the user actually sends a message (or a remote solar_diag pull).
     */
    public static void onThreadOpened(Context ctx, SharedPreferences prefs) {
        // Intentionally empty: ship-on-open flooded solar-diag and tanked Y1/Y2 performance.
    }

    /**
     * Store locally, fan-out wire PMs to developer accounts, and ship a full user-report
     * diagnostic bundle (quoted message + logs) to the Cloudflare worker.
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 1) Full diagnostics → GitHub issue (primary support path; works even if offline PM fails later).
                SolarDiagnosticReporter.shipUserReport(ctx, prefs, trimmed, null);

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
