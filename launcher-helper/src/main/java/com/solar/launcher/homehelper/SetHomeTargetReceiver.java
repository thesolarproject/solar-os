package com.solar.launcher.homehelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Shell/helper broadcast entry to update persist HOME target without Solar prefs.
 * Layman: other apps can tell the middle-man which launcher the user picked.
 * Technical: writes persist.solar.home.target (+ optional component prop).
 */
public final class SetHomeTargetReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!HomeTargetPolicy.ACTION_SET_HOME_TARGET.equals(intent.getAction())) return;
        String target = intent.getStringExtra(HomeTargetPolicy.EXTRA_HOME_TARGET);
        if (target == null) return;
        target = HomeTargetPolicy.normalizeTarget(target);
        setSystemProperty(HomeTargetPolicy.PROP_HOME_TARGET, target);
        String component = intent.getStringExtra(HomeTargetPolicy.EXTRA_HOME_COMPONENT);
        if (component != null && component.length() > 0) {
            setSystemProperty(HomeTargetPolicy.PROP_HOME_COMPONENT, component);
        }
    }

    static void setSystemProperty(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
        } catch (Exception ignored) {}
    }
}
