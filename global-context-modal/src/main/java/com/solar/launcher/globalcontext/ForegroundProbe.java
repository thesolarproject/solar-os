package com.solar.launcher.globalcontext;

import android.app.ActivityManager;

import java.util.List;

/**
 * 2026-07-10 — Best-effort foreground package for companion hold FSM at modal fire.
 * Layman: asks Android which app is on screen when the quick menu is about to open.
 * Technical: RunningTasks + IMPORTANCE_FOREGROUND fallback; null when probe fails.
 * Reversal: delete; coordinator uses DOWN-time fg from Xposed only.
 */
final class ForegroundProbe {

    private static volatile String cached;
    private static volatile long cachedAt;

    private ForegroundProbe() {}

    static String topPackage() {
        long now = android.os.SystemClock.uptimeMillis();
        if (cached != null && now - cachedAt < 250L) {
            return cached;
        }
        cached = topPackageUncached();
        cachedAt = now;
        return cached;
    }

    @SuppressWarnings("deprecation")
    private static String topPackageUncached() {
        try {
            ActivityManager am = (ActivityManager) Class.forName(
                    "android.app.ActivityManagerNative")
                    .getMethod("getDefault").invoke(null);
            if (am == null) return null;
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks != null && !tasks.isEmpty()) {
                ActivityManager.RunningTaskInfo top = tasks.get(0);
                if (top.baseActivity != null) {
                    return top.baseActivity.getPackageName();
                }
                if (top.topActivity != null) {
                    return top.topActivity.getPackageName();
                }
            }
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo proc : procs) {
                    if (proc.importance
                            != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        continue;
                    }
                    if (proc.pkgList != null && proc.pkgList.length > 0) {
                        return proc.pkgList[0];
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
