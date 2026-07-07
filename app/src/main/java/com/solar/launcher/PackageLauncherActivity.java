package com.solar.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 2026-07-05 — Minimal installed-app picker for emergency recovery tier.
 * Layman: scroll wheel picks any installed app to open when Solar will not start.
 * Technical: lists user-visible packages; launches MAIN/LAUNCHER intent.
 * Reversal: delete; emergency screen only offers repair + retry.
 */
public class PackageLauncherActivity extends Activity {

    private final List<String> labels = new ArrayList<String>();
    private final List<String> packages = new ArrayList<String>();
    private int focusIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPackages();
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        TextView title = new TextView(this);
        title.setText(getString(R.string.emergency_package_launcher_title));
        title.setTextSize(16);
        root.addView(title);
        for (int i = 0; i < labels.size(); i++) {
            TextView row = new TextView(this);
            row.setText(labels.get(i));
            row.setTextSize(14);
            row.setPadding(8, 10, 8, 10);
            root.addView(row);
        }
        scroll.addView(root);
        setContentView(scroll);
        root.setTag(root);
    }

    private void loadPackages() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<ApplicationInfo> user = new ArrayList<ApplicationInfo>();
        for (ApplicationInfo info : apps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || pm.getLaunchIntentForPackage(info.packageName) != null) {
                user.add(info);
            }
        }
        Collections.sort(user, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                CharSequence la = pm.getApplicationLabel(a);
                CharSequence lb = pm.getApplicationLabel(b);
                return String.valueOf(la).compareToIgnoreCase(String.valueOf(lb));
            }
        });
        for (ApplicationInfo info : user) {
            Intent launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null) continue;
            packages.add(info.packageName);
            labels.add(String.valueOf(pm.getApplicationLabel(info)));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (labels.isEmpty()) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (focusIndex < labels.size() - 1) focusIndex++;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (focusIndex > 0) focusIndex--;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            String pkg = packages.get(focusIndex);
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) startActivity(i);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
