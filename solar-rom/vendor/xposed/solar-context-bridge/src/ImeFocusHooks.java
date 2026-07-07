package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Suppress stock LatinIME showSoftInput when Solar IME is the default.
 * Layman: stops the old Android keyboard from popping up over Solar's wheel strip.
 * Technical: hook IMM showSoftInput/hideSoftInputFromWindow in system_server + app processes.
 */
final class ImeFocusHooks {

  private static final String SOLAR_IME = "com.solar.launcher/.SolarInputMethodService";

  private ImeFocusHooks() {}

  static void installSystemServer(LoadPackageParam lpparam) {
    try {
      Class<?> imm = XposedHelpers.findClass("android.view.inputmethod.InputMethodManager", lpparam.classLoader);
      hookShowSoftInput(imm);
      SolarContextBridge.log("hooked IMM showSoftInput in system_server");
    } catch (Throwable t) {
      SolarContextBridge.log("ImeFocusHooks system skip: " + t.getClass().getSimpleName());
    }
  }

  static void installApp(LoadPackageParam lpparam) {
    if (lpparam == null || lpparam.packageName == null) return;
    if (lpparam.packageName.startsWith("com.solar.launcher")) return;
    try {
      Class<?> imm = XposedHelpers.findClass("android.view.inputmethod.InputMethodManager", lpparam.classLoader);
      hookShowSoftInput(imm);
    } catch (Throwable ignored) {}
  }

  private static void hookShowSoftInput(Class<?> imm) {
    XposedHookKit.hookAll(imm, "showSoftInput", new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param) {
        // 2026-07-06 — Block stock LatinIME while Solar tray owns the session.
        if (isSolarDefaultIme() && ImeKeyForwarder.isImeActive()) {
          param.setResult(false);
        }
      }
    });
    XposedHookKit.hookAll(imm, "hideSoftInputFromWindow", new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param) {
        // 2026-07-06 — Solar tray dismisses itself; stock IMM hide causes focus fights.
        if (ImeKeyForwarder.isImeActive()) {
          param.setResult(null);
        }
      }
    });
  }

  private static boolean isSolarDefaultIme() {
    try {
      Class<?> ctxCls = XposedHelpers.findClass("android.app.ActivityThread", null);
      Object app = XposedHelpers.callStaticMethod(ctxCls, "currentApplication");
      if (app != null) {
        Object cr = XposedHelpers.callMethod(app, "getContentResolver");
        Object v = XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.provider.Settings$Secure", null),
            "getString", cr, "default_input_method");
        String s = v != null ? String.valueOf(v) : "";
        if (s.length() > 0) return s.contains("com.solar.launcher");
      }
    } catch (Throwable ignored) {}
    try {
      Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
      Object v = XposedHelpers.callStaticMethod(sp, "get", "persist.sys.default_ime", "");
      return String.valueOf(v).contains("com.solar.launcher");
    } catch (Throwable t) {
      return true;
    }
  }
}
