package com.solar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 2026-07-05 — Degraded launcher when Solar crash loop sets emergency_mode after reboot.
 * Layman: explains Solar could not start and offers app picker, versions, or streak reset.
 * Technical: lightweight Activity — companion EmergencyRockboxMode replaces when installed.
 * Reversal: delete Activity; boot always starts MainActivity.
 */
public class EmergencyRecoveryActivity extends Activity {

    /** No user-facing platform repair — silent prep only (2026-07-11). */
    private static final String[] MENU = {"versions", "launcher", "clear", "retry"};
    private int focusIndex;
    private LinearLayout menuRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        menuRoot = new LinearLayout(this);
        menuRoot.setOrientation(LinearLayout.VERTICAL);
        menuRoot.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText(getString(R.string.emergency_recovery_title));
        title.setTextSize(18);
        menuRoot.addView(title);

        TextView body = new TextView(this);
        body.setText(getString(R.string.emergency_recovery_body));
        body.setTextSize(14);
        body.setPadding(0, 16, 0, 24);
        menuRoot.addView(body);

        for (int i = 0; i < MENU.length; i++) {
            TextView row = new TextView(this);
            row.setTag(MENU[i]);
            row.setText(labelFor(MENU[i]));
            row.setTextSize(16);
            row.setPadding(8, 12, 8, 12);
            row.setFocusable(true);
            menuRoot.addView(row);
        }
        scroll.addView(menuRoot);
        setContentView(scroll);
        updateFocus();
    }

    private String labelFor(String id) {
        if ("versions".equals(id)) return getString(R.string.emergency_recovery_versions);
        if ("launcher".equals(id)) return getString(R.string.emergency_recovery_launcher);
        if ("clear".equals(id)) return getString(R.string.emergency_recovery_clear);
        return getString(R.string.emergency_recovery_retry);
    }

    private void updateFocus() {
        if (menuRoot == null) return;
        for (int i = 0; i < menuRoot.getChildCount(); i++) {
            View v = menuRoot.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            if (v.getTag() == null) continue;
            boolean sel = i - 2 == focusIndex;
            v.setBackgroundColor(sel ? 0x44FFFFFF : 0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (focusIndex < MENU.length - 1) focusIndex++;
            updateFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (focusIndex > 0) focusIndex--;
            updateFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            performAction(MENU[focusIndex]);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void performAction(String id) {
        if ("versions".equals(id)) {
            launchSolarVersionsApp();
            return;
        }
        if ("launcher".equals(id)) {
            startActivity(new Intent(this, PackageLauncherActivity.class));
            return;
        }
        if ("clear".equals(id)) {
            SolarRecoveryCoordinator.clearEmergencyState(this);
            retrySolar();
            return;
        }
        if ("retry".equals(id)) {
            retrySolar();
        }
    }

    private void retrySolar() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    /** Open standalone Solar Versions app — rollback path when launcher will not start. 2026-07-05 */
    private void launchSolarVersionsApp() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.solar.updater");
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        }
        android.widget.Toast.makeText(this, getString(R.string.emergency_recovery_versions_missing),
                android.widget.Toast.LENGTH_LONG).show();
    }
}
