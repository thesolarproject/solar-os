package com.solar.launcher;

import android.graphics.Bitmap;

import com.solar.launcher.theme.ThemeSkinBridge;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Theme skin sidecar JSON + wallpaper scaling for Xposed ActivitySkin. */
public class ThemeSkinBridgeTest {

    @Test
    public void readEnabledFromJson() {
        assertTrue(ThemeSkinBridge.readEnabled("{\"enabled\":true}"));
        assertFalse(ThemeSkinBridge.readEnabled("{\"enabled\":false}"));
    }

    @Test
    public void readBackgroundColorFromJson() {
        assertEquals(0xFF112233, ThemeSkinBridge.readBackgroundColor("{\"backgroundColor\":-15654349}"));
    }

    @Test
    public void buildSkinJsonHasVersionAndFlags() throws Exception {
        JSONObject json = ThemeSkinBridge.buildSkinJson();
        assertEquals(ThemeSkinBridge.SIDECAR_VERSION, json.getInt("version"));
        assertTrue(json.getBoolean("enabled"));
        assertTrue(json.has("backgroundColor"));
        assertTrue(json.has("statusBarColor"));
        assertTrue(json.has("hasWallpaper"));
    }

    @Test
    public void wallpaperScaleGeometryCenterCrops800x600() {
        int[] geo = ThemeSkinBridge.wallpaperScaleGeometry(800, 600);
        assertEquals(480, geo[0]);
        assertEquals(360, geo[1]);
        assertEquals(0, geo[2]);
        assertEquals(0, geo[3]);
        assertEquals(480, geo[4]);
        assertEquals(360, geo[5]);
    }
}
