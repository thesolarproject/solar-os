package com.solar.launcher;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * Root probe from com.solar.launcher process (not instrumentation UID).
 */
@RunWith(AndroidJUnit4.class)
public class RootShellTest {

    @Rule
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class, true, false);

    @Test
    public void canRunSuFromSolarProcess() throws Exception {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.setClassName("com.solar.launcher", "com.solar.launcher.MainActivity");
        final MainActivity activity = activityRule.launchActivity(launch);
        final AtomicBoolean ok = new AtomicBoolean(false);
        final Object lock = new Object();
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ok.set(RootShell.canRun());
                synchronized (lock) {
                    lock.notifyAll();
                }
            }
        });
        synchronized (lock) {
            lock.wait(20000L);
        }
        JSONObject d = new JSONObject();
        d.put("canRun", ok.get());
        d.put("xbinSu", new File("/system/xbin/su").exists());
        d.put("binSu", new File("/system/bin/su").exists());
        DebugSessionLog.logAlways("RootShellTest.canRunSuFromSolarProcess",
                ok.get() ? "ok" : "fail", "H-A", d);
        assertTrue("su must work from com.solar.launcher on Y1/Y2 ROM", ok.get());
    }
}
