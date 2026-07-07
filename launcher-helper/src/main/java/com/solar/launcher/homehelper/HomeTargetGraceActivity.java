package com.solar.launcher.homehelper;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.solar.home.policy.HomeTargetPolicy;

/**
 * 2026-07-06 — Brief grace UI while persist.solar.home.applying=1 settles PM preferred HOME.
 * Layman: shows "Updating home app…" so Home does not flash the wrong launcher.
 * Technical: polls applying prop up to 4s then routes like LauncherHomeActivity.
 */
public final class HomeTargetGraceActivity extends Activity {

    private static final long POLL_MS = 200L;
    private static final long MAX_WAIT_MS = 4000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long waitedMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView label = new TextView(this);
        label.setText(R.string.grace_waiting);
        setContentView(label);
        handler.post(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isHomeApplyInProgress() || waitedMs >= MAX_WAIT_MS) {
                LauncherHomeActivity.launchSavedTarget(HomeTargetGraceActivity.this);
                finish();
                return;
            }
            waitedMs += POLL_MS;
            handler.postDelayed(this, POLL_MS);
        }
    };

    private static boolean isHomeApplyInProgress() {
        return "1".equals(LauncherHomeActivity.readSystemProperty(
                HomeTargetPolicy.PROP_HOME_APPLYING, "0"));
    }
}
