package com.solar.launcher.xposed.bridge.extract;

/**
 * Reads named fields from live framework objects — Xposed in production, maps in unit tests.
 */
public interface ReflectFields {

    /** @return field value or null when missing / unreadable */
    Object get(Object target, String fieldName);
}
