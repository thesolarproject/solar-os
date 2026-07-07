package com.solar.launcher;

import com.solar.launcher.theme.ThemeColorBridge;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Theme color sidecar JSON parsing for Xposed Holo fail-open skin. */
public class ThemeColorBridgeTest {

    @Test
    public void readPanelColorFromJson() {
        assertEquals(0xFF112233, ThemeColorBridge.readPanelColor("{\"panelColor\":-15654349}"));
    }
}
