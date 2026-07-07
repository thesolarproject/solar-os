package com.solar.launcher;

import android.view.WindowManager;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Global WM overlay must not steal focus from the foreground app. */
public class SolarOverlayFocusTest {

    @Test
    public void globalOverlayWindowFlags_includeNotFocusable() {
        int flags = SolarOverlayService.globalOverlayWindowFlags();
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    }
}
