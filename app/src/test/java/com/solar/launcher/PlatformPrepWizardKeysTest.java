package com.solar.launcher;

import com.solar.launcher.platform.PlatformPrepWizardKeys;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Y1/Y2 key routing for platform prep wizard — wheel center/back, not stock focus. */
public class PlatformPrepWizardKeysTest {

    @Test
    public void centerKeyUpActivates() {
        assertTrue(PlatformPrepWizardKeys.shouldActivateAction(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP));
        assertTrue(PlatformPrepWizardKeys.shouldActivateAction(66, KeyEvent.ACTION_UP));
        assertTrue(PlatformPrepWizardKeys.shouldActivateAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.ACTION_UP));
    }

    @Test
    public void centerKeyDownDoesNotActivate() {
        assertFalse(PlatformPrepWizardKeys.shouldActivateAction(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN));
    }

    @Test
    public void backKeyUpDismisses() {
        assertTrue(PlatformPrepWizardKeys.shouldDismissWizard(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP));
    }

    @Test
    public void wheelKeysDoNotDismiss() {
        assertFalse(PlatformPrepWizardKeys.shouldDismissWizard(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.ACTION_UP));
        assertFalse(PlatformPrepWizardKeys.shouldDismissWizard(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.ACTION_UP));
    }
}
