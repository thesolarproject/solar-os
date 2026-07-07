package com.solar.launcher;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Session-scoped root key injector, run out-of-process via {@code app_process} as uid 0.
 *
 * <p>Because it lives in a root VM every permission (including INJECT_EVENTS) is granted — the same
 * reason {@code su input keyevent} works. Unlike {@code /system/bin/input} we boot the Dalvik VM
 * ONCE and then stream keycodes over stdin, so each key costs a binder call (~ms) instead of a
 * fresh VM fork (~0.5-1s on MT6572/82). RootKeyInjector on the app side keeps the pipe open.
 *
 * <p>Protocol: one decimal Android keycode per stdin line → synthetic DOWN+UP injected. EOF (pipe
 * closed by {@link RootKeyInjector#stop()}), a {@code quit} line, or any unparseable line exits.
 */
public class KeyInjectorMain {
    // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH (2) — exactly what /system/bin/input uses. ASYNC (0)
    // was verified NOT delivered by the KitKat InputDispatcher on the Y2 (screen never moved), so we
    // block until the event is handled. On an already-booted VM this is still a fast binder call.
    private static final int INJECT_MODE_WAIT_FOR_FINISH = 2;

    public static void main(String[] args) {
        try {
            // Resolve the hidden InputManager singleton + injectInputEvent(InputEvent,int) via
            // reflection (both @hide on API 17) — cached once for the whole session.
            Class<?> imClass = Class.forName("android.hardware.input.InputManager");
            Object inputManager = imClass.getMethod("getInstance").invoke(null);
            Method inject = imClass.getMethod("injectInputEvent", InputEvent.class, int.class);
            // Tell the client the VM is up so it stops falling back to the slow one-shot su path.
            System.out.println("READY");
            System.out.flush();
            // Stream keycodes from stdin (fed straight from the su shell pipe) until EOF/quit.
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if ("quit".equals(line.trim())) break; // explicit shutdown request
                int code = parseKeyCode(line);
                if (code <= 0) break; // blank/unparseable line → clean exit
                injectKey(inputManager, inject, code);
            }
        } catch (Throwable t) {
            // Any boot failure prints to stderr (surfaced in the client's drain loop) and exits non-zero.
            t.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /** Parse one stdin line into a keycode; returns -1 for blank/non-numeric (caller treats as quit). */
    static int parseKeyCode(String line) {
        if (line == null) return -1;
        String trimmed = line.trim();
        if (trimmed.length() == 0) return -1;
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Inject a DOWN then UP for one keycode — mirrors /system/bin/input's KeyEvent construction. */
    private static void injectKey(Object inputManager, Method inject, int code) throws Exception {
        long now = SystemClock.uptimeMillis();
        inject.invoke(inputManager, keyEvent(now, KeyEvent.ACTION_DOWN, code), INJECT_MODE_WAIT_FOR_FINISH);
        inject.invoke(inputManager, keyEvent(now, KeyEvent.ACTION_UP, code), INJECT_MODE_WAIT_FOR_FINISH);
    }

    /** Virtual-keyboard KeyEvent identical to what {@code input keyevent} sends to InputDispatcher. */
    private static KeyEvent keyEvent(long now, int action, int code) {
        return new KeyEvent(now, now, action, code, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD);
    }
}
