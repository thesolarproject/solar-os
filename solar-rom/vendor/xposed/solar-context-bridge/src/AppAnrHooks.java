package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.solar.launcher.xposed.bridge.extract.SystemErrorDialogRouting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — system_server hooks for stock "isn't responding" ANR dialogs.
 * Third-party apps get Solar's wheel-friendly overlay; Solar processes auto-WAIT then overlay;
 * system / fail-open paths keep Holo UI with AnrDialogKeyForwarder wheel→DPAD injection.
 * Reversal: remove install() call from SystemServerHooks — stock ANR returns unchanged.
 */
final class AppAnrHooks {

    /** KitKat {@link com.android.server.am.AppNotRespondingDialog} handler codes. */
    private static final int FORCE_CLOSE = SystemErrorDialogRouting.ANR_FORCE_CLOSE;
    private static final int WAIT = SystemErrorDialogRouting.ANR_WAIT;
    private static final int WAIT_AND_REPORT = SystemErrorDialogRouting.ANR_WAIT_AND_REPORT;

    /** Result broadcast targets system_server — same action as crash / AlertDialog hooks. */
    private static final String CALLER_PACKAGE = "android";

    private static final ConcurrentHashMap<String, PendingAnr> PENDING =
            new ConcurrentHashMap<String, PendingAnr>();

    private static volatile boolean resultReceiverRegistered;

    /** Live AppNotRespondingDialog + overlay row → handler message code. */
    private static final class PendingAnr {
        final Object dialogRef;
        final int[] buttonCodes;

        PendingAnr(Object dialogRef, int[] buttonCodes) {
            this.dialogRef = dialogRef;
            this.buttonCodes = buttonCodes;
        }
    }

    private AppAnrHooks() {}

    /** Install in system_server (Y1 + Y2) alongside AppErrorHooks. */
    static void install(LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.android.server.am.AppNotRespondingDialog", lpparam.classLoader);
            XposedHookKit.hookAll(cls, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (interceptAnrDialog(param.thisObject)) {
                            XposedHookKit.skipMethod(param);
                        }
                    } catch (Throwable t) {
                        SolarContextBridge.log("AppAnr.show intercept error: "
                                + t.getClass().getSimpleName());
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // Stock Holo shown — arm PWM wheel→DPAD for scrollwheel navigation.
                    AnrDialogKeyForwarder.setStockAnrActive(true);
                }
            });
            hookDismiss(cls);
            int nClose = XposedHookKit.hookAll(cls, "closeDialog", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    AnrDialogKeyForwarder.setStockAnrActive(false);
                }
            });
            SolarContextBridge.log("AppNotRespondingDialog hooks show+dismiss close=" + nClose);
        } catch (Throwable t) {
            SolarContextBridge.log("AppNotRespondingDialog skip: " + t.getClass().getSimpleName());
        }
    }

    /** Clear stock-ANR key-forward flag when the Holo dialog closes. */
    private static void hookDismiss(Class<?> cls) {
        try {
            XposedHookKit.hookAll(cls, "dismiss", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    AnrDialogKeyForwarder.setStockAnrActive(false);
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Route ANR to Solar overlay when safe; Solar pkg auto-fires WAIT first so the process
     * gets breathing room before the user picks Wait / Close / Report on the overlay.
     */
    private static boolean interceptAnrDialog(Object dialog) {
        if (dialog == null) return false;
        try {
            Object proc = XposedHelpers.getObjectField(dialog, "mProc");
            String processName = processNameFromProc(proc);
            if (com.solar.home.policy.LauncherErrorRecoveryPolicy.shouldSilentlyDismissErrorUi(processName)) {
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("proc", processName != null ? processName : "");
                    DebugSession8e4cdcLog.event("AppAnrHooks.silentDismiss", "transition/pending_kill", "S1", d);
                } catch (Throwable ignored) {}
                // #endregion
                fireAnrHandler(dialog, FORCE_CLOSE);
                return true;
            }
            if (SystemErrorDialogRouting.isSolarProcess(processName)
                    && com.solar.home.policy.LauncherErrorRecoveryPolicy.shouldOfferRecoveryOverlay(
                            processName)) {
                fireAnrHandler(dialog, FORCE_CLOSE);
                Context ctxEarly = (Context) XposedHelpers.callMethod(dialog, "getContext");
                if (ctxEarly != null) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("proc", processName);
                        DebugSession8e4cdcLog.event("AppAnrHooks.recoveryOverlay", "crash streak", "R1", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                    SolarOverlayClient.showLauncherRecoveryOverlay(ctxEarly, processName);
                }
                return true;
            }
            Context ctx = (Context) XposedHelpers.callMethod(dialog, "getContext");
            boolean overlayOk = ctx != null && SolarOverlayClient.canDeliverOverlay(ctx);
            if (!SystemErrorDialogRouting.shouldReplaceAnr(processName, overlayOk)) return false;

            CharSequence title = (CharSequence) XposedHelpers.callMethod(dialog, "getTitle");
            CharSequence message = (CharSequence) XposedHelpers.callMethod(dialog, "getMessage");
            String body = message != null ? message.toString().trim() : "";
            if (body.length() == 0) return false;

            boolean solarProcess =
                    SystemErrorDialogRouting.shouldAutoWaitBeforeSolarAnrOverlay(processName);
            if (solarProcess) {
                com.solar.home.policy.LauncherErrorRecoveryPolicy.recordCrashInWindow(processName);
            }
            if (solarProcess) {
                // Auto-WAIT before overlay — dismisses harsh stock ANR state for Solar itself.
                fireAnrHandler(dialog, WAIT);
            }

            ArrayList<String> labels = new ArrayList<String>();
            ArrayList<Integer> codes = new ArrayList<Integer>();
            readAnrButtonsWheelOrder(dialog, labels, codes);
            SystemErrorDialogRouting.defaultAnrButtons(labels, codes);

            int[] buttonCodes = new int[codes.size()];
            for (int i = 0; i < codes.size(); i++) {
                buttonCodes[i] = codes.get(i);
            }
            String sessionId = UUID.randomUUID().toString();
            PENDING.put(sessionId, new PendingAnr(dialog, buttonCodes));
            ensureResultReceiver(ctx.getApplicationContext());

            String dialogTitle = title != null && title.length() > 0 ? title.toString() : null;
            String[] buttons = labels.toArray(new String[labels.size()]);
            if (!SolarOverlayClient.showNativeDialog(ctx, dialogTitle, body, buttons, sessionId,
                    CALLER_PACKAGE)) {
                PENDING.remove(sessionId);
                return false;
            }
            scheduleAnrOverlayFailOpen(dialog, sessionId);
            SolarContextBridge.log("AppAnr overlay proc=" + (processName != null ? processName : "?")
                    + " solarAutoWait=" + solarProcess);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("AppAnr intercept failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

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

    /**
     * Read Wait / Close / Report from AlertController; present Wait first for wheel UX.
     * Holo order is positive=close, negative=wait, neutral=report.
     */
    private static void readAnrButtonsWheelOrder(Object dialog, ArrayList<String> labels,
            ArrayList<Integer> codes) {
        Map<Integer, String> byCode = new LinkedHashMap<Integer, String>();
        try {
            Object controller = XposedHelpers.getObjectField(dialog, "mAlert");
            if (controller != null) {
                putButtonLabel(byCode, controller, "mButtonNegativeText", WAIT);
                putButtonLabel(byCode, controller, "mButtonPositiveText", FORCE_CLOSE);
                if (hasAnrReportReceiver(dialog)) {
                    putButtonLabel(byCode, controller, "mButtonNeutralText", WAIT_AND_REPORT);
                }
            }
        } catch (Throwable ignored) {}
        if (byCode.isEmpty()) {
            collectAnrButton(dialog, DialogInterface.BUTTON_NEGATIVE, WAIT, byCode);
            collectAnrButton(dialog, DialogInterface.BUTTON_POSITIVE, FORCE_CLOSE, byCode);
            if (hasAnrReportReceiver(dialog)) {
                collectAnrButton(dialog, DialogInterface.BUTTON_NEUTRAL, WAIT_AND_REPORT, byCode);
            }
        }
        SystemErrorDialogRouting.orderAnrButtonsForWheel(byCode, labels, codes);
    }

    /** True when AMS attached a bug-report receiver — Report row only then. */
    private static boolean hasAnrReportReceiver(Object dialog) {
        try {
            Object proc = XposedHelpers.getObjectField(dialog, "mProc");
            if (proc == null) return false;
            return XposedHelpers.getObjectField(proc, "errorReportReceiver") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void putButtonLabel(Map<Integer, String> byCode, Object controller,
            String field, int code) {
        try {
            CharSequence text = (CharSequence) XposedHelpers.getObjectField(controller, field);
            if (text != null && text.length() > 0) {
                byCode.put(code, text.toString());
            }
        } catch (Throwable ignored) {}
    }

    private static void collectAnrButton(Object dialog, int which, int code,
            Map<Integer, String> byCode) {
        try {
            CharSequence text = (CharSequence) XposedHelpers.callMethod(dialog, "getButton", which);
            if (text != null && text.length() > 0) {
                byCode.put(code, text.toString());
            }
        } catch (Throwable ignored) {}
    }

    /** Deliver overlay button picks back into AppNotRespondingDialog's internal Handler. */
    private static void ensureResultReceiver(Context appCtx) {
        if (resultReceiverRegistered) return;
        synchronized (AppAnrHooks.class) {
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
                    PendingAnr pending = PENDING.remove(sessionId);
                    if (pending == null) return;
                    try {
                        int code = SystemErrorDialogRouting.handlerCodeForOverlaySelection(
                                pending.buttonCodes, index, FORCE_CLOSE);
                        fireAnrHandler(pending.dialogRef, code);
                    } catch (Throwable t) {
                        SolarContextBridge.log("AppAnr result failed: " + t.getClass().getSimpleName());
                    }
                }
            }, new IntentFilter(DialogHooks.ACTION_DIALOG_RESULT));
            resultReceiverRegistered = true;
        }
    }

    /** Route through AppNotRespondingDialog.mHandler — same path as stock Holo button tap. */
    private static void fireAnrHandler(Object dialog, int code) {
        Object handler = XposedHelpers.getObjectField(dialog, "mHandler");
        Object msg = XposedHelpers.callMethod(handler, "obtainMessage", code);
        XposedHelpers.callMethod(handler, "handleMessage", msg);
        SolarContextBridge.log("AppAnr handler code=" + code);
    }

    /** 2026-07-07 — If overlay never paints ui=1, fail-open to stock Holo + wheel forwarder. */
    private static void scheduleAnrOverlayFailOpen(final Object dialog, final String sessionId) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!PENDING.containsKey(sessionId)) return;
                if (isOverlayUiVisible()) {
                    return;
                }
                PENDING.remove(sessionId);
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("session", sessionId.substring(0, Math.min(8, sessionId.length())));
                    DebugSession8e4cdcLog.event("AppAnrHooks.failOpen", "stock Holo", "E1", d);
                } catch (Throwable ignored) {}
                // #endregion
                SolarContextBridge.log("AppAnr fail-open stock Holo session="
                        + sessionId.substring(0, Math.min(8, sessionId.length())));
                AnrDialogKeyForwarder.setStockAnrActive(true);
                try {
                    XposedHelpers.callMethod(dialog, "show");
                } catch (Throwable t) {
                    SolarContextBridge.log("AppAnr fail-open show failed: "
                            + t.getClass().getSimpleName());
                }
            }
        }, 2000L);
    }

    private static boolean isOverlayUiVisible() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get",
                    "sys.solar.overlay.ui", "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
