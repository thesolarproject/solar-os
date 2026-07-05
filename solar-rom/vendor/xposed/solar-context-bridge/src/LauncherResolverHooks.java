package com.solar.launcher.xposed.bridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Silences stock HOME "Always / Just once" dialogs — wheel hardware cannot use them.
 * Opens whichever launcher the user already picked in Solar settings instead.
 */
final class LauncherResolverHooks {

    private static final String RESOLVER = "com.android.internal.app.ResolverActivity";
    private static final String CHOOSER = "com.android.internal.app.ChooserActivity";

    /** One hook attach per class loader — ResolverActivity is framework-wide. */
    private static final Set<ClassLoader> HOOKED =
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>());

    private LauncherResolverHooks() {}

    /** Install HOME resolver silencer in every non-Solar app process that loads ResolverActivity. */
    static void install(LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.classLoader == null) return;
        if ("com.solar.launcher".equals(lpparam.packageName)) return;
        synchronized (HOOKED) {
            if (HOOKED.contains(lpparam.classLoader)) return;
        }
        int n = hookResolverClass(lpparam, RESOLVER);
        n += hookResolverClass(lpparam, CHOOSER);
        if (n > 0) {
            synchronized (HOOKED) {
                HOOKED.add(lpparam.classLoader);
            }
            SolarContextBridge.log("HOME resolver hooks=" + n + " pkg=" + lpparam.packageName);
        }
    }

    private static int hookResolverClass(LoadPackageParam lpparam, String className) {
        try {
            Class<?> cls = XposedHelpers.findClass(className, lpparam.classLoader);
            return XposedHookKit.hookAll(cls, "onCreate", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof Activity)) return;
                    Activity activity = (Activity) param.thisObject;
                    if (!isHomeResolverIntent(activity.getIntent())) return;
                    if (isHomeApplyInProgress()) {
                        try {
                            activity.finish();
                        } catch (Throwable ignored) {}
                        SolarContextBridge.log("resolver skipped — home apply in progress");
                        return;
                    }
                    final Context ctx = activity.getApplicationContext();
                    try {
                        activity.finish();
                    } catch (Throwable ignored) {}
                    activity.getWindow().getDecorView().post(new Runnable() {
                        @Override
                        public void run() {
                            SolarLauncherSilencer.launchSavedHome(ctx);
                        }
                    });
                    SolarContextBridge.log("silenced HOME resolver in " + lpparam.packageName);
                }
            });
        } catch (Throwable t) {
            return 0;
        }
    }

    /** True when Android is asking the user to pick a HOME launcher (Always / Just once). */
    static boolean isHomeResolverIntent(Intent intent) {
        if (intent == null) return false;
        Intent target = extractResolvedIntent(intent);
        if (target == null) return false;
        if (!Intent.ACTION_MAIN.equals(target.getAction())) return false;
        return target.hasCategory(Intent.CATEGORY_HOME);
    }

    /** Matches {@link com.solar.launcher.LauncherPreference#PROP_HOME_APPLYING}. */
    private static boolean isHomeApplyInProgress() {
        try {
            Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object v = XposedHelpers.callStaticMethod(sp, "get",
                    "persist.solar.home.applying", "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Intent extractResolvedIntent(Intent wrapper) {
        if (wrapper == null) return null;
        try {
            if (wrapper.hasExtra(Intent.EXTRA_INTENT)) {
                Object extra = wrapper.getParcelableExtra(Intent.EXTRA_INTENT);
                if (extra instanceof Intent) return (Intent) extra;
            }
        } catch (Throwable ignored) {}
        if (Intent.ACTION_MAIN.equals(wrapper.getAction())
                && wrapper.hasCategory(Intent.CATEGORY_HOME)) {
            return wrapper;
        }
        return null;
    }
}
