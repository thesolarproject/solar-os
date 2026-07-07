package com.solar.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;

/**
 * Lazy dev mode helper: gates all debugging instrumentation behind the
 * "Enable Advance Debugging" setting AND physical USB/PC connection detection.
 */
public final class AdvancedDebuggingPolicy {
    public static final String PREF_ADVANCED_DEBUGGING = "settings.debug.advanced_debugging_enabled";
    private static volatile Context appCtx = null;

    private AdvancedDebuggingPolicy() {}

    public static void init(Context context) {
        if (context != null && appCtx == null) {
            appCtx = context.getApplicationContext();
        }
    }

    public static boolean isDebuggingAllowed() {
        if (appCtx == null) return false;
        return isDebuggingAllowed(appCtx);
    }

    public static boolean isDebuggingAllowed(Context ctx) {
        if (ctx == null) return false;
        try {
            SharedPreferences prefs = ctx.getSharedPreferences("com.solar.launcher_preferences", Context.MODE_PRIVATE);
            if (!prefs.getBoolean(PREF_ADVANCED_DEBUGGING, false)) {
                return false;
            }
            return isUsbConnected(ctx);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isUsbConnected(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent intent = ctx.registerReceiver(null, new IntentFilter("android.hardware.usb.action.USB_STATE"));
            if (intent != null && intent.getBooleanExtra("connected", false)) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            Intent batteryIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                if (plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_AC) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
