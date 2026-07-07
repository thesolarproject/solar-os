package de.robv.android.xposed;
public final class XposedBridge {
    public static void log(String text) {}
    public static XC_MethodHook.Unhook hookMethod(java.lang.reflect.Member hookMethod, XC_MethodHook callback) { return null; }
    public static void hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {}
}
