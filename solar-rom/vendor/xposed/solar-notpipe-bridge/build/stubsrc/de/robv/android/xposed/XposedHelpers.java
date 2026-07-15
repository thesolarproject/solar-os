package de.robv.android.xposed;
public final class XposedHelpers {
    public static Class<?> findClass(String className, ClassLoader cl) { return null; }
    // 2026-07-14 — Must be void to match Y1/Y2 XposedBridge (Object/Unhook return → NoSuchMethodError at runtime).
    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {}
    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {}
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static int getIntField(Object obj, String fieldName) { return 0; }
    public static boolean getBooleanField(Object obj, String fieldName) { return false; }
}
