package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.solar.launcher.xposed.bridge.extract.MenuExtract;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * App-process hooks: replace stock text context menus with Solar overlay rows.
 * Selection returns via {@link #ACTION_APP_MENU_RESULT} — never parcel a ResultReceiver
 * (Solar cannot unmarshal anonymous bridge classes from other APK class loaders).
 * Fail-open: when Solar is missing or overlay cannot start, stock Holo menus run unchanged.
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
    /** Must match {@link com.solar.launcher.OverlayTriggers#EXTRA_MENU_HAS_SUBMENU}. */
    static final String EXTRA_MENU_HAS_SUBMENU = "menu_has_submenu";

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

    /** Hook menu dialog/popup show only — MenuBuilder.performShow must finish before we replace UI. */
    static void install(LoadPackageParam lpparam) {
        hookHelperShow(MENU_DIALOG, "show", lpparam);
        hookHelperShow(MENU_POPUP, "show", lpparam);
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

    /** One receiver per hooked app process — forwards selection or cancel back to the stock Menu. */
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
                    SolarContextBridge.log("appMenu result idx=" + index + " session="
                            + sessionId.substring(0, Math.min(8, sessionId.length())));
                    PendingMenu pending = PENDING.remove(sessionId);
                    if (pending == null) {
                        SolarContextBridge.log("appMenu result stale session");
                        return;
                    }
                    try {
                        if (index < 0) {
                            closeMenu(pending.menuRef);
                            return;
                        }
                        if (index >= pending.items.size()) {
                            SolarContextBridge.log("appMenu result index oob=" + index);
                            return;
                        }
                        Object item = pending.items.get(index);
                        // #region agent log
                        try {
                            JSONObject d = new JSONObject();
                            d.put("idx", index);
                            d.put("itemCount", pending.items.size());
                            Debug32618Log.event("AppMenuHooks.onReceive", "deliver selection", "F", d);
                        } catch (Throwable ignored) {}
                        // #endregion
                        if (!invokeMenuItemSelection(pending.menuRef, item)) {
                            SolarContextBridge.log("appMenu invoke failed idx=" + index);
                        }
                    } catch (Throwable t) {
                        SolarContextBridge.log("performItemAction failed: " + t);
                    }
                }
            }, new IntentFilter(ACTION_APP_MENU_RESULT));
            resultReceiverRegistered = true;
        }
    }

    /** Dismiss the stock menu builder when the user backs out of the Solar overlay. */
    private static void closeMenu(Object menuRef) {
        if (menuRef == null) return;
        try {
            XposedHelpers.callMethod(menuRef, "close");
        } catch (Throwable t) {
            try {
                XposedHelpers.callMethod(menuRef, "cancel");
            } catch (Throwable ignored) {
                SolarContextBridge.log("menu close failed: " + t.getClass().getSimpleName());
            }
        }
    }

    private static XC_MethodHook interceptFromHelper(final ClassLoader cl) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object menu = resolveMenuFromHelper(param.thisObject);
                if (interceptMenu(menu, cl)) {
                    XposedHookKit.skipMethod(param);
                }
            }
        };
    }

    /**
     * Deliver the user's overlay pick into the stock Menu — try performItemAction first, then
     * item.invoke / OnMenuItemClickListener when the menu was torn down after overlay paint.
     */
    private static boolean invokeMenuItemSelection(Object menuRef, Object item) {
        if (menuRef == null || item == null) return false;
        try {
            Object r = XposedHelpers.callMethod(menuRef, "performItemAction", item, 0);
            if (Boolean.TRUE.equals(r)) {
                // #region agent log
                Debug32618Log.event("AppMenuHooks.invoke", "performItemAction ok", "G", null);
                // #endregion
                return true;
            }
        } catch (Throwable t) {
            SolarContextBridge.log("performItemAction: " + t.getClass().getSimpleName());
        }
        try {
            Object r = XposedHelpers.callMethod(item, "invoke", menuRef, 0);
            if (Boolean.TRUE.equals(r)) {
                Debug32618Log.event("AppMenuHooks.invoke", "item.invoke ok", "G", null);
                return true;
            }
        } catch (Throwable t) {
            SolarContextBridge.log("item.invoke: " + t.getClass().getSimpleName());
        }
        try {
            Object cb = XposedHelpers.getObjectField(menuRef, "mCallback");
            if (cb != null) {
                Object r = XposedHelpers.callMethod(cb, "onMenuItemSelected", menuRef, item);
                if (Boolean.TRUE.equals(r)) {
                    Debug32618Log.event("AppMenuHooks.invoke", "mCallback ok", "G", null);
                    return true;
                }
            }
        } catch (Throwable t) {
            SolarContextBridge.log("mCallback: " + t.getClass().getSimpleName());
        }
        try {
            Object listener = XposedHelpers.getObjectField(item, "mClickListener");
            if (listener != null) {
                Object r = XposedHelpers.callMethod(listener, "onMenuItemClick", item);
                if (Boolean.TRUE.equals(r)) {
                    Debug32618Log.event("AppMenuHooks.invoke", "mClickListener ok", "G", null);
                    return true;
                }
            }
        } catch (Throwable t) {
            SolarContextBridge.log("mClickListener: " + t.getClass().getSimpleName());
        }
        return false;
    }

    /** @return true when Solar overlay replaced the stock menu and delivery succeeded. */
    private static boolean interceptMenu(Object menu, ClassLoader cl) {
        if (menu == null) return false;
        try {
            MenuExtract.Snapshot snap = MenuExtract.fromMenu(new MenuBuilderReader(menu));
            if (snap.size() == 0) return false;
            List<?> visible = (List<?>) XposedHelpers.callMethod(menu, "getVisibleItems");
            final ArrayList<Object> items = new ArrayList<Object>(visible);
            String[] titles = new String[snap.size()];
            boolean[] hasSubmenu = new boolean[snap.size()];
            for (int i = 0; i < snap.size(); i++) {
                titles[i] = snap.rows[i].title;
                hasSubmenu[i] = snap.rows[i].hasSubmenu;
            }
            Context ctx = (Context) XposedHelpers.callMethod(menu, "getContext");
            if (ctx == null) return false;
            if (!SolarOverlayClient.canDeliverOverlay(ctx)) return false;
            String sessionId = UUID.randomUUID().toString();
            PENDING.put(sessionId, new PendingMenu(menu, items));
            ensureResultReceiver(ctx.getApplicationContext());
            String callerPackage = ctx.getPackageName();
            if (!SolarOverlayClient.showAppMenu(ctx, null, titles, hasSubmenu, sessionId, callerPackage)) {
                PENDING.remove(sessionId);
                return false;
            }
            markMenuShowing(menu);
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("items", titles.length);
                d.put("pkg", callerPackage != null ? callerPackage : "");
                Debug32618Log.event("AppMenuHooks.interceptMenu", "overlay replaced dialog", "H", d);
            } catch (Throwable ignored) {}
            // #endregion
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

    /** MenuBuilder.performShow sets this before MenuDialogHelper.show — keep state if we skip the dialog. */
    private static void markMenuShowing(Object menu) {
        if (menu == null) return;
        try {
            java.lang.reflect.Field f = menu.getClass().getDeclaredField("mIsShowing");
            f.setAccessible(true);
            f.setBoolean(menu, true);
        } catch (Throwable ignored) {}
    }
}
