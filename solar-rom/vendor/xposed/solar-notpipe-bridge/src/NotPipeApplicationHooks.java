package com.solar.launcher.xposed.notpipe;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — Registers Solar command receiver inside notPipe process early.
 * Layman: notPipe listens for Solar search/play/save requests over broadcasts.
 * Technical: attachBaseContext + onCreate register bridge; Manager.init on first register.
 * Reversal: disable bridge module — receiver never registers; Solar probe fails gracefully.
 */
public final class NotPipeApplicationHooks {

    private static volatile boolean receiverRegistered;
    private static volatile boolean managerInited;

    private NotPipeApplicationHooks() {}

    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> appClass = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.NotPipe", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(appClass, "attachBaseContext", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Application app = (Application) param.thisObject;
                            registerBridge(app);
                        }
                    });
            XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Application app = (Application) param.thisObject;
                    registerBridge(app);
                }
            });
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeApplicationHooks failed: " + t.getMessage());
        }
    }

    private static void registerBridge(final Context ctx) {
        if (receiverRegistered || ctx == null) return;
        receiverRegistered = true;
        ensureManagerInit();
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                final String cmd = intent.getStringExtra(NotPipeIpc.EXTRA_CMD);
                final String requestId = intent.getStringExtra(NotPipeIpc.EXTRA_REQUEST_ID);
                if (cmd == null || requestId == null) return;
                new CommandTask(context.getApplicationContext(), cmd, requestId, intent).execute();
            }
        };
        ctx.registerReceiver(receiver, new IntentFilter(NotPipeIpc.ACTION_CMD));
        SolarNotPipeBridge.log("bridge receiver registered");
    }

    private static void ensureManagerInit() {
        if (managerInited) return;
        try {
            Class<?> managerClass = Class.forName("io.github.gohoski.notpipe.api.Manager");
            Method init = managerClass.getDeclaredMethod("init");
            init.invoke(null);
            managerInited = true;
        } catch (Throwable t) {
            SolarNotPipeBridge.log("Manager.init failed: " + t.getMessage());
        }
    }

    private static final class CommandTask extends AsyncTask<Void, Void, Void> {
        private final Context appCtx;
        private final String cmd;
        private final String requestId;
        private final Intent in;
        private String payload;
        private boolean ok;
        private String error;

        CommandTask(Context appCtx, String cmd, String requestId, Intent in) {
            this.appCtx = appCtx;
            this.cmd = cmd;
            this.requestId = requestId;
            this.in = in;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                ensureManagerInit();
                if (NotPipeIpc.CMD_PROBE.equals(cmd)) {
                    ok = true;
                    payload = "{\"version\":\"0.3.0\"}";
                    return null;
                }
                Class<?> managerClass = Class.forName("io.github.gohoski.notpipe.api.Manager");
                Method getInstance = managerClass.getDeclaredMethod("getInstance");
                Object manager = getInstance.invoke(null);
                if (NotPipeIpc.CMD_POPULAR.equals(cmd) || NotPipeIpc.CMD_SEARCH.equals(cmd)) {
                    Method getMeta = managerClass.getDeclaredMethod("getMetadata");
                    Object metadata = getMeta.invoke(manager);
                    List<?> videos;
                    if (NotPipeIpc.CMD_SEARCH.equals(cmd)) {
                        String query = in.getStringExtra(NotPipeIpc.EXTRA_QUERY);
                        if (query == null || query.trim().isEmpty()) {
                            error = "empty query";
                            return null;
                        }
                        Method search = metadata.getClass().getMethod("search", String.class);
                        videos = (List<?>) search.invoke(metadata, query.trim());
                    } else {
                        Method popular = metadata.getClass().getMethod("getPopularVideos");
                        videos = (List<?>) popular.invoke(metadata);
                    }
                    payload = NotPipeJson.videosToJson(videos);
                    ok = true;
                } else if (NotPipeIpc.CMD_RESOLVE_STREAM.equals(cmd)) {
                    String videoId = in.getStringExtra(NotPipeIpc.EXTRA_VIDEO_ID);
                    String quality = in.getStringExtra(NotPipeIpc.EXTRA_QUALITY);
                    if (quality == null) quality = "360";
                    if (videoId == null || videoId.isEmpty()) {
                        error = "no video id";
                        return null;
                    }
                    String streamKind = in.getStringExtra(NotPipeIpc.EXTRA_STREAM_KIND);
                    if (streamKind == null || streamKind.isEmpty()) {
                        streamKind = NotPipeIpc.STREAM_KIND_VIDEO;
                    }
                    int timeout = 120000;
                    try {
                        Class<?> httpClient = Class.forName("io.github.gohoski.notpipe.http.HttpClient");
                        timeout = httpClient.getField("CONVERSION_TIMEOUT").getInt(null);
                    } catch (Throwable ignored) {}
                    payload = NotPipeStreamResolver.resolvePayload(
                            manager, videoId, quality, timeout, streamKind);
                    ok = true;
                } else {
                    error = "unknown cmd";
                }
            } catch (Throwable t) {
                error = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                SolarNotPipeBridge.log("cmd " + cmd + " failed: " + error);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Intent out = new Intent(NotPipeIpc.ACTION_RESULT);
            out.putExtra(NotPipeIpc.EXTRA_REQUEST_ID, requestId);
            out.putExtra(NotPipeIpc.EXTRA_CMD, cmd);
            out.putExtra(NotPipeIpc.EXTRA_OK, ok);
            if (payload != null) out.putExtra(NotPipeIpc.EXTRA_PAYLOAD, payload);
            if (error != null) out.putExtra(NotPipeIpc.EXTRA_ERROR, error);
            appCtx.sendBroadcast(out);
        }
    }
}
