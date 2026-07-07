package com.solar.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 2026-07-05 — Tier-3 IME fallback: clipboard paste when InputConnection tier-1 misses.
 * Arms only when SolarImeRouteArbiter route=a11y; never runs in parallel with active IME tray.
 * When changing: 99SolarInit.sh must auto-enable this service on ROM bake; APK cannot skip ROM-first ship.
 * Reversal: disable a11y service; typing falls back to stock IME only.
 */
public class SolarImeAccessibilityService extends AccessibilityService {

    private static volatile SolarImeAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (!SolarImeRouteArbiter.isRouteAccessibility()) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED) return;
        // Do not warm-start IME from a11y — onStartInput handles real field focus.
    }

    @Override
    public void onInterrupt() {}

    /** Tier 3 commit — clipboard + paste on focused editable node. */
    public static boolean commitViaAccessibility(Context ctx, String text) {
        SolarImeAccessibilityService svc = instance;
        if (svc == null) return false;
        return svc.pasteIntoFocusedEditable(ctx, text);
    }

    private boolean pasteIntoFocusedEditable(Context ctx, String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focused = findFocusedEditable(root);
        if (focused == null) {
            root.recycle();
            return false;
        }
        boolean ok = false;
        try {
            if (text != null && text.length() > 0) {
                ClipboardManager clip = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clip != null) {
                    clip.setPrimaryClip(ClipData.newPlainText("solar_ime", text));
                }
                ok = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            } else {
                ok = focused.performAction(AccessibilityNodeInfo.ACTION_CUT)
                        || focused.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION);
            }
        } catch (Exception ignored) {}
        focused.recycle();
        root.recycle();
        return ok;
    }

    private static AccessibilityNodeInfo findFocusedEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isFocused() && node.isEditable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo hit = findFocusedEditable(child);
            child.recycle();
            if (hit != null) return hit;
        }
        return null;
    }
}
