package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * App-process hooks: replace stock text context menus with Solar overlay rows.
 * Selection returns via {@link #ACTION_APP_MENU_RESULT} — never parcel a ResultReceiver
 * (Solar cannot unmarshal anonymous bridge classes from other APK class loaders).
 */
final class AppMenuHooks {

    private static final String MENU_DIALOG = "com.android.internal.view.menu.MenuDialogHelper";
    private static final String MENU_POPUP = "com.android.internal.view.menu.MenuPopupHelper";
    private static final String MENU_BUILDER = "com.android.internal.view.menu.MenuBuilder";

    /** Must match {@link com.solar.launcher.OverlayTriggers#ACTION_APP_MENU_RESULT}. */
    static final String ACTION_APP_MENU_RESULT =
            "com.solar.launcher.action.APP_MENU_RESULT";
    static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    static final String EXTRA_SELECTED_INDEX = "menu_selected_index";

    private static final ConcurrentHashMap<String, PendingMenu> PENDING =
            new ConcurrentHashMap<String, PendingMenu>();

    private static volatile boolean resultReceiverRegistered;

    private static final class PendingMenu {
        final Object menuRef;
        final ArrayList<Object> items;

        PendingMenu(Object menuRef, ArrayList<Object> items) {
            this.menuRef = menuRef;
            this.items = items;
        }
    }

    private AppMenuHooks() {}

    static void install(LoadPackageParam lpparam) {
        hookHelperShow(MENU_DIALOG, "show", lpparam);
        hookHelperShow(MENU_POPUP, "tryShow", lpparam);
        hookHelperShow(MENU_POPUP, "show", lpparam);
        try {
            Class<?> builder = XposedHelpers.findClass(MENU_BUILDER, lpparam.classLoader);
            XposedHookKit.hookAll(builder, "performShow", interceptFromMenuBuilder(lpparam.classLoader));
            SolarContextBridge.log("hooked MenuBuilder.performShow in " + lpparam.packageName);
        } catch (Throwable t) {
            SolarContextBridge.log("MenuBuilder skip " + lpparam.packageName + ": " + t.getClass().getSimpleName());
        }
    }

    private static void hookHelperShow(String className, String methodName, LoadPackageParam lpparam) {
        try {
            Class<?> helper = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedHookKit.hookAll(helper, methodName, interceptFromHelper(lpparam.classLoader));
            SolarContextBridge.log("hooked " + className + "." + methodName + " in " + lpparam.packageName);
        } catch (Throwable t) {
            SolarContextBridge.log(className + "." + methodName + " skip " + lpparam.packageName + ": "
                    + t.getClass().getSimpleName());
        }
    }

    /** One receiver per hooked app process — forwards primitive selection back to the stock Menu. */
    private static void ensureResultReceiver(Context appCtx) {
        if (resultReceiverRegistered) return;
        synchronized (AppMenuHooks.class) {
            if (resultReceiverRegistered) return;
            appCtx.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent == null || !ACTION_APP_MENU_RESULT.equals(intent.getAction())) return;
                    String sessionId = intent.getStringExtra(EXTRA_MENU_SESSION_ID);
                    if (sessionId == null) return;
                    int index = intent.getIntExtra(EXTRA_SELECTED_INDEX, -1);
                    PendingMenu pending = PENDING.remove(sessionId);
                    if (pending == null || index < 0 || index >= pending.items.size()) return;
                    try {
                        XposedHelpers.callMethod(pending.menuRef, "performItemAction",
                                pending.items.get(index), 0);
                    } catch (Throwable t) {
                        SolarContextBridge.log("performItemAction failed: " + t);
                    }
                }
            }, new IntentFilter(ACTION_APP_MENU_RESULT));
            resultReceiverRegistered = true;
        }
    }

    private static XC_MethodReplacement interceptFromHelper(final ClassLoader cl) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Object menu = resolveMenuFromHelper(param.thisObject);
                return interceptMenu(menu, cl) ? null : null;
            }
        };
    }

    private static XC_MethodReplacement interceptFromMenuBuilder(final ClassLoader cl) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return interceptMenu(param.thisObject, cl) ? null : null;
            }
        };
    }

    /** @return true when Solar overlay replaced the stock menu. */
    private static boolean interceptMenu(Object menu, ClassLoader cl) {
        if (menu == null) return false;
        try {
            List<?> visible = (List<?>) XposedHelpers.callMethod(menu, "getVisibleItems");
            if (visible == null || visible.isEmpty()) return false;
            final ArrayList<Object> items = new ArrayList<Object>(visible);
            String[] titles = new String[items.size()];
            for (int i = 0; i < items.size(); i++) {
                Object item = items.get(i);
                CharSequence t = (CharSequence) XposedHelpers.callMethod(item, "getTitle");
                titles[i] = t != null ? t.toString() : "";
            }
            Context ctx = (Context) XposedHelpers.callMethod(menu, "getContext");
            if (ctx == null) return false;
            String sessionId = UUID.randomUUID().toString();
            PENDING.put(sessionId, new PendingMenu(menu, items));
            ensureResultReceiver(ctx.getApplicationContext());
            String callerPackage = ctx.getPackageName();
            SolarOverlayClient.showAppMenu(ctx, null, titles, sessionId, callerPackage);
            SolarContextBridge.log("menu overlay items=" + titles.length + " pkg="
                    + (callerPackage != null ? callerPackage : "?"));
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("menu intercept error: " + t);
            return false;
        }
    }

    /** MenuDialogHelper / MenuPopupHelper store MenuBuilder under mMenu on AOSP and MTK. */
    private static Object resolveMenuFromHelper(Object helper) {
        if (helper == null) return null;
        String[] fields = {"mMenu", "mMenuBuilder"};
        for (String field : fields) {
            try {
                Object menu = XposedHelpers.getObjectField(helper, field);
                if (menu != null) return menu;
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
