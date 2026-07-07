package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.solar.launcher.xposed.bridge.extract.SystemErrorDialogRouting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * system_server hooks — replace stock "App has stopped" / "Android System has stopped" dialogs
 * with Solar's themed global overlay (scrollable detail + wheel-friendly Close / Report rows).
 * Fail-open to stock Holo crash UI when Solar is missing or overlay start fails.
 */
final class AppErrorHooks {

    /** KitKat {@link com.android.server.am.AppErrorDialog} force-close codes. */
    private static final int FORCE_QUIT = SystemErrorDialogRouting.CRASH_FORCE_QUIT;
    private static final int FORCE_QUIT_AND_REPORT = SystemErrorDialogRouting.CRASH_FORCE_QUIT_AND_REPORT;

    /** Result broadcast targets system_server — same action as app AlertDialog hooks. */
    private static final String CALLER_PACKAGE = "android";

    private static final ConcurrentHashMap<String, PendingCrash> PENDING =
            new ConcurrentHashMap<String, PendingCrash>();

    private static volatile boolean resultReceiverRegistered;

    /** Live AppErrorDialog + button index → handler message code. */
    private static final class PendingCrash {
        final Object dialogRef;
        final int[] buttonCodes;

        PendingCrash(Object dialogRef, int[] buttonCodes) {
            this.dialogRef = dialogRef;
            this.buttonCodes = buttonCodes;
        }
    }

    private AppErrorHooks() {}

    /** Process label from AMS ProcessRecord — pkgList fallback when processName absent. */
    private static String processNameFromProc(Object proc) {
        if (proc == null) return null;
        try {
            String name = (String) XposedHelpers.getObjectField(proc, "processName");
            if (name != null && name.length() > 0) return name;
        } catch (Throwable ignored) {}
        try {
            String[] pkgList = (String[]) XposedHelpers.getObjectField(proc, "pkgList");
            if (pkgList != null && pkgList.length > 0 && pkgList[0] != null) {
                return pkgList[0];
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Install in system_server (Y1 + Y2) — does not touch USB storage hooks. */
    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.android.server.am.AppErrorDialog", lpparam.classLoader);
            int n = XposedHookKit.hookAll(cls, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (interceptAppErrorDialog(param.thisObject)) {
                            XposedHookKit.skipMethod(param);
                        }
                    } catch (Throwable t) {
                        SolarContextBridge.log("AppError.show intercept error: " + t.getClass().getSimpleName());
                    }
                }
            });
            SolarContextBridge.log("AppErrorDialog hooks=" + n);
        } catch (Throwable t) {
            SolarContextBridge.log("AppErrorDialog skip: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Read crash title/body/buttons from the stock dialog and open Solar native-dialog overlay.
     * The stock 5-minute auto-dismiss timer in AppErrorDialog still runs — fail-safe force-close.
     */
    private static boolean interceptAppErrorDialog(Object dialog) {
        if (dialog == null) return false;
        try {
            Object proc = XposedHelpers.getObjectField(dialog, "mProc");
            String processName = processNameFromProc(proc);
            if (com.solar.home.policy.LauncherErrorRecoveryPolicy.shouldSilentlyDismissErrorUi(processName)) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("proc", processName != null ? processName : "");
                    DebugSession8e4cdcLog.event("AppErrorHooks.silentDismiss", "transition/pending_kill", "S1", d);
                } catch (Throwable ignored) {}
                // #endregion
                fireCrashHandler(dialog, FORCE_QUIT);
                return true;
            }
            if (SystemErrorDialogRouting.isSolarProcess(processName)
                    && com.solar.home.policy.LauncherErrorRecoveryPolicy.shouldOfferRecoveryOverlay(
                            processName)) {
                fireCrashHandler(dialog, FORCE_QUIT);
                Context ctxEarly = (Context) XposedHelpers.callMethod(dialog, "getContext");
                if (ctxEarly != null) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("proc", processName);
                        DebugSession8e4cdcLog.event("AppErrorHooks.recoveryOverlay", "crash streak", "R1", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                    SolarOverlayClient.showLauncherRecoveryOverlay(ctxEarly, processName);
                }
                return true;
            }
            Context ctx = (Context) XposedHelpers.callMethod(dialog, "getContext");
            if (ctx == null || !SystemErrorDialogRouting.shouldReplaceCrash(
                    SolarOverlayClient.canDeliverOverlay(ctx))) return false;

            CharSequence title = (CharSequence) XposedHelpers.callMethod(dialog, "getTitle");
            CharSequence message = (CharSequence) XposedHelpers.callMethod(dialog, "getMessage");
            String body = message != null ? message.toString().trim() : "";
            if (body.length() == 0) return false;

            if (SystemErrorDialogRouting.isSolarProcess(processName)) {
                com.solar.home.policy.LauncherErrorRecoveryPolicy.recordCrashInWindow(processName);
            }

            ArrayList<String> labels = new ArrayList<String>();
            ArrayList<Integer> codes = new ArrayList<Integer>();
            readCrashButtons(dialog, labels, codes);
            SystemErrorDialogRouting.defaultCrashButtons(labels, codes);

            int[] buttonCodes = new int[codes.size()];
            for (int i = 0; i < codes.size(); i++) {
                buttonCodes[i] = codes.get(i);
            }
            String sessionId = UUID.randomUUID().toString();
            PENDING.put(sessionId, new PendingCrash(dialog, buttonCodes));
            ensureResultReceiver(ctx.getApplicationContext());

            String dialogTitle = title != null && title.length() > 0 ? title.toString() : null;
            String[] buttons = labels.toArray(new String[labels.size()]);
            if (!SolarOverlayClient.showNativeDialog(ctx, dialogTitle, body, buttons, sessionId,
                    CALLER_PACKAGE)) {
                PENDING.remove(sessionId);
                return false;
            }
            SolarContextBridge.log("AppError overlay pkg=" + body.substring(0, Math.min(40, body.length())));
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("buttons", buttons.length);
                d.put("title", dialogTitle != null ? dialogTitle : "");
                BridgeAnrDebugLog.event("AppErrorHooks.intercept", "solar overlay", "H6", d);
            } catch (Throwable ignored) {}
            // #endregion
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("AppError intercept failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Read Close / Report labels from AlertController — getButton() is empty before show(). */
    private static void readCrashButtons(Object dialog, ArrayList<String> labels,
            ArrayList<Integer> codes) {
        try {
            Object controller = XposedHelpers.getObjectField(dialog, "mAlert");
            if (controller != null) {
                addButtonLabel(controller, "mButtonPositiveText", FORCE_QUIT, labels, codes);
                if (hasCrashReportReceiver(dialog)) {
                    addButtonLabel(controller, "mButtonNegativeText", FORCE_QUIT_AND_REPORT, labels, codes);
                }
            }
        } catch (Throwable ignored) {}
        if (labels.isEmpty()) {
            collectCrashButton(dialog, DialogInterface.BUTTON_POSITIVE, FORCE_QUIT, labels, codes);
            if (hasCrashReportReceiver(dialog)) {
                collectCrashButton(dialog, DialogInterface.BUTTON_NEGATIVE, FORCE_QUIT_AND_REPORT, labels, codes);
            }
        }
    }

    /** True when AMS attached a crash report receiver — Report row only then. */
    private static boolean hasCrashReportReceiver(Object dialog) {
        try {
            Object proc = XposedHelpers.getObjectField(dialog, "mProc");
            if (proc == null) return false;
            return XposedHelpers.getObjectField(proc, "errorReportReceiver") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void addButtonLabel(Object controller, String field, int code,
            ArrayList<String> labels, ArrayList<Integer> codes) {
        try {
            CharSequence text = (CharSequence) XposedHelpers.getObjectField(controller, field);
            if (text != null && text.length() > 0) {
                labels.add(text.toString());
                codes.add(code);
            }
        } catch (Throwable ignored) {}
    }

    /** Fallback when AlertController fields are unavailable — after partial show. */
    private static void collectCrashButton(Object dialog, int which, int code,
            ArrayList<String> labels, ArrayList<Integer> codes) {
        try {
            CharSequence text = (CharSequence) XposedHelpers.callMethod(dialog, "getButton", which);
            if (text != null && text.length() > 0) {
                labels.add(text.toString());
                codes.add(code);
            }
        } catch (Throwable ignored) {}
    }

    /** Deliver overlay button picks back into AppErrorDialog's internal Handler. */
    private static void ensureResultReceiver(Context appCtx) {
        if (resultReceiverRegistered) return;
        synchronized (AppErrorHooks.class) {
            if (resultReceiverRegistered) return;
            appCtx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !DialogHooks.ACTION_DIALOG_RESULT.equals(intent.getAction())) {
                        return;
                    }
                    String sessionId = intent.getStringExtra(DialogHooks.EXTRA_MENU_SESSION_ID);
                    if (sessionId == null) return;
                    int index = intent.getIntExtra(DialogHooks.EXTRA_SELECTED_INDEX, -1);
                    PendingCrash pending = PENDING.remove(sessionId);
                    if (pending == null) return;
                    try {
                        int code = SystemErrorDialogRouting.handlerCodeForOverlaySelection(
                                pending.buttonCodes, index, FORCE_QUIT);
                        fireCrashHandler(pending.dialogRef, code);
                    } catch (Throwable t) {
                        SolarContextBridge.log("AppError result failed: " + t.getClass().getSimpleName());
                    }
                }
            }, new IntentFilter(DialogHooks.ACTION_DIALOG_RESULT));
            resultReceiverRegistered = true;
        }
    }

    /** Route through AppErrorDialog.mHandler — same path as tapping Close / Report on stock UI. */
    private static void fireCrashHandler(Object dialog, int code) {
        Object handler = XposedHelpers.getObjectField(dialog, "mHandler");
        Object msg = XposedHelpers.callMethod(handler, "obtainMessage", code);
        XposedHelpers.callMethod(handler, "handleMessage", msg);
        SolarContextBridge.log("AppError handler code=" + code);
    }
}
