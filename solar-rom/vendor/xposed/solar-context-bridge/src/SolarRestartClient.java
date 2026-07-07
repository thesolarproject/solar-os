package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * OS-wide Solar restart — dismiss overlay then force-stop + relaunch HOME via system shell.
 */
public final class SolarRestartClient {

    public static final String ACTION_RESTART_SOLAR =
            "com.solar.launcher.action.RESTART_SOLAR";
    public static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";

    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String RESTART_RECEIVER = SOLAR_PKG + ".SolarRestartReceiver";

    private SolarRestartClient() {}

    /** Ultra-long BACK from system_server — works in Rockbox and stock apps. */
    public static void restartSolarApp(final Context ctx) {
        if (ctx == null) return;
        try {
            Intent dismiss = new Intent(ACTION_DISMISS_OVERLAY);
            dismiss.setComponent(new ComponentName(SOLAR_PKG, SOLAR_PKG + ".SolarOverlayService"));
            ctx.startService(dismiss);
        } catch (Throwable ignored) {}
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Runtime.getRuntime().exec(new String[]{"sh", "-c",
                            "am force-stop com.solar.launcher; "
                                    + "am start -a android.intent.action.MAIN "
                                    + "-c android.intent.category.HOME "
                                    + "-n com.solar.launcher/.MainActivity "
                                    + "-f 0x34000000"});
                    SolarContextBridge.log("restartSolar am exec");
                } catch (Throwable t) {
                    SolarContextBridge.log("restartSolar am failed: "
                            + t.getClass().getSimpleName());
                }
            }
        }, "SolarRestart").start();
        Intent intent = new Intent(ACTION_RESTART_SOLAR);
        intent.setComponent(new ComponentName(SOLAR_PKG, RESTART_RECEIVER));
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
        } catch (Throwable ignored) {}
    }
}
