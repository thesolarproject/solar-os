package com.solar.launcher.globalcontext;

/**
 * 2026-07-06 — Root evdev daemon entry owned by companion APK (Phase 2c).
 * Layman: the hold-to-menu keypad watcher starts from the helper app, not only Solar.
 * Technical: app_process main with CLASSPATH=companion:solar; delegates to Solar evdev loop.
 * Reversal: point solar-rescue-daemon.sh back at com.solar.launcher.GlobalOverlayTriggerMain.
 */
public final class CompanionRootInputDaemon {

    private static final String SOLAR_DAEMON = "com.solar.launcher.GlobalOverlayTriggerMain";

    private CompanionRootInputDaemon() {}

    /** app_process entry — requires Solar APK on classpath after companion. */
    public static void main(String[] args) {
        try {
            Class<?> daemon = Class.forName(SOLAR_DAEMON);
            daemon.getMethod("main", String[].class).invoke(null, (Object) args);
        } catch (Throwable t) {
            System.err.println("CompanionRootInputDaemon: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
