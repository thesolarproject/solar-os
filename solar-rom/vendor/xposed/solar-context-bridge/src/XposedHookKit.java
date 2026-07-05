package com.solar.launcher.xposed.bridge;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;

/**
 * Hook via runtime XposedBridge reflection — compile stubs must not call hookAllMethods directly
 * (Y1/Y2 XposedBridge signatures differ and cause NoSuchMethodError at load time).
 */
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
            SolarContextBridge.log("XposedHookKit init failed: " + t.getClass().getSimpleName());
        }
    }

    private XposedHookKit() {}

    /** Attach a suppress/replace hook to every overload of {@code methodName}. */
    static int hookAllReplace(Class<?> cls, String methodName, final String logLabel) {
        return hookAll(cls, methodName, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                SolarContextBridge.log("SUPPRESSED " + logLabel);
                return null;
            }
        });
    }

    /** Attach a custom hook to every overload of {@code methodName}. */
    static int hookAll(Class<?> cls, String methodName, XC_MethodHook callback) {
        if (cls == null || methodName == null || callback == null) return 0;
        if (hookAllMethods != null) {
            try {
                hookAllMethods.invoke(null, cls, methodName, callback);
                SolarContextBridge.log("hookAllMethods OK " + cls.getSimpleName() + "." + methodName);
                return 1;
            } catch (Throwable t) {
                SolarContextBridge.log("hookAllMethods FAIL " + methodName + ": "
                        + t.getClass().getSimpleName());
            }
        }
        return hookDeclared(cls, methodName, callback);
    }

    /** Fallback: hook each declared overload via hookMethod(Member, callback). */
    static int hookDeclared(Class<?> cls, String methodName, XC_MethodHook callback) {
        if (hookMethod == null || cls == null) return 0;
        int n = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (!methodName.equals(m.getName())) continue;
            try {
                m.setAccessible(true);
                hookMethod.invoke(null, m, callback);
                SolarContextBridge.log("hookMethod OK " + cls.getSimpleName() + "." + methodName
                        + paramSig(m.getParameterTypes()));
                n++;
            } catch (Throwable t) {
                SolarContextBridge.log("hookMethod FAIL " + methodName + paramSig(m.getParameterTypes())
                        + ": " + t.getClass().getSimpleName());
            }
        }
        return n > 0 ? 1 : 0;
    }

    /** Hook one exact signature when the overload set is known. */
    static int hookExact(Class<?> cls, String methodName, XC_MethodHook callback, Class<?>... paramTypes) {
        if (hookMethod == null || cls == null) return 0;
        try {
            Method m = cls.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            hookMethod.invoke(null, m, callback);
            SolarContextBridge.log("hookExact OK " + methodName + paramSig(paramTypes));
            return 1;
        } catch (Throwable t) {
            SolarContextBridge.log("hookExact FAIL " + methodName + paramSig(paramTypes) + ": "
                    + t.getClass().getSimpleName());
            return hookDeclared(cls, methodName, callback);
        }
    }

    private static String paramSig(Class<?>... types) {
        return Arrays.toString(types);
    }
}
