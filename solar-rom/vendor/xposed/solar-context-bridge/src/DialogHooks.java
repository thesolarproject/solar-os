package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Message;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * App-process hooks: replace stock AlertDialog info/confirm boxes with Solar overlay rows.
 * Scrollable message body + wheel-friendly action buttons (Details / OK pattern).
 */
final class DialogHooks {

    /** Must match {@link com.solar.launcher.OverlayTriggers#ACTION_DIALOG_RESULT}. */
    static final String ACTION_DIALOG_RESULT =
            "com.solar.launcher.action.DIALOG_RESULT";
    static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    static final String EXTRA_SELECTED_INDEX = "menu_selected_index";

    private static final ConcurrentHashMap<String, PendingDialog> PENDING =
            new ConcurrentHashMap<String, PendingDialog>();

    private static volatile boolean resultReceiverRegistered;

    /** AlertController message fields for positive/negative/neutral taps. */
    private static final class PendingDialog {
        final Object dialogRef;
        final Object controllerRef;
        final int[] buttonIds;

        PendingDialog(Object dialogRef, Object controllerRef, int[] buttonIds) {
            this.dialogRef = dialogRef;
            this.controllerRef = controllerRef;
            this.buttonIds = buttonIds;
        }
    }

    private DialogHooks() {}

    /** Hook AlertDialog.show in every non-Solar app process. */
    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> alertDialog = XposedHelpers.findClass("android.app.AlertDialog", lpparam.classLoader);
            XposedHookKit.hookAll(alertDialog, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (interceptAlertDialog(param.thisObject)) {
                        param.setResult(null);
                    }
                }
            });
            SolarContextBridge.log("hooked AlertDialog.show in " + lpparam.packageName);
        } catch (Throwable t) {
            SolarContextBridge.log("AlertDialog.show skip " + lpparam.packageName + ": "
                    + t.getClass().getSimpleName());
        }
    }

    /** Extract title/message/buttons and open Solar native-dialog overlay instead of stock UI. */
    private static boolean interceptAlertDialog(Object dialog) {
        if (dialog == null) return false;
        try {
            Object controller = XposedHelpers.getObjectField(dialog, "mAlert");
            if (controller == null) return false;

            // Custom-view-only dialogs — leave stock UI (fail-open).
            Object customView = XposedHelpers.getObjectField(controller, "mView");
            CharSequence message = (CharSequence) XposedHelpers.getObjectField(controller, "mMessage");
            if (customView != null && (message == null || message.length() == 0)) {
                return false;
            }

            CharSequence title = (CharSequence) XposedHelpers.getObjectField(controller, "mTitle");
            ArrayList<String> buttonLabels = new ArrayList<String>();
            ArrayList<Integer> buttonIds = new ArrayList<Integer>();
            collectButton(controller, "mButtonPositiveText", DialogInterface.BUTTON_POSITIVE,
                    buttonLabels, buttonIds);
            collectButton(controller, "mButtonNegativeText", DialogInterface.BUTTON_NEGATIVE,
                    buttonLabels, buttonIds);
            collectButton(controller, "mButtonNeutralText", DialogInterface.BUTTON_NEUTRAL,
                    buttonLabels, buttonIds);

            String body = message != null ? message.toString().trim() : "";
            if (body.length() == 0 && buttonLabels.isEmpty()) {
                return false;
            }
            if (buttonLabels.isEmpty()) {
                buttonLabels.add("OK");
                buttonIds.add(DialogInterface.BUTTON_POSITIVE);
            }

            Context ctx = (Context) XposedHelpers.callMethod(dialog, "getContext");
            if (ctx == null) return false;

            int[] ids = new int[buttonIds.size()];
            for (int i = 0; i < buttonIds.size(); i++) {
                ids[i] = buttonIds.get(i);
            }
            String sessionId = UUID.randomUUID().toString();
            PENDING.put(sessionId, new PendingDialog(dialog, controller, ids));
            ensureResultReceiver(ctx.getApplicationContext());

            String dialogTitle = title != null && title.length() > 0 ? title.toString() : null;
            String[] buttons = buttonLabels.toArray(new String[buttonLabels.size()]);
            SolarOverlayClient.showNativeDialog(ctx, dialogTitle, body, buttons, sessionId,
                    ctx.getPackageName());
            SolarContextBridge.log("dialog overlay pkg=" + ctx.getPackageName()
                    + " buttons=" + buttons.length);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("dialog intercept error: " + t);
            return false;
        }
    }

    /** Read one AlertController button label when present. */
    private static void collectButton(Object controller, String textField, int buttonId,
            ArrayList<String> labels, ArrayList<Integer> ids) {
        try {
            CharSequence text = (CharSequence) XposedHelpers.getObjectField(controller, textField);
            if (text != null && text.length() > 0) {
                labels.add(text.toString());
                ids.add(buttonId);
            }
        } catch (Throwable ignored) {}
    }

    /** Deliver overlay selection back into the hooked app's AlertDialog. */
    private static void ensureResultReceiver(Context appCtx) {
        if (resultReceiverRegistered) return;
        synchronized (DialogHooks.class) {
            if (resultReceiverRegistered) return;
            appCtx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !ACTION_DIALOG_RESULT.equals(intent.getAction())) return;
                    String sessionId = intent.getStringExtra(EXTRA_MENU_SESSION_ID);
                    if (sessionId == null) return;
                    int index = intent.getIntExtra(EXTRA_SELECTED_INDEX, -1);
                    PendingDialog pending = PENDING.remove(sessionId);
                    if (pending == null) return;
                    try {
                        if (index < 0) {
                            XposedHelpers.callMethod(pending.dialogRef, "cancel");
                            return;
                        }
                        int buttonIndex = index - 1;
                        if (buttonIndex < 0 || buttonIndex >= pending.buttonIds.length) {
                            XposedHelpers.callMethod(pending.dialogRef, "dismiss");
                            return;
                        }
                        fireAlertButton(pending, pending.buttonIds[buttonIndex]);
                    } catch (Throwable t) {
                        SolarContextBridge.log("dialog result failed: " + t);
                    }
                }
            }, new IntentFilter(ACTION_DIALOG_RESULT));
            resultReceiverRegistered = true;
        }
    }

    /** Route through AlertController's Message handler — same path as tapping the stock button. */
    private static void fireAlertButton(PendingDialog pending, int buttonId) {
        String msgField;
        if (buttonId == DialogInterface.BUTTON_POSITIVE) {
            msgField = "mButtonPositiveMessage";
        } else if (buttonId == DialogInterface.BUTTON_NEGATIVE) {
            msgField = "mButtonNegativeMessage";
        } else {
            msgField = "mButtonNeutralMessage";
        }
        Object msgObj = XposedHelpers.getObjectField(pending.controllerRef, msgField);
        if (msgObj instanceof Message) {
            Message msg = Message.obtain((Message) msgObj);
            if (msg != null) {
                msg.sendToTarget();
                return;
            }
        }
        XposedHelpers.callMethod(pending.dialogRef, "dismiss");
    }
}
