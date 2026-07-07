package com.solar.launcher.xposed.rockbox.compat;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

/** Hook via runtime XposedBridge reflection — API 17/19 safe (2026-07-05). */
final class XposedHookKit {

    private static Method hookAllMethods;
    private static Method hookMethod;

    static {
        try {
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
            for (Method m : bridge.getDeclaredMethods()) {
                if ("hookAllMethods".equals(m.getName()) && m.getParameterTypes().length == 3) {
                    hookAllMethods = m;
                } else if ("hookMethod".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    hookMethod = m;
                }
            }
        } catch (Throwable t) {
            SolarRockboxCompat.log("XposedHookKit init failed: " + t.getClass().getSimpleName());
        }
    }

    private XposedHookKit() {}

    static int hookAll(Class<?> cls, String methodName, XC_MethodHook callback) {
        if (cls == null || methodName == null || callback == null) return 0;
        if (hookAllMethods != null) {
            try {
                hookAllMethods.invoke(null, cls, methodName, callback);
                return 1;
            } catch (Throwable ignored) {}
        }
        if (hookMethod == null) return 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) continue;
            try {
                m.setAccessible(true);
                hookMethod.invoke(null, m, callback);
                return 1;
            } catch (Throwable ignored) {}
        }
        return 0;
    }
}
