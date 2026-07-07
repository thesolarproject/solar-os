package com.solar.launcher.xposed.rockbox.ime;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Rockbox search/text: Solar IME Enter presses stock OK on KbdInput dialog.
 * Reversal: disable SolarRockboxIme module — wheel must tap OK after typing in Rockbox.
 */
final class RockboxKeyboardImeOkHooks {

    private static final String ROCKBOX_PKG = "org.rockbox";
    private static final String KBD_INPUT_ID = "KbdInput";
    private static volatile boolean installed;

    private RockboxKeyboardImeOkHooks() {}

    static void install(LoadPackageParam lpparam) {
        if (installed) return;
        installed = true;
        try {
            Class<?> alertDialog = XposedHelpers.findClass("android.app.AlertDialog", lpparam.classLoader);
            XposedHookKit.hookAll(alertDialog, "show", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    wireKeyboardDialogIfNeeded(param.thisObject);
                }
            });
            SolarRockboxIme.log("RockboxKeyboardImeOkHooks installed");
        } catch (Throwable t) {
            SolarRockboxIme.log("RockboxKeyboardImeOkHooks failed: " + t.getClass().getSimpleName());
        }
    }

    private static void wireKeyboardDialogIfNeeded(Object dialogObj) {
        if (!(dialogObj instanceof AlertDialog)) return;
        final AlertDialog dialog = (AlertDialog) dialogObj;
        final EditText input = findKbdInput(dialog);
        if (input == null) return;

        Runnable wire = new Runnable() {
            @Override
            public void run() {
                try {
                    input.setImeOptions(EditorInfo.IME_ACTION_DONE
                            | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    // 2026-07-06 — Rockbox KbdInput has inputType 0 — Solar IME tray rejects without TEXT class.
                    input.setInputType(EditorInfo.TYPE_CLASS_TEXT);
                    input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                            if (actionId == EditorInfo.IME_ACTION_DONE
                                    || actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                                triggerPositive(dialog);
                                return true;
                            }
                            return false;
                        }
                    });
                } catch (Throwable t) {
                    SolarRockboxIme.log("KbdInput wire failed: " + t.getClass().getSimpleName());
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            wire.run();
        } else {
            new Handler(Looper.getMainLooper()).post(wire);
        }
    }

    private static EditText findKbdInput(AlertDialog dialog) {
        if (dialog == null) return null;
        try {
            android.content.Context ctx = dialog.getContext();
            if (ctx == null || !ROCKBOX_PKG.equals(ctx.getPackageName())) return null;
            int id = ctx.getResources().getIdentifier(KBD_INPUT_ID, "id", ROCKBOX_PKG);
            if (id == 0) return null;
            View v = dialog.findViewById(id);
            if (v instanceof EditText) return (EditText) v;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void triggerPositive(final AlertDialog dialog) {
        if (dialog == null) return;
        Runnable click = new Runnable() {
            @Override
            public void run() {
                try {
                    View positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (positive != null && positive.getVisibility() == View.VISIBLE) {
                        positive.performClick();
                        return;
                    }
                } catch (Throwable ignored) {}
                try {
                    Object controller = XposedHelpers.getObjectField(dialog, "mAlert");
                    if (controller == null) return;
                    Object msgObj = XposedHelpers.getObjectField(controller, "mButtonPositiveMessage");
                    if (msgObj instanceof Message) {
                        ((Message) msgObj).sendToTarget();
                    }
                } catch (Throwable ignored) {}
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            click.run();
        } else {
            new Handler(Looper.getMainLooper()).post(click);
        }
    }
}
