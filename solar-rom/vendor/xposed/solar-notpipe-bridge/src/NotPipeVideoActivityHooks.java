package com.solar.launcher.xposed.notpipe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.view.KeyEvent;
import android.widget.VideoView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-06 — Wheel keys + landscape lock for Solar-hosted notPipe VideoActivity.
 * Layman: makes YouTube playback work with the scroll wheel on Y1/Y2.
 * Technical: solar_hosted extra gates hooks; blocks portrait fullscreen from notPipe 0.3.0.
 * Reversal: disable module — notPipe stock touch UI returns for direct launches.
 */
public final class NotPipeVideoActivityHooks {

    private NotPipeVideoActivityHooks() {}

    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> videoAct = XposedHelpers.findClass(
                    "io.github.gohoski.notpipe.VideoActivity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(videoAct, "onCreate",
                    android.os.Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Activity act = (Activity) param.thisObject;
                            if (!isSolarHosted(act)) return;
                            act.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                            try {
                                act.getActionBar().hide();
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedHelpers.findAndHookMethod(videoAct, "setRequestedOrientation", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity act = (Activity) param.thisObject;
                            if (!isSolarHosted(act)) return;
                            int req = (Integer) param.args[0];
                            if (req == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    || req == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    || req == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                                param.args[0] = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(videoAct, "onKeyDown", int.class, KeyEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity act = (Activity) param.thisObject;
                            if (!isSolarHosted(act)) return;
                            int keyCode = (Integer) param.args[0];
                            KeyEvent event = (KeyEvent) param.args[1];
                            if (event == null) return;
                            if (handleWheelKey(act, keyCode, event)) {
                                param.setResult(true);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(videoAct, "onDestroy", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity act = (Activity) param.thisObject;
                    if (!isSolarHosted(act)) return;
                    Intent exited = new Intent(NotPipeIpc.ACTION_PLAYER_EXITED);
                    act.sendBroadcast(exited);
                }
            });
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeVideoActivityHooks failed: " + t.getMessage());
        }
    }

    private static boolean isSolarHosted(Activity act) {
        if (act == null || act.getIntent() == null) return false;
        return act.getIntent().getBooleanExtra(NotPipeIpc.EXTRA_SOLAR_HOSTED, false);
    }

    /** Y1/Y2 wheel + side keys → play/pause and coarse seek. */
    private static boolean handleWheelKey(Activity act, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getRepeatCount() > 0 && keyCode != KeyEvent.KEYCODE_BACK) return false;
        try {
            VideoView vv = (VideoView) XposedHelpers.getObjectField(act, "videoView");
            if (vv == null) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: // 85
                case 126:
                case 127:
                    if (vv.isPlaying()) vv.pause();
                    else vv.start();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: // 21
                    vv.seekTo(Math.max(0, vv.getCurrentPosition() - 10000));
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: // 22
                    vv.seekTo(vv.getCurrentPosition() + 10000);
                    return true;
                case KeyEvent.KEYCODE_BACK: // 4
                    act.finish();
                    return true;
                default:
                    return false;
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
