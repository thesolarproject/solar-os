package com.solar.launcher.xposed.powermenu;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Y2 (API 19) test module: block stock GlobalActions / power menu on lock-button long-press.
 * MTK ROMs expose methods with signatures findAndHookMethod(name-only) misses — enumerate + hookMethod.
 */
public final class PowerMenuTest implements IXposedHookLoadPackage {

    private static final String TAG = "SolarPMTest";
    private static final String PWM = "com.android.internal.policy.impl.PhoneWindowManager";
    private static final String GLOBAL_ACTIONS = "com.android.internal.policy.impl.GlobalActions";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        log("module loaded into system_server");

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(PWM, lpparam.classLoader);
        } catch (Throwable t) {
            log("FAILED to find PhoneWindowManager: " + t);
            return;
        }
        log("PhoneWindowManager=" + pwm.getName());
        logDeclaredMethods(pwm, "Global", "Power", "global", "power");

        int hooked = 0;
        // Primary KitKat entry — ()V on Y2 android.policy.jar (dexdump verified).
        hooked += hookAllByName(pwm, "showGlobalActionsDialog", true);
        hooked += hookByReflection(pwm, "showGlobalActionsInternal", true);
        hooked += hookByReflection(pwm, "interceptPowerKeyDown", false, boolean.class);

        Class<?> globalActions;
        try {
            globalActions = XposedHelpers.findClass(GLOBAL_ACTIONS, lpparam.classLoader);
            log("GlobalActions=" + globalActions.getName());
            hooked += hookAllByName(globalActions, "showDialog", true);
            hooked += hookAllByName(globalActions, "handleShow", true);
            hooked += hookAllByName(globalActions, "prepareDialog", true);
        } catch (Throwable t) {
            log("GlobalActions not found: " + t.getClass().getSimpleName());
        }

        log("total hooks attached=" + hooked);
    }

    /** hookAllMethods — catches every overload for this name on MTK/AOSP variants. */
    private int hookAllByName(Class<?> cls, String methodName, boolean suppress) {
        try {
            XposedBridge.hookAllMethods(cls, methodName, suppress ? suppressHook(methodName) : observeHook(methodName));
            log("hookAllMethods OK " + cls.getSimpleName() + "." + methodName);
            return 1;
        } catch (Throwable t) {
            log("hookAllMethods FAIL " + methodName + ": " + t.getClass().getSimpleName());
            return hookByReflection(cls, methodName, suppress);
        }
    }

    /** Fallback: declared method with explicit param types via XposedBridge.hookMethod. */
    private int hookByReflection(Class<?> cls, String methodName, boolean suppress, Class<?>... paramTypes) {
        try {
            Method m = cls.getDeclaredMethod(methodName, paramTypes);
            XposedBridge.hookMethod(m, suppress ? suppressHook(methodName) : observeHook(methodName));
            log("hookMethod OK " + methodName + paramSig(paramTypes));
            return 1;
        } catch (Throwable t) {
            log("hookMethod FAIL " + methodName + paramSig(paramTypes) + ": " + t.getClass().getSimpleName());
            return 0;
        }
    }

    private static XC_MethodReplacement suppressHook(final String label) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                log("SUPPRESSED " + label + "()");
                return null;
            }
        };
    }

    private static XC_MethodHook observeHook(final String label) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                log("ENTER " + label + "()");
            }
        };
    }

    private static void logDeclaredMethods(Class<?> cls, String... needles) {
        for (Method m : cls.getDeclaredMethods()) {
            String n = m.getName();
            for (String needle : needles) {
                if (n.contains(needle)) {
                    log("declared " + n + paramSig(m.getParameterTypes()));
                    break;
                }
            }
        }
    }

    private static String paramSig(Class<?>... types) {
        return Arrays.toString(types);
    }

    private static void log(String msg) {
        Log.i(TAG, msg);
        XposedBridge.log(TAG + ": " + msg);
    }
}
