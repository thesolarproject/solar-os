package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Start Solar overlay service directly — more reliable than broadcast from system_server.
 */
public final class SolarOverlayClient {

    public static final String PERMISSION = "com.solar.launcher.permission.OVERLAY_TRIGGER";
    public static final String ACTION_SHOW_OVERLAY_POWER =
            "com.solar.launcher.action.SHOW_OVERLAY_POWER";
    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            "com.solar.launcher.action.SHOW_OVERLAY_APP_MENU";
    public static final String ACTION_SHOW_OVERLAY_VOLUME =
            "com.solar.launcher.action.SHOW_OVERLAY_VOLUME";
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_PICKER =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_PICKER";
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            "com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG";
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE";
    public static final String EXTRA_USB_STORAGE_LOCK = "usb_storage_lock";
    public static final String EXTRA_MENU_TITLES = "menu_titles";
    public static final String EXTRA_MENU_TITLE = "menu_title";
    public static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    public static final String EXTRA_MENU_CALLER_PACKAGE = "menu_caller_package";
    public static final String EXTRA_DIALOG_MESSAGE = "dialog_message";
    public static final String EXTRA_DIALOG_BUTTONS = "dialog_buttons";

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";
    private static final String OPEN_RECEIVER = SOLAR_PKG + ".PowerOverlayOpenReceiver";
    private static final String TRIGGER_RECEIVER = SOLAR_PKG + ".OverlayTriggerReceiver";
    private static final String APP_MENU_RECEIVER = SOLAR_PKG + ".AppMenuOverlayReceiver";
    private static final String NATIVE_DIALOG_RECEIVER = SOLAR_PKG + ".NativeDialogOverlayReceiver";
    private static final String USB_STORAGE_RECEIVER = SOLAR_PKG + ".UsbStorageOverlayReceiver";
    private static final String LAUNCHER_PICKER_RECEIVER = SOLAR_PKG + ".LauncherPickerOverlayReceiver";

    private SolarOverlayClient() {}

    public static final String ACTION_OPEN_CONTEXT_MENU =
            "com.solar.launcher.action.OPEN_CONTEXT_MENU";
    public static final String EXTRA_CONTEXT_POWER_HOLD = "context_power_hold";

    /** Y2 power-hold while Solar is foreground — in-app power tier, not :overlay. */
    public static void showInAppPowerMenu(Context ctx) {
        if (ctx == null) return;
        Intent launch = new Intent(ACTION_OPEN_CONTEXT_MENU);
        launch.setComponent(new ComponentName(SOLAR_PKG, SOLAR_PKG + ".MainActivity"));
        launch.putExtra(EXTRA_CONTEXT_POWER_HOLD, true);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            ctx.startActivity(launch);
            SolarContextBridge.log("showInAppPowerMenu startActivity");
        } catch (Throwable t) {
            SolarContextBridge.log("showInAppPowerMenu failed: " + t.getClass().getSimpleName());
        }
    }

    /** Y1 BACK-long / Y2 power-hold or BACK-long — startService first (works when app is stopped). */
    public static void showPowerOverlay(Context ctx) {
        setOverlayActiveEarly(true);
        warmOverlayProcess(ctx);
        if (startOverlayService(ctx, ACTION_SHOW_OVERLAY_POWER)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_POWER, OPEN_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_POWER);
    }

    /** Spin up :overlay early on BACK/power DOWN so hold threshold does not pay cold-start cost. */
    public static void warmOverlayProcess(Context ctx) {
        if (ctx == null) return;
        Intent keep = new Intent("com.solar.launcher.action.OVERLAY_KEEPALIVE");
        keep.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
        try {
            ctx.startService(keep);
        } catch (Throwable ignored) {}
    }

    public static void showVolumeOverlay(Context ctx) {
        // Passive volume HUD must not set overlay-active — that swallows HW keys in PWM without
        // a handler to apply MediaVolumeControl (keys consumed, level never changes).
        if (startOverlayService(ctx, ACTION_SHOW_OVERLAY_VOLUME)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_VOLUME, OPEN_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_VOLUME);
    }

    /** Replace stock HOME resolver — open broadcast works from ResolverActivity process. */
    public static void showLauncherPickerOverlay(Context ctx) {
        if (ctx == null) return;
        if (startOverlayService(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER, LAUNCHER_PICKER_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER);
    }

    /** Replace stock text context menu — startService with extras (broadcast can lag on JB). */
    public static void showAppMenu(Context ctx, String title, String[] itemTitles,
            String sessionId, String callerPackage) {
        if (ctx == null || itemTitles == null || itemTitles.length == 0) return;
        if (sessionId == null || sessionId.length() == 0) return;
        setOverlayActiveEarly(true);
        if (startAppMenuService(ctx, title, itemTitles, sessionId, callerPackage)) return;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_APP_MENU);
        intent.setComponent(new ComponentName(SOLAR_PKG, APP_MENU_RECEIVER));
        intent.putExtra(EXTRA_MENU_TITLES, itemTitles);
        intent.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (callerPackage != null) intent.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) intent.putExtra(EXTRA_MENU_TITLE, title);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("appMenu broadcast items=" + itemTitles.length);
        } catch (Throwable t) {
            SolarContextBridge.log("appMenu broadcast failed: " + t.getClass().getSimpleName());
        }
    }

    /** Direct :overlay service start — sets prop via SolarOverlayService before menu paints. */
    private static boolean startAppMenuService(Context ctx, String title, String[] itemTitles,
            String sessionId, String callerPackage) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_APP_MENU);
        svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
        svc.putExtra(EXTRA_MENU_TITLES, itemTitles);
        svc.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (callerPackage != null) svc.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) svc.putExtra(EXTRA_MENU_TITLE, title);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("appMenu startService items=" + itemTitles.length);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("appMenu startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Replace stock AlertDialog — scrollable detail body + wheel-friendly OK/Cancel rows. */
    public static void showNativeDialog(Context ctx, String title, String message, String[] buttons,
            String sessionId, String callerPackage) {
        if (ctx == null || sessionId == null || sessionId.length() == 0) return;
        if (buttons == null || buttons.length == 0) return;
        setOverlayActiveEarly(true);
        if (startNativeDialogService(ctx, title, message, buttons, sessionId, callerPackage)) return;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
        intent.setComponent(new ComponentName(SOLAR_PKG, NATIVE_DIALOG_RECEIVER));
        intent.putExtra(EXTRA_DIALOG_MESSAGE, message != null ? message : "");
        intent.putExtra(EXTRA_DIALOG_BUTTONS, buttons);
        intent.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (callerPackage != null) intent.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) intent.putExtra(EXTRA_MENU_TITLE, title);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("nativeDialog broadcast buttons=" + buttons.length);
        } catch (Throwable t) {
            SolarContextBridge.log("nativeDialog broadcast failed: " + t.getClass().getSimpleName());
        }
    }

    /** Replace SystemUI UsbStorageActivity enable prompt — wheel-friendly global overlay tier. */
    public static void showUsbStoragePrompt(Context ctx) {
        if (ctx == null) return;
        setOverlayActiveEarly(true);
        if (startUsbStorageService(ctx, false)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_USB_STORAGE, USB_STORAGE_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_USB_STORAGE);
    }

    /** UMS already exported — Solar USB lock only when Solar is foreground. */
    public static void bringSolarToUsbLockScreen(Context ctx) {
        if (ctx == null) return;
        String fg = SystemServerHooks.foregroundPackage(ctx);
        if (SystemServerHooks.shouldOfferOverlayForPackage(fg)) {
            SolarContextBridge.log("bringSolarToUsbLockScreen skip third-party fg=" + fg);
            return;
        }
        if (startUsbStorageService(ctx, true)) return;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_USB_STORAGE);
        intent.setComponent(new ComponentName(SOLAR_PKG, USB_STORAGE_RECEIVER));
        intent.putExtra(EXTRA_USB_STORAGE_LOCK, true);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("usb lock broadcast");
        } catch (Throwable t) {
            SolarContextBridge.log("usb lock broadcast failed: " + t.getClass().getSimpleName());
        }
    }

    /** Direct :overlay service for USB storage prompt or lock handoff. */
    private static boolean startUsbStorageService(Context ctx, boolean lockOnly) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_USB_STORAGE);
        svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
        svc.putExtra(EXTRA_USB_STORAGE_LOCK, lockOnly);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("usbStorage startService lock=" + lockOnly);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("usbStorage startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Direct :overlay service for native dialog replacement. */
    private static boolean startNativeDialogService(Context ctx, String title, String message,
            String[] buttons, String sessionId, String callerPackage) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
        svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
        svc.putExtra(EXTRA_DIALOG_MESSAGE, message != null ? message : "");
        svc.putExtra(EXTRA_DIALOG_BUTTONS, buttons);
        svc.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (callerPackage != null) svc.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) svc.putExtra(EXTRA_MENU_TITLE, title);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("nativeDialog startService buttons=" + buttons.length);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("nativeDialog startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Prop before startService — blocks handoff inject in main process during overlay boot. */
    private static void setOverlayActiveEarly(boolean active) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set",
                    OverlayKeyForwarder.ACTIVE_PROPERTY, active ? "1" : "0");
            SolarContextBridge.log("overlay prop early=" + (active ? "1" : "0"));
        } catch (Throwable t) {
            SolarContextBridge.log("overlay prop early failed: " + t.getClass().getSimpleName());
        }
    }

    /** Direct startService — reliable from system_server even when Solar main process is stopped. */
    private static boolean startOverlayService(Context ctx, String action) {
        if (ctx == null || action == null) return false;
        Intent svc = new Intent(action);
        svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
        try {
            ctx.startService(svc);
            SolarContextBridge.log("startService " + action);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static void deliverOpenBroadcast(Context ctx, String action, String receiverClass) {
        if (ctx == null || action == null || receiverClass == null) return;
        Intent open = new Intent(action);
        open.setComponent(new ComponentName(SOLAR_PKG, receiverClass));
        open.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(open);
            SolarContextBridge.log("open broadcast " + action);
        } catch (Throwable t) {
            SolarContextBridge.log("open broadcast failed: " + t.getClass().getSimpleName());
        }
    }

    private static void startOverlayFallback(Context ctx, String action) {
        if (ctx == null || action == null) return;
        Intent broadcast = new Intent(action);
        broadcast.setComponent(new ComponentName(SOLAR_PKG, TRIGGER_RECEIVER));
        deliver(ctx, broadcast);
    }

    private static void deliver(Context ctx, Intent intent) {
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent, PERMISSION);
            SolarContextBridge.log("broadcast " + intent.getAction());
            return;
        } catch (Throwable t) {
            SolarContextBridge.log("permission broadcast failed: " + t.getClass().getSimpleName());
        }
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("broadcast (open) " + intent.getAction());
        } catch (Throwable t) {
            SolarContextBridge.log("broadcast failed: " + t.getClass().getSimpleName());
        }
    }
}
