package com.solar.launcher.xposed.rockbox.ime;

import java.lang.reflect.Method;
import java.util.Arrays;

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
            SolarRockboxIme.log("XposedHookKit init failed: " + t.getClass().getSimpleName());
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
        return hookDeclared(cls, methodName, callback);
    }

    static int hookDeclared(Class<?> cls, String methodName, XC_MethodHook callback) {
        if (hookMethod == null || cls == null) return 0;
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) continue;
            try {
                m.setAccessible(true);
                hookMethod.invoke(null, m, callback);
                n++;
            } catch (Throwable ignored) {}
        }
        return n > 0 ? 1 : 0;
    }

    static Object defaultReturnValue(XC_MethodHook.MethodHookParam param) {
        try {
            Object member = XposedHelpers.getObjectField(param, "method");
            if (!(member instanceof Method)) return null;
            Class<?> ret = ((Method) member).getReturnType();
            if (ret == boolean.class || ret == Boolean.class) return Boolean.FALSE;
            if (ret == int.class || ret == Integer.class) return 0;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String paramSig(Class<?>... types) {
        return Arrays.toString(types);
    }
}
