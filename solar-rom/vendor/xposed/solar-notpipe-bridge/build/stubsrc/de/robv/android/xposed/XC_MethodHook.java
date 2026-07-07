package de.robv.android.xposed;
public abstract class XC_MethodHook {
    public static class Unhook {}
    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public void setResult(Object result) {}
        public Object getResult() { return null; }
        public Throwable getThrowable() { return null; }
        public boolean hasThrowable() { return false; }
    }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
}
