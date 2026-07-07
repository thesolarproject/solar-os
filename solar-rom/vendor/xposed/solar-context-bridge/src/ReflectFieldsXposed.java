package com.solar.launcher.xposed.bridge;

import com.solar.launcher.xposed.bridge.extract.ReflectFields;

/** Production {@link ReflectFields} backed by XposedHelpers.getObjectField. */
final class ReflectFieldsXposed implements ReflectFields {

    static final ReflectFields INSTANCE = new ReflectFieldsXposed();

    private ReflectFieldsXposed() {}

    @Override
    public Object get(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        try {
            return de.robv.android.xposed.XposedHelpers.getObjectField(target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
