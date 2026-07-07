package com.solar.launcher.xposed.bridge;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.EditText;

/**
 * 2026-07-05 — Detect Rockbox KbdInput AlertDialog for DialogHooks denylist.
 * Layman: tells the bridge not to replace Rockbox's typing dialog with Solar overlay.
 * Technical: org.rockbox context + KbdInput view id — logic stays in bridge when IME hooks split out.
 * Reversal: inline check back into DialogHooks if probe removed.
 */
final class RockboxKeyboardDialogProbe {

    private static final String ROCKBOX_PKG = "org.rockbox";
    private static final String KBD_INPUT_ID = "KbdInput";

    private RockboxKeyboardDialogProbe() {}

    /** DialogHooks denylist — never replace Rockbox KbdInput AlertDialog with Solar overlay. */
    static boolean isRockboxKeyboardDialog(Object dialog) {
        if (!(dialog instanceof AlertDialog)) return false;
        try {
            Context ctx = ((AlertDialog) dialog).getContext();
            if (ctx == null || !ROCKBOX_PKG.equals(ctx.getPackageName())) return false;
            return findKbdInput((AlertDialog) dialog) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static EditText findKbdInput(AlertDialog dialog) {
        if (dialog == null) return null;
        try {
            Context ctx = dialog.getContext();
            if (ctx == null) return null;
            int id = ctx.getResources().getIdentifier(KBD_INPUT_ID, "id", ROCKBOX_PKG);
            if (id == 0) return null;
            View v = dialog.findViewById(id);
            if (v instanceof EditText) return (EditText) v;
        } catch (Throwable ignored) {}
        return null;
    }
}
