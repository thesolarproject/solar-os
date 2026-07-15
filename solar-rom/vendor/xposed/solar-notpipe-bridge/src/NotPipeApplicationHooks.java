package com.solar.launcher.xposed.notpipe;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — Registers Solar command receiver inside notPipe process early.
 * Layman: notPipe listens for Solar search/play/save requests over broadcasts.
 * Technical: NotPipe.onCreate → register BroadcastReceiver; Manager.init on first register.
 * 2026-07-14 — Use NotPipeXposedKit (no findAndHookMethod) — fixes Y1/Y2 NoSuchMethodError.
 * 2026-07-14 — Also hooks SolarCmdReceiver (manifest) for API 17 am -n / setComponent delivery.
 * Reversal: disable bridge module — receiver never registers; Solar probe fails gracefully.
 */
public final class NotPipeApplicationHooks {

    private static volatile boolean receiverRegistered;
    private static volatile boolean managerInited;
    private static volatile boolean cmdReceiverHooked;
    /** 2026-07-14 — Keep receiver strongly referenced (Dalvik can drop locals early). */
    private static BroadcastReceiver bridgeReceiver;
    /** 2026-07-14 — Dedupe when dynamic + manifest both see the same CMD. */
    private static final ConcurrentHashMap<String, Long> recentRequestIds =
            new ConcurrentHashMap<String, Long>();

    private NotPipeApplicationHooks() {}

    static void install(LoadPackageParam lpparam) {
        final XC_MethodHook registerAfter = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                registerBridge((Application) param.thisObject);
            }
        };
        try {
            Class<?> appClass = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.NotPipe", lpparam.classLoader);
            int n = NotPipeXposedKit.hookDeclared(appClass, "onCreate", registerAfter);
            SolarNotPipeBridge.log("NotPipe.onCreate hooks=" + n);
            if (n == 0) {
                NotPipeXposedKit.hookDeclared(Application.class, "onCreate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object self = param.thisObject;
                        if (self != null
                                && "io.github.gohoski.notpipe.NotPipe".equals(self.getClass().getName())) {
                            registerBridge((Application) self);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeApplicationHooks failed: " + t);
        }
        hookCmdReceiver(lpparam);
    }

    /**
     * 2026-07-14 — Hook exported SolarCmdReceiver so component-targeted broadcasts work.
     * Layman: named mailbox for YouTube commands when shell am has no -p.
     * Reversal: remove hook — only dynamic Application receiver remains.
     */
    private static void hookCmdReceiver(LoadPackageParam lpparam) {
        if (cmdReceiverHooked) return;
        try {
            Class<?> rx = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.SolarCmdReceiver", lpparam.classLoader);
            int n = NotPipeXposedKit.hookDeclared(rx, "onReceive", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    BroadcastReceiver self = (BroadcastReceiver) param.thisObject;
                    Context ctx = (Context) param.args[0];
                    Intent intent = (Intent) param.args[1];
                    dispatchCommand(self, ctx, intent);
                    param.setResult(null);
                }
            });
            cmdReceiverHooked = n > 0;
            SolarNotPipeBridge.log("SolarCmdReceiver.onReceive hooks=" + n);
        } catch (Throwable t) {
            SolarNotPipeBridge.log("SolarCmdReceiver hook skipped: " + t);
        }
    }

    private static void ensureManagerInit(ClassLoader loader) {
        if (managerInited) return;
        try {
            ClassLoader cl = loader != null ? loader : NotPipeApplicationHooks.class.getClassLoader();
            NotPipeReflect.ensureManagerReady(cl);
            managerInited = true;
        } catch (Throwable t) {
            SolarNotPipeBridge.log("Manager.init skipped/failed: " + t);
        }
    }

    private static void registerBridge(final Context ctx) {
        if (receiverRegistered || ctx == null) return;
        receiverRegistered = true;
        ensureManagerInit(ctx.getClassLoader());
        startWakeService(ctx);
        bridgeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                dispatchCommand(this, context, intent);
            }
        };
        ctx.getApplicationContext().registerReceiver(bridgeReceiver,
                new IntentFilter(NotPipeIpc.ACTION_CMD));
        SolarNotPipeBridge.log("bridge receiver registered");
    }

    /**
     * 2026-07-14 — Shared CMD path for dynamic + manifest receivers.
     * Layman: same YouTube ask handler whether the mailbox was dynamic or named.
     * Technical: goAsync + worker thread so API 17 am broadcast finishes without ANR wait.
     */
    private static void dispatchCommand(BroadcastReceiver self, Context context, Intent intent) {
        if (intent == null || context == null) return;
        SolarNotPipeBridge.log("cmd onReceive action=" + intent.getAction());
        final String cmd = intent.getStringExtra(NotPipeIpc.EXTRA_CMD);
        final String requestId = intent.getStringExtra(NotPipeIpc.EXTRA_REQUEST_ID);
        if (cmd == null || requestId == null) {
            SolarNotPipeBridge.log("cmd missing extras cmd=" + cmd + " id=" + requestId);
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        Long prev = recentRequestIds.putIfAbsent(requestId, Long.valueOf(now));
        if (prev != null && (now - prev.longValue()) < 60000L) {
            SolarNotPipeBridge.log("cmd dup skip " + cmd + " id=" + requestId);
            return;
        }
        // Drop stale ids so the map cannot grow forever on long sessions.
        if (recentRequestIds.size() > 64) {
            for (java.util.Map.Entry<String, Long> e : recentRequestIds.entrySet()) {
                if (now - e.getValue().longValue() > 120000L) {
                    recentRequestIds.remove(e.getKey(), e.getValue());
                }
            }
        }
        SolarNotPipeBridge.log("cmd recv " + cmd + " id=" + requestId);
        final Context app = context.getApplicationContext();
        final Intent extras = new Intent(intent);
        // 2026-07-14 — Do not goAsync for long RESOLVE: am would wait minutes.
        // Worker thread + return onReceive immediately keeps BroadcastQueue healthy.
        new Thread(new Runnable() {
            @Override
            public void run() {
                new CommandTask(app, cmd, requestId, extras).run();
            }
        }, "solar-notpipe-cmd").start();
    }

    /**
     * 2026-07-14 — Start sticky SolarWakeService (baked into Solarized notPipe APK).
     * Layman: keeps notPipe awake in the background for YouTube answers.
     * Reversal: remove startService — wake Activity alone must keep process.
     */
    private static void startWakeService(Context ctx) {
        try {
            Intent svc = new Intent();
            svc.setClassName(NotPipeIpc.NOTPIPE_PKG, "io.github.gohoski.notpipe.SolarWakeService");
            ctx.startService(svc);
            SolarNotPipeBridge.log("SolarWakeService started");
        } catch (Throwable t) {
            SolarNotPipeBridge.log("SolarWakeService start failed: " + t);
        }
    }

    /** Runs on a worker thread; posts RESULT broadcast when finished. */
    private static final class CommandTask {
        private final Context appCtx;
        private final String cmd;
        private final String requestId;
        private final Intent in;

        CommandTask(Context appCtx, String cmd, String requestId, Intent in) {
            this.appCtx = appCtx;
            this.cmd = cmd;
            this.requestId = requestId;
            this.in = in;
        }

        void run() {
            String payload = null;
            boolean ok = false;
            String error = null;
            try {
                ClassLoader cl = appCtx.getClassLoader();
                ensureManagerInit(cl);
                if (NotPipeIpc.CMD_PROBE.equals(cmd)) {
                    ok = true;
                    payload = "{\"version\":\"0.3.0\"}";
                    SolarNotPipeBridge.log("cmd PROBE ok");
                } else {
                    Object manager = NotPipeReflect.getManager(cl);
                    if (NotPipeIpc.CMD_POPULAR.equals(cmd) || NotPipeIpc.CMD_SEARCH.equals(cmd)) {
                        Object metadata = NotPipeReflect.getMetadata(manager);
                        List<?> videos;
                        if (NotPipeIpc.CMD_SEARCH.equals(cmd)) {
                            String query = in.getStringExtra(NotPipeIpc.EXTRA_QUERY);
                            if (query == null || query.trim().isEmpty()) {
                                error = "empty query";
                            } else {
                                videos = NotPipeReflect.search(metadata, query.trim());
                                payload = NotPipeJson.videosToJson(videos);
                                ok = true;
                                SolarNotPipeBridge.log("cmd " + cmd + " ok n="
                                        + (videos != null ? videos.size() : 0));
                            }
                        } else {
                            videos = NotPipeReflect.popularVideos(metadata);
                            payload = NotPipeJson.videosToJson(videos);
                            ok = true;
                            SolarNotPipeBridge.log("cmd " + cmd + " ok n="
                                    + (videos != null ? videos.size() : 0));
                        }
                    } else if (NotPipeIpc.CMD_RESOLVE_STREAM.equals(cmd)) {
                        String videoId = in.getStringExtra(NotPipeIpc.EXTRA_VIDEO_ID);
                        String quality = in.getStringExtra(NotPipeIpc.EXTRA_QUALITY);
                        if (quality == null) quality = "360";
                        if (videoId == null || videoId.isEmpty()) {
                            error = "no video id";
                        } else {
                            String streamKind = in.getStringExtra(NotPipeIpc.EXTRA_STREAM_KIND);
                            if (streamKind == null || streamKind.isEmpty()) {
                                streamKind = NotPipeIpc.STREAM_KIND_VIDEO;
                            }
                            int timeout = 120000;
                            try {
                                Class<?> httpClient = Class.forName(
                                        "io.github.gohoski.notpipe.http.HttpClient", true, cl);
                                timeout = httpClient.getField("CONVERSION_TIMEOUT").getInt(null);
                            } catch (Throwable ignored) {}
                            payload = NotPipeStreamResolver.resolvePayload(
                                    manager, videoId, quality, timeout, streamKind);
                            ok = true;
                            SolarNotPipeBridge.log("cmd RESOLVE_STREAM ok id=" + videoId);
                        }
                    } else if (NotPipeIpc.CMD_GET_COMMENTS.equals(cmd)) {
                        String videoId = in.getStringExtra(NotPipeIpc.EXTRA_VIDEO_ID);
                        if (videoId == null || videoId.isEmpty()) {
                            error = "no video id";
                        } else {
                            Object metadata = NotPipeReflect.getMetadata(manager);
                            List<?> comments = NotPipeReflect.comments(metadata, videoId);
                            payload = NotPipeJson.commentsToJson(comments);
                            ok = true;
                            SolarNotPipeBridge.log("cmd GET_COMMENTS ok n="
                                    + (comments != null ? comments.size() : 0));
                        }
                    } else {
                        error = "unknown cmd";
                    }
                }
            } catch (Throwable t) {
                ok = false;
                error = t.getMessage() != null ? t.getMessage() : t.toString();
                SolarNotPipeBridge.log("cmd " + cmd + " failed: " + error);
            }
            Intent out = new Intent(NotPipeIpc.ACTION_RESULT);
            out.setPackage("com.solar.launcher");
            out.putExtra(NotPipeIpc.EXTRA_REQUEST_ID, requestId);
            out.putExtra(NotPipeIpc.EXTRA_OK, ok);
            if (ok) {
                out.putExtra(NotPipeIpc.EXTRA_PAYLOAD, payload != null ? payload : "");
            } else {
                out.putExtra(NotPipeIpc.EXTRA_ERROR, error != null ? error : "error");
            }
            appCtx.sendBroadcast(out);
        }
    }
}
