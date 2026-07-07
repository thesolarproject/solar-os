package com.solar.launcher.radio.fm;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.io.File;
import java.lang.reflect.Method;

/**
 * 2026-07-06 — Resolve MediaTek {@code FMRadioNative} JNI stubs from ROM jars/APK.
 * Layman: finds the hidden FM driver class before we talk to the chip.
 * Technical: PathClassLoader over framework jars or FMRadio.apk; loads libfmjni.so.
 */
final class FmNativeLoader {
  private static final String NATIVE_CLASS_JJ = "com.mediatek.FMRadio.FMRadioNative";
  private static final String NATIVE_CLASS_AOSP = "com.mediatek.fmradio.FmRadioNative";

  private static final String[] FRAMEWORK_JARS = {
    "/system/framework/mediatek-framework.jar",
    "/system/framework/custom_ext.jar",
    "/system/framework/com.mediatek.hardware.jar"
  };

  private static final String[] FM_PACKAGES = {
    "com.mediatek.FMRadio", "com.innioasis.fm", "com.innioasis.y1"
  };

  private final Class<?> nativeClass;
  private final String loadError;

  FmNativeLoader(Context ctx) {
    Class<?> resolved = null;
    String err = "";
    try {
      resolved = Class.forName(NATIVE_CLASS_JJ);
    } catch (Throwable t1) {
      try {
        resolved = Class.forName(NATIVE_CLASS_AOSP);
      } catch (Throwable t2) {
        resolved = resolveFromFrameworkJars();
        if (resolved == null) {
          resolved = resolveFromPackages(ctx);
        }
      }
    }
    if (resolved != null) {
      loadNativeLibrary();
    } else {
      err = "FMRadioNative driver missing";
    }
    nativeClass = resolved;
    loadError = err;
  }

  boolean isReady() {
    return nativeClass != null;
  }

  String loadError() {
    return loadError;
  }

  Class<?> nativeClass() {
    return nativeClass;
  }

  Method method(String name, Class<?>... types) throws NoSuchMethodException {
    Class<?> clazz = nativeClass;
    while (clazz != null) {
      try {
        Method m = clazz.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
      } catch (NoSuchMethodException e) {
        clazz = clazz.getSuperclass();
      }
    }
    throw new NoSuchMethodException(name);
  }

  Object invokeStatic(String name, Class<?>[] types, Object[] args) throws Exception {
    return method(name, types).invoke(null, args);
  }

  private static Class<?> resolveFromFrameworkJars() {
    for (String path : FRAMEWORK_JARS) {
      if (!new File(path).exists()) continue;
      try {
        dalvik.system.PathClassLoader cl =
            new dalvik.system.PathClassLoader(path, ClassLoader.getSystemClassLoader());
        try {
          return Class.forName(NATIVE_CLASS_JJ, true, cl);
        } catch (Throwable ignored) {
          return Class.forName(NATIVE_CLASS_AOSP, true, cl);
        }
      } catch (Throwable ignored) {}
    }
    return null;
  }

  private static Class<?> resolveFromPackages(Context ctx) {
    if (ctx == null) return null;
    for (String pkg : FM_PACKAGES) {
      try {
        ApplicationInfo info = ctx.getPackageManager().getApplicationInfo(pkg, 0);
        dalvik.system.PathClassLoader cl =
            new dalvik.system.PathClassLoader(info.sourceDir, ClassLoader.getSystemClassLoader());
        try {
          return Class.forName(NATIVE_CLASS_JJ, true, cl);
        } catch (Throwable ignored) {
          return Class.forName(NATIVE_CLASS_AOSP, true, cl);
        }
      } catch (Throwable ignored) {}
    }
    return null;
  }

  private static void loadNativeLibrary() {
    try {
      System.loadLibrary("fmjni");
    } catch (Throwable t) {
      try {
        System.load("/system/lib/libfmjni.so");
      } catch (Throwable ignored) {}
    }
  }
}
