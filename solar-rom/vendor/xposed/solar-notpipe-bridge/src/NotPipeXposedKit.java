package com.solar.launcher.xposed.notpipe;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

/**
 * 2026-07-14 — Runtime-reflect XposedBridge.hookMethod (same idea as context-bridge XposedHookKit).
 * Layman: talk to Xposed the way this ROM actually exposes it, not the compile stub signature.
 * Technical: findAndHookMethod stubs cause NoSuchMethodError on Y1/Y2; hookMethod(Member) works.
 * Reversal: call XposedHelpers.findAndHookMethod again if bridge rebuilds against matching API.
 */
final class NotPipeXposedKit {

    private static Method hookMethod;

    static {
        try {
            Class<?> bridge = Class.forName("de.robv.android.xposed.XposedBridge");
            for (Method m : bridge.getDeclaredMethods()) {
                if ("hookMethod".equals(m.getName()) && m.getParameterTypes().length == 2) {
                    hookMethod = m;
                    break;
                }
            }
        } catch (Throwable t) {
            SolarNotPipeBridge.log("NotPipeXposedKit init failed: " + t);
        }
    }

    private NotPipeXposedKit() {}

    /** Hook declared overloads of methodName on cls. */
    static int hookDeclared(Class<?> cls, String methodName, XC_MethodHook callback) {
        if (hookMethod == null || cls == null || methodName == null || callback == null) return 0;
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) continue;
            try {
                m.setAccessible(true);
                hookMethod.invoke(null, m, callback);
                n++;
            } catch (Throwable t) {
                SolarNotPipeBridge.log("hookDeclared FAIL " + methodName + ": " + t);
            }
        }
        return n;
    }

    /** Hook exact signature; falls back to all declared overloads. */
    static int hookExact(Class<?> cls, String methodName, XC_MethodHook callback,
            Class<?>... paramTypes) {
        if (hookMethod == null || cls == null) return 0;
        try {
            Method m = cls.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            hookMethod.invoke(null, m, callback);
            return 1;
        } catch (Throwable t) {
            return hookDeclared(cls, methodName, callback);
        }
    }
}
