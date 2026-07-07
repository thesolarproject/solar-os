package com.solar.launcher;

import android.view.inputmethod.EditorInfo;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SolarImeDismissTest {

    @Test
    public void rejectsSolarLauncherPackage() {
        EditorInfo info = new EditorInfo();
        info.packageName = "com.solar.launcher";
        info.inputType = EditorInfo.TYPE_CLASS_TEXT;
        assertFalse(SolarImeDismiss.shouldShowSystemImeTray(info));
    }

    @Test
    public void acceptsRockboxTextField() {
        EditorInfo info = new EditorInfo();
        info.packageName = "org.rockbox";
        info.inputType = EditorInfo.TYPE_CLASS_TEXT;
        assertTrue(SolarImeDismiss.shouldShowSystemImeTray(info));
    }

    @Test
    public void rejectsNonTextInput() {
        EditorInfo info = new EditorInfo();
        info.packageName = "com.android.settings";
        info.inputType = 0;
        assertFalse(SolarImeDismiss.shouldShowSystemImeTray(info));
    }
}
