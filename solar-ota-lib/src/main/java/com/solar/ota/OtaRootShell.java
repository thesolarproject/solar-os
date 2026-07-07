package com.solar.ota;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * 2026-07-06 — Minimal root shell for OTA companion installs (shared by Solar + Updater).
 * Layman: runs privileged copy/pm install steps when the ROM grants su.
 * Technical: tries /system/xbin/su paths; mirrors SolarApkInstaller.runSu.
 */
final class OtaRootShell {
    private static final String[] SU_PATHS = {"/system/xbin/su", "/system/bin/su", "su"};
    private static volatile boolean locationPinned;

    private OtaRootShell() {}

    static boolean canRun() {
        return run("id");
    }

    static boolean run(String command) {
        if (command == null || command.isEmpty()) return false;
        for (String su : SU_PATHS) {
            Process proc = null;
            try {
                proc = Runtime.getRuntime().exec(new String[] {su, "-c", command});
                drain(proc.getErrorStream());
                if (proc.waitFor() == 0) return true;
            } catch (Exception ignored) {
            } finally {
                if (proc != null) proc.destroy();
            }
        }
        return false;
    }

    static String runCapture(String command) {
        if (command == null || command.isEmpty()) return null;
        for (String su : SU_PATHS) {
            Process proc = null;
            try {
                proc = Runtime.getRuntime().exec(new String[] {su, "-c", command});
                String out = readStream(proc.getInputStream());
                drain(proc.getErrorStream());
                if (proc.waitFor() == 0) return out;
            } catch (Exception ignored) {
            } finally {
                if (proc != null) proc.destroy();
            }
        }
        return null;
    }

    /** Pin pm to internal flash before companion pm installs (Y1 SD-card drift guard). */
    static void enforceInternalInstallLocation() {
        if (locationPinned) return;
        locationPinned = true;
        run("pm set-install-location 1");
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static void drain(InputStream in) {
        if (in == null) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(in));
            while (r.readLine() != null) { /* discard */ }
        } catch (Exception ignored) {}
    }
}
