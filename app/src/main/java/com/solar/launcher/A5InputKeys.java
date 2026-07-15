package com.solar.launcher;

import android.content.Context;
import android.view.KeyEvent;

/**
 * 2026-07-14 — Timmkoo A5 KeyCodeDisp + stock mtk-kpd (p2_ata) → Solar nav keys.
 * Layman: middle confirms; power goes back; menus use face L/R to scroll; on Now Playing
 * face L/R skip or hold-scrub; side volume changes loudness (not skip).
 * Tech: BACK+scan158→CENTER, MEDIA_STOP/scan116→BACK; DPAD 19/20 wheel / MEDIA_PREV|NEXT on NP;
 * VOLUME_* via Solar HUD after A5-mtk.kl.
 * Landscape: UP↔DOWN (and MEDIA_PLAY↔PAUSE wheel twins) so scroll matches finger after rotate.
 * Stock scancodes (mtk-kpd): face 103/108 DPAD, 158 BACK; side 114/115 VOLUME; power 116→MEDIA_STOP.
 * Was: any KEYCODE_BACK→CENTER, so power→Back and edge-synth Back re-entered as OK.
 * Reversal: map code 4→CENTER again without scancode gate; drop landscape invert.
 */
public final class A5InputKeys {

    /**
     * Front middle — KeyCodeDisp KEYCODE_BACK (4) / scancode 158.
     * Remaps to DPAD_CENTER (OK); hold uses center-hold policy (context / queue move).
     */
    public static final int FACE_MIDDLE = KeyEvent.KEYCODE_BACK; // 4

    /** KeyCodeDisp / mtk-kpd scancode for front middle (must stay distinct from power 116). */
    public static final int FACE_MIDDLE_SCAN = 158;

    /**
     * Side power — A5-mtk.kl maps scancode 116 → MEDIA_STOP (86); app remaps to BACK.
     * Hold uses Back-hold context / rescue ladders.
     */
    public static final int SIDE_POWER = KeyEvent.KEYCODE_MEDIA_STOP; // 86

    /** mtk-kpd / A5-mtk.kl scancode for side power. */
    public static final int SIDE_POWER_SCAN = 116;

    /** Face left — scancode 103 → DPAD_UP (19); menus = wheel; NP → prev / hold rewind. */
    public static final int NAV_UP = KeyEvent.KEYCODE_DPAD_UP; // 19

    /** Face right — scancode 108 → DPAD_DOWN (20); menus = wheel; NP → next / hold FF. */
    public static final int NAV_DOWN = KeyEvent.KEYCODE_DPAD_DOWN; // 20

    /** Side Vol Down — scancode 114 → VOLUME_DOWN after A5-mtk.kl / Generic restore. */
    public static final int SIDE_VOL_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN; // 25

    /** Side Vol Up — scancode 115 → VOLUME_UP after A5-mtk.kl / Generic restore. */
    public static final int SIDE_VOL_UP = KeyEvent.KEYCODE_VOLUME_UP; // 24

    /** 2026-07-14 — Unit-test override for landscape invert; null = use real orientation. */
    private static Boolean invertVerticalForTest;

    /**
     * 2026-07-14 — After remapEvent + re-dispatch, skip a second remap pass.
     * Layman: flip up/down once when rotated; do not flip it back on the same press.
     * Tech: ThreadLocal blocks needsRemap so landscape UP↔DOWN cannot ping-pong until depth abort.
     * Was: recurse dispatchKeyEvent remapped DOWN while invert still on → UP→DOWN→UP→swallow.
     * Reversal: remove ThreadLocal; accept landscape invert only once outside dispatch.
     */
    private static final ThreadLocal<Boolean> REMAP_PASSTHROUGH = new ThreadLocal<Boolean>();

    private A5InputKeys() {}

    /**
     * 2026-07-14 — Call around re-dispatch of a remapped KeyEvent.
     * Layman: this press was already rewritten — do not rewrite again.
     */
    public static void beginRemapPassthrough() {
        REMAP_PASSTHROUGH.set(Boolean.TRUE);
    }

    /** 2026-07-14 — Clear one-shot remap gate after nested dispatch returns. */
    public static void endRemapPassthrough() {
        REMAP_PASSTHROUGH.remove();
    }

    /** 2026-07-14 — True while nested dispatch runs after remapEvent. */
    public static boolean isRemapPassthrough() {
        return Boolean.TRUE.equals(REMAP_PASSTHROUGH.get());
    }

    /**
     * Keys that must be rewritten before stock handlers (Recents / play-pause).
     * Face DPAD and VOLUME stay out — menus/wheel and Solar volume HUD own them unless NP face remap
     * or landscape vertical invert.
     */
    public static boolean isA5HardwareKey(int keyCode) {
        // FACE_MIDDLE (4) / SIDE_POWER (86) / raw POWER (26) when kl not yet A5-mtk.
        return keyCode == FACE_MIDDLE || keyCode == SIDE_POWER
                || keyCode == 4 || keyCode == 86
                || keyCode == KeyEvent.KEYCODE_POWER || keyCode == 26;
    }

    /**
     * 2026-07-14 — Face left/right after kl (DPAD_UP/DOWN).
     * Layman: the two side-by-side front buttons next to the middle OK.
     * Tech: scancodes 103/108 → 19/20; NP remaps to MEDIA_PREVIOUS/NEXT.
     */
    public static boolean isFaceNavKey(int keyCode) {
        return keyCode == NAV_UP || keyCode == NAV_DOWN
                || keyCode == 19 || keyCode == 20;
    }

    /**
     * 2026-07-14 — Y1-style wheel vertical twins (MEDIA_PLAY/PAUSE) for landscape invert.
     * Layman: same “scroll up/down” idea as face buttons when a wheel key arrives.
     * Tech: 126/127 — rare on stock A5; included so one invert covers wheel + DPAD.
     */
    public static boolean isWheelVerticalKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127;
    }

    /**
     * 2026-07-14 — Side power hardware (scan 116 / MEDIA_STOP / raw POWER).
     * Layman: the side sleeper button on A5 — always means “go back”, never OK.
     * Tech: A5-mtk.kl → MEDIA_STOP; Y1-Rockbox.kl still POWER; both share scancode 116.
     * Reversal: drop helper; rely on needsRemap only.
     */
    public static boolean isSidePower(int keyCode, int scan) {
        if (!DeviceFeatures.isA5()) return false;
        if (scan == SIDE_POWER_SCAN) return true;
        if (keyCode == SIDE_POWER || keyCode == 86) return true;
        // Raw POWER when A5 keymap not pushed yet (Y1-Rockbox.kl still maps 116→POWER).
        return keyCode == KeyEvent.KEYCODE_POWER || keyCode == 26;
    }

    /** 2026-07-14 — Event wrapper for {@link #isSidePower(int, int)}. */
    public static boolean isSidePowerEvent(KeyEvent event) {
        return event != null && isSidePower(event.getKeyCode(), event.getScanCode());
    }

    /**
     * 2026-07-14 — Face middle OK pad (scan 158), not edge Back or power.
     * Layman: the front center button that confirms a letter on the keyboard.
     * Tech: KEYCODE_BACK + FACE_MIDDLE_SCAN only.
     */
    public static boolean isFaceMiddle(int keyCode, int scan) {
        if (!DeviceFeatures.isA5()) return false;
        return (keyCode == FACE_MIDDLE || keyCode == 4) && scan == FACE_MIDDLE_SCAN;
    }

    /** 2026-07-14 — Event wrapper for {@link #isFaceMiddle(int, int)}. */
    public static boolean isFaceMiddleEvent(KeyEvent event) {
        return event != null && isFaceMiddle(event.getKeyCode(), event.getScanCode());
    }

    /**
     * 2026-07-14 — Build Back from side power with scan 0 so it never looks like face middle (158).
     * Layman: pretend the user pressed a normal Back, not the front middle OK pad.
     * Tech: KeyEvent BACK + scanCode 0; needsRemap skips face-mid remap. Consume UP/DOWN same way.
     * Reversal: remap MEDIA_STOP→BACK preserving scan 116 again.
     */
    public static KeyEvent sidePowerAsBackEvent(KeyEvent src) {
        if (src == null) return null;
        return new KeyEvent(src.getDownTime(), src.getEventTime(), src.getAction(),
                KeyEvent.KEYCODE_BACK, src.getRepeatCount(), src.getMetaState(),
                src.getDeviceId(),
                0 /* scan: never FACE_MIDDLE_SCAN */,
                src.getFlags());
    }

    /**
     * 2026-07-14 — True when dispatch must run remapEvent (mid, NP face, or landscape vertical).
     * Layman: only rewrite keys we own; power is handled separately via {@link #isSidePowerEvent}.
     * Tech: power no longer goes through rematch BACK→CENTER; face mid still scan 158→CENTER.
     * Reversal: restore mid/power in one needsRemap gate.
     */
    public static boolean needsRemap(Context ctx, KeyEvent event, boolean nowPlaying) {
        if (!DeviceFeatures.isA5() || event == null) return false;
        // 2026-07-14 — Nested dispatch after remapEvent: already flipped / mid→OK once.
        if (isRemapPassthrough()) return false;
        // Power owned by isSidePowerEvent → sidePowerAsBackEvent (never remap to OK here).
        if (isSidePowerEvent(event)) return false;
        int keyCode = event.getKeyCode();
        int scan = event.getScanCode();
        // Face middle only when KeyCodeDisp mid scancode — never edge-synth Back (scan 0).
        if ((keyCode == FACE_MIDDLE || keyCode == 4) && scan == FACE_MIDDLE_SCAN) {
            return true;
        }
        if (isFaceNavKey(keyCode) && (nowPlaying || shouldInvertVerticalNav(ctx))) return true;
        return shouldInvertVerticalNav(ctx) && isWheelVerticalKey(keyCode);
    }

    /** @deprecated use {@link #needsRemap(Context, KeyEvent, boolean)} — scan-aware. */
    public static boolean needsRemap(Context ctx, int keyCode, boolean nowPlaying) {
        // Fail-open: mid/power keycodes without scancode (tests / odd callers).
        if (!DeviceFeatures.isA5()) return false;
        if (isRemapPassthrough()) return false;
        if (keyCode == SIDE_POWER || keyCode == 86) return true;
        if (keyCode == FACE_MIDDLE || keyCode == 4) return true;
        if (isFaceNavKey(keyCode) && (nowPlaying || shouldInvertVerticalNav(ctx))) return true;
        return shouldInvertVerticalNav(ctx) && isWheelVerticalKey(keyCode);
    }

    /**
     * 2026-07-14 — A5 landscape: invert vertical scroll sense.
     * Layman: after turning the player sideways, “up” button should move the list down.
     * Tech: isA5 + A5NavigationMode.isLandscape; null ctx / portrait → false.
     * Reversal: always return false.
     */
    public static boolean shouldInvertVerticalNav(Context ctx) {
        // Nested re-dispatch after UP→DOWN: do not invert again on the same press.
        if (isRemapPassthrough()) return false;
        if (invertVerticalForTest != null) {
            return DeviceFeatures.isA5() && invertVerticalForTest.booleanValue();
        }
        return DeviceFeatures.isA5() && A5NavigationMode.isLandscape(ctx);
    }

    /** 2026-07-14 — Test hook: force landscape invert on/off; null clears. */
    static void setInvertVerticalForTest(Boolean invert) {
        invertVerticalForTest = invert;
    }

    /** 2026-07-14 — Clear landscape invert test override. */
    static void resetInvertVerticalForTest() {
        invertVerticalForTest = null;
    }

    /**
     * 2026-07-14 — Pure UP↔DOWN / PLAY↔PAUSE swap (no device gate).
     * Layman: flip which vertical key code we pretend arrived.
     * Tech: DPAD 19↔20 and MEDIA_PLAY 126↔MEDIA_PAUSE 127; others unchanged.
     * Reversal: return keyCode unchanged.
     */
    public static int swapVerticalNavKey(int keyCode) {
        if (keyCode == NAV_UP || keyCode == 19) return NAV_DOWN;
        if (keyCode == NAV_DOWN || keyCode == 20) return NAV_UP;
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == 126) {
            return KeyEvent.KEYCODE_MEDIA_PAUSE;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == 127) {
            return KeyEvent.KEYCODE_MEDIA_PLAY;
        }
        return keyCode;
    }

    /**
     * Remap A5 hardware key to a Solar-handled keycode, or -1 if not A5 / not ours.
     * @param nowPlaying when true, face L/R become track prev/next (hold scrub via media skip path).
     */
    public static int remapToSolarKeyCode(Context ctx, int keyCode, boolean nowPlaying) {
        // Tests without scancode: treat bare FACE_MIDDLE / SIDE_POWER by keycode.
        int scan = (keyCode == FACE_MIDDLE || keyCode == 4) ? FACE_MIDDLE_SCAN
                : ((keyCode == SIDE_POWER || keyCode == 86) ? SIDE_POWER_SCAN : 0);
        return remapToSolarKeyCode(ctx, keyCode, scan, nowPlaying);
    }

    /**
     * 2026-07-14 — Scancode-aware remap (power 116 / mid 158).
     * Was: any BACK→CENTER, so power→Back and edge Back became OK.
     * Reversal: ignore scanCode; map code 4→CENTER always.
     */
    public static int remapToSolarKeyCode(Context ctx, int keyCode, int scanCode,
            boolean nowPlaying) {
        if (!DeviceFeatures.isA5()) return -1;
        int raw = keyCode;
        // 2026-07-14 — Landscape first: flip vertical before mid/power/NP map.
        if (shouldInvertVerticalNav(ctx)) {
            keyCode = swapVerticalNavKey(keyCode);
        }
        // Power: MEDIA_STOP or power scancode → BACK (idempotent if already BACK+116).
        if (keyCode == SIDE_POWER || keyCode == 86 || scanCode == SIDE_POWER_SCAN) {
            return KeyEvent.KEYCODE_BACK;
        }
        // Face middle only — scancode 158. Synthetic Back (scan 0) and power-as-Back (116) stay Back.
        if ((keyCode == FACE_MIDDLE || keyCode == 4) && scanCode == FACE_MIDDLE_SCAN) {
            return KeyEvent.KEYCODE_DPAD_CENTER;
        }
        // 2026-07-14 — NP only: face left/right → skip after landscape invert.
        if (nowPlaying && isFaceNavKey(keyCode)) {
            if (keyCode == NAV_UP || keyCode == 19) {
                return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
            }
            if (keyCode == NAV_DOWN || keyCode == 20) {
                return KeyEvent.KEYCODE_MEDIA_NEXT;
            }
        }
        // Menus landscape: return flipped vertical; portrait menus stay -1 (passthrough).
        if (keyCode != raw && (isFaceNavKey(keyCode) || isWheelVerticalKey(keyCode))) {
            return keyCode;
        }
        return -1;
    }

    /**
     * Build a KeyEvent with remapped keycode, same action/time/repeat as source.
     * Returns null when no remap applies.
     */
    public static KeyEvent remapEvent(Context ctx, KeyEvent src, boolean nowPlaying) {
        if (src == null) return null;
        int mapped = remapToSolarKeyCode(ctx, src.getKeyCode(), src.getScanCode(), nowPlaying);
        if (mapped < 0 || mapped == src.getKeyCode()) return null;
        return new KeyEvent(src.getDownTime(), src.getEventTime(), src.getAction(),
                mapped, src.getRepeatCount(), src.getMetaState(), src.getDeviceId(),
                src.getScanCode(), src.getFlags());
    }

    /** Self-check KeyCodeDisp matrix — fails fast in unit tests. */
    static void selfCheckRemapMatrix() {
        if (remapMenusForCheck(FACE_MIDDLE) != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("face mid → center");
        }
        if (remapMenusForCheck(SIDE_POWER) != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("power MEDIA_STOP → back");
        }
        if (remapMenusForCheck(4) != KeyEvent.KEYCODE_DPAD_CENTER) {
            throw new AssertionError("keycode 4+scan158 → center");
        }
        if (remapMenusForCheck(86) != KeyEvent.KEYCODE_BACK) {
            throw new AssertionError("keycode 86 → back");
        }
        // Synthetic / edge Back (scan 0) must NOT become OK.
        DeviceFeatures.setCachedFamilyForTest("a5");
        try {
            if (remapToSolarKeyCode(null, 4, 0, false) >= 0) {
                throw new AssertionError("synthetic BACK scan0 must passthrough");
            }
            if (remapToSolarKeyCode(null, 4, SIDE_POWER_SCAN, false) != KeyEvent.KEYCODE_BACK) {
                throw new AssertionError("BACK+powerScan must stay/become Back");
            }
            // 2026-07-14 — Side power ints (no KeyEvent: Robolectric-free JVM unit tests).
            if (!isSidePower(SIDE_POWER, SIDE_POWER_SCAN) || !isSidePower(86, 116)) {
                throw new AssertionError("MEDIA_STOP+116 must be side power");
            }
            if (!isSidePower(KeyEvent.KEYCODE_POWER, SIDE_POWER_SCAN)
                    && !isSidePower(26, SIDE_POWER_SCAN)) {
                throw new AssertionError("raw POWER+116 must be side power");
            }
            if (isSidePower(FACE_MIDDLE, FACE_MIDDLE_SCAN) || isSidePower(4, 158)) {
                throw new AssertionError("face mid must not be side power");
            }
            // Power-as-Back uses scan 0 → not face mid remap.
            if (remapToSolarKeyCode(null, KeyEvent.KEYCODE_BACK, 0, false) >= 0) {
                throw new AssertionError("BACK scan0 after power bypass must passthrough");
            }
        } finally {
            DeviceFeatures.resetCacheForTest();
        }
        // 2026-07-14 — Portrait/null ctx: face DPAD must not rewrite (wheel scroll).
        if (isA5HardwareKey(NAV_UP) || isA5HardwareKey(NAV_DOWN)) {
            throw new AssertionError("DPAD must not be A5 mid/power remap keys");
        }
        if (remapMenusForCheck(NAV_UP) >= 0 || remapMenusForCheck(NAV_DOWN) >= 0) {
            throw new AssertionError("menus face DPAD must passthrough");
        }
        // 2026-07-14 — NP: face left → prev, face right → next (hold scrub via skip handlers).
        if (remapNpForCheck(NAV_UP) != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("NP face left → prev");
        }
        if (remapNpForCheck(NAV_DOWN) != KeyEvent.KEYCODE_MEDIA_NEXT) {
            throw new AssertionError("NP face right → next");
        }
        if (remapNpForCheck(19) != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("NP keycode 19 → prev");
        }
        if (remapNpForCheck(20) != KeyEvent.KEYCODE_MEDIA_NEXT) {
            throw new AssertionError("NP keycode 20 → next");
        }
        // Side volume never remapped here — Solar volume HUD owns VOLUME_* after kl.
        if (remapMenusForCheck(SIDE_VOL_UP) >= 0 || remapNpForCheck(SIDE_VOL_DOWN) >= 0) {
            throw new AssertionError("VOLUME must not be A5 remap");
        }
        // 2026-07-14 — Landscape invert table (pure swap; orientation gated at call site).
        if (swapVerticalNavKey(NAV_UP) != NAV_DOWN || swapVerticalNavKey(NAV_DOWN) != NAV_UP) {
            throw new AssertionError("landscape must flip DPAD UP↔DOWN");
        }
        if (swapVerticalNavKey(19) != 20 || swapVerticalNavKey(20) != 19) {
            throw new AssertionError("landscape must flip keycodes 19↔20");
        }
        if (swapVerticalNavKey(126) != KeyEvent.KEYCODE_MEDIA_PAUSE
                || swapVerticalNavKey(127) != KeyEvent.KEYCODE_MEDIA_PLAY) {
            throw new AssertionError("landscape must flip MEDIA_PLAY↔PAUSE");
        }
        if (swapVerticalNavKey(FACE_MIDDLE) != FACE_MIDDLE
                || swapVerticalNavKey(SIDE_POWER) != SIDE_POWER) {
            throw new AssertionError("mid/power must not flip");
        }
        // Landscape menus: UP→DOWN via real remap + test invert override.
        if (remapMenusLandscapeForCheck(NAV_UP) != NAV_DOWN
                || remapMenusLandscapeForCheck(NAV_DOWN) != NAV_UP) {
            throw new AssertionError("landscape menus must remap UP↔DOWN");
        }
        // Landscape NP: physical UP → invert DOWN → MEDIA_NEXT.
        if (remapNpLandscapeForCheck(NAV_UP) != KeyEvent.KEYCODE_MEDIA_NEXT
                || remapNpLandscapeForCheck(NAV_DOWN) != KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            throw new AssertionError("landscape NP must flip face→track after invert");
        }
    }

    /** Test helper — forces A5 family for pure remap table checks (menus). */
    private static int remapMenusForCheck(int keyCode) {
        DeviceFeatures.setCachedFamilyForTest("a5");
        try {
            return remapToSolarKeyCode(null, keyCode, false);
        } finally {
            DeviceFeatures.resetCacheForTest();
        }
    }

    /** Test helper — forces A5 family for Now Playing face→track remap checks. */
    private static int remapNpForCheck(int keyCode) {
        DeviceFeatures.setCachedFamilyForTest("a5");
        try {
            return remapToSolarKeyCode(null, keyCode, true);
        } finally {
            DeviceFeatures.resetCacheForTest();
        }
    }

    /** 2026-07-14 — Menus remap with forced landscape invert override. */
    private static int remapMenusLandscapeForCheck(int keyCode) {
        DeviceFeatures.setCachedFamilyForTest("a5");
        setInvertVerticalForTest(Boolean.TRUE);
        try {
            return remapToSolarKeyCode(null, keyCode, false);
        } finally {
            resetInvertVerticalForTest();
            DeviceFeatures.resetCacheForTest();
        }
    }

    /** 2026-07-14 — NP remap with forced landscape invert override. */
    private static int remapNpLandscapeForCheck(int keyCode) {
        DeviceFeatures.setCachedFamilyForTest("a5");
        setInvertVerticalForTest(Boolean.TRUE);
        try {
            return remapToSolarKeyCode(null, keyCode, true);
        } finally {
            resetInvertVerticalForTest();
            DeviceFeatures.resetCacheForTest();
        }
    }
}
