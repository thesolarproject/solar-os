package com.solar.launcher.xposed.bridge;

import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — When stock Holo ANR is showing, remap wheel/side/center keys to DPAD taps
 * so scrollwheel hardware can focus Wait / Close / Report without editing keylayouts.
 * Reversal: remove install() from SystemServerHooks — stock ANR stays touch-only again.
 */
final class AnrDialogKeyForwarder {

    /** PWM interceptKeyBeforeDispatching: return >= 0 means key consumed. */
    private static final long KEY_CONSUMED_DISPATCH = -1L;
    /** PWM interceptKeyBeforeQueueing: return 0 drops the key before dispatch. */
    private static final int KEY_CONSUMED_QUEUE = 0;

    private static volatile boolean stockAnrActive;

    private AnrDialogKeyForwarder() {}

    /** Called from AppAnrHooks when stock Holo ANR is visible. */
    static void setStockAnrActive(boolean active) {
        stockAnrActive = active;
        if (active) {
            SolarContextBridge.log("AnrKeyForwarder armed");
        }
    }

    /** Hook PWM dispatch + queue — MTK Y1/Y2 often queue wheel keys before dispatch. */
    static void install(Class<?> pwm) {
        XC_MethodHook dispatchHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleStockAnrKey(param, false);
            }
        };
        XC_MethodHook queueHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleStockAnrKey(param, true);
            }
        };
        try {
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", dispatchHook);
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", queueHook);
            SolarContextBridge.log("hooked stock ANR key forward on PhoneWindowManager");
        } catch (Throwable t) {
            SolarContextBridge.log("AnrKeyForwarder hook failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * On ACTION_UP, inject DPAD focus/activate keys into the stock ANR dialog.
     * Original media keys are swallowed so apps do not receive wheel events.
     */
    private static void handleStockAnrKey(XC_MethodHook.MethodHookParam param, boolean queueing) {
        if (!stockAnrActive || OverlayKeyForwarder.isOverlayActive()) return;
        KeyEvent event = findKeyEvent(param.args);
        if (event == null) return;
        int action = event.getAction();
        if (action != KeyEvent.ACTION_UP) {
            if (action == KeyEvent.ACTION_DOWN && isAnrNavigationKey(event.getKeyCode())) {
                param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            }
            return;
        }
        if (event.getRepeatCount() > 0) {
            param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            return;
        }
        int dpad = mediaToDpad(event.getKeyCode());
        if (dpad <= 0) return;
        SystemServerHooks.injectDpadTap(dpad);
        SolarContextBridge.log("AnrKeyForward media=" + event.getKeyCode() + " dpad=" + dpad);
        param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
    }

    /** Same remap table as ExternalInputHandoff.mediaToDpad — wheel/side/center → DPAD. */
    private static int mediaToDpad(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == 87) {
            return KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == 88) {
            return KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126) {
            return KeyEvent.KEYCODE_DPAD_UP;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127) {
            return KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == 85 || keyCode == 79) {
            return KeyEvent.KEYCODE_DPAD_CENTER;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == 19) {
            return KeyEvent.KEYCODE_DPAD_UP;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == 20) {
            return KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == 21) {
            return KeyEvent.KEYCODE_DPAD_LEFT;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == 22) {
            return KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == 66 || keyCode == 23) {
            return KeyEvent.KEYCODE_DPAD_CENTER;
        }
        return 0;
    }

    private static boolean isAnrNavigationKey(int keyCode) {
        return mediaToDpad(keyCode) > 0;
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof KeyEvent) return (KeyEvent) args[i];
        }
        return null;
    }
}
