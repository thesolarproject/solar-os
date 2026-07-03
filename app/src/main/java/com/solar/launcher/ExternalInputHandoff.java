package com.solar.launcher;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.lang.reflect.Method;

/**
 * Rockbox-Y1 style handoff for Android screens outside Solar.
 * Hardware mappings stay untouched; media-style Y1 button events are re-emitted as DPAD only
 * while Solar has explicitly handed focus to another foreground activity.
 */
public final class ExternalInputHandoff {
    private static final String TAG = "ExternalInputHandoff";
    private static volatile boolean loggedPrivilegedInjectFailure;
    private static volatile boolean loggedSuInjectFailure;

    public static final int MODE_OFF = 0;
    public static final int MODE_FM = 1;
    public static final int MODE_ANDROID = 2;

    private ExternalInputHandoff() {}

    public static boolean isMediaNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_HEADSETHOOK;
    }

    public static int mediaToDpad(int keyCode, int repeatCount, int mode) {
        if (mode == MODE_FM) return mediaToFmDpad(keyCode, repeatCount);
        if (mode != MODE_ANDROID) return 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return KeyEvent.KEYCODE_DPAD_UP;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return KeyEvent.KEYCODE_DPAD_DOWN;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return KeyEvent.KEYCODE_DPAD_CENTER;
            default:
                return 0;
        }
    }

    private static int mediaToFmDpad(int keyCode, int repeatCount) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                return KeyEvent.KEYCODE_DPAD_RIGHT;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                return KeyEvent.KEYCODE_DPAD_LEFT;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                return KeyEvent.KEYCODE_VOLUME_DOWN;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                return KeyEvent.KEYCODE_VOLUME_UP;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                return repeatCount > 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN;
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return KeyEvent.KEYCODE_DPAD_CENTER;
            default:
                return 0;
        }
    }

    public static boolean injectClick(Context context, int keyCode) {
        if (context == null || keyCode <= 0) return false;
        long now = SystemClock.uptimeMillis();
        boolean injected = injectKeyEvent(context, new KeyEvent(now, now,
                KeyEvent.ACTION_DOWN, keyCode, 0))
                & injectKeyEvent(context, new KeyEvent(now + 20, now + 20,
                KeyEvent.ACTION_UP, keyCode, 0));
        if (injected) return true;
        return DeviceFeatures.hasRootAccess() && injectClickViaSu(keyCode);
    }

    private static boolean injectKeyEvent(Context context, KeyEvent event) {
        try {
            InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
            Method injectInputEvent = InputManager.class.getMethod(
                    "injectInputEvent", InputEvent.class, int.class);
            final int injectInputEventModeAsync = 0;
            Object result = injectInputEvent.invoke(im, event, injectInputEventModeAsync);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (Exception e) {
            if (!loggedPrivilegedInjectFailure) {
                loggedPrivilegedInjectFailure = true;
                Log.w(TAG, "failed to inject key event " + event.getKeyCode(), e);
            }
            return false;
        }
    }

    private static boolean injectClickViaSu(int keyCode) {
        try {
            Process proc = Runtime.getRuntime().exec(new String[] {
                    "su", "-c", "input keyevent " + keyCode
            });
            return proc.waitFor() == 0;
        } catch (Exception e) {
            if (!loggedSuInjectFailure) {
                loggedSuInjectFailure = true;
                Log.w(TAG, "failed to inject key event via su " + keyCode, e);
            }
            return false;
        }
    }
}
