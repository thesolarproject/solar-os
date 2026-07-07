package com.solar.launcher.xposed.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.inputmethod.InputConnection;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-05 — Tier-2 app-process InputConnection stash for IME text commit fallback.
 * Layman: remembers the typing pipe in each app so Solar can still insert letters if IME bind fails.
 * Technical: weak-ref IC keyed by package; ACTION_IME_COMMIT broadcast handler.
 */
final class ImeSessionHooks {

  static final String ACTION_IME_COMMIT = "com.solar.launcher.action.IME_COMMIT";
  static final String EXTRA_IME_TEXT = "ime_text";
  static final String EXTRA_IME_DELETE = "ime_delete";

  private static final Map<String, WeakReference<InputConnection>> SESSIONS =
      new ConcurrentHashMap<String, WeakReference<InputConnection>>();
  private static volatile boolean receiverRegistered;

  private ImeSessionHooks() {}

  static void install(LoadPackageParam lpparam) {
    if (lpparam == null || lpparam.packageName == null) return;
    if (lpparam.packageName.startsWith("com.solar.launcher")) return;
    hookInputConnection(lpparam);
  }

  private static void hookInputConnection(LoadPackageParam lpparam) {
    try {
      Class<?> view = XposedHelpers.findClass("android.view.View", lpparam.classLoader);
      XposedHookKit.hookAll(view, "onCreateInputConnection", new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
          Object ic = param.getResult();
          if (!(ic instanceof InputConnection)) return;
          String pkg = lpparam.packageName;
          SESSIONS.put(pkg, new WeakReference<InputConnection>((InputConnection) ic));
          View v = (View) param.thisObject;
          if (v != null) {
            Context app = v.getContext();
            if (app != null) {
              ensureCommitReceiver(app.getApplicationContext(), pkg);
            }
          }
        }
      });
      SolarContextBridge.log("hooked View.onCreateInputConnection in " + lpparam.packageName);
    } catch (Throwable t) {
      SolarContextBridge.log("ImeSessionHooks skip " + lpparam.packageName + ": "
          + t.getClass().getSimpleName());
    }
  }

  private static void ensureCommitReceiver(Context appCtx, final String pkg) {
    if (receiverRegistered) return;
    synchronized (ImeSessionHooks.class) {
      if (receiverRegistered) return;
      appCtx.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent == null || !ACTION_IME_COMMIT.equals(intent.getAction())) return;
          WeakReference<InputConnection> ref = SESSIONS.get(pkg);
          InputConnection ic = ref != null ? ref.get() : null;
          if (ic == null) return;
          boolean delete = intent.getBooleanExtra(EXTRA_IME_DELETE, false);
          if (delete) {
            try {
              ic.deleteSurroundingText(1, 0);
            } catch (Throwable ignored) {}
            return;
          }
          String text = intent.getStringExtra(EXTRA_IME_TEXT);
          if (text != null) {
            try {
              ic.commitText(text, 1);
            } catch (Throwable ignored) {}
          }
        }
      }, new IntentFilter(ACTION_IME_COMMIT));
      receiverRegistered = true;
    }
  }
}
