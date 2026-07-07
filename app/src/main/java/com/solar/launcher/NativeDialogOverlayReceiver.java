package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Xposed AlertDialog hooks forward native dialogs here — scrollable detail + action rows.
 */
public final class NativeDialogOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_NATIVE_DIALOG.equals(intent.getAction())) return;
        String[] buttons = intent.getStringArrayExtra(OverlayTriggers.EXTRA_DIALOG_BUTTONS);
        if (buttons == null || buttons.length == 0) return;
        String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
        if (sessionId == null || sessionId.length() == 0) return;

        OverlayHandoffRestoreReceiver.notifyPause(context);
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
        svc.putExtra(OverlayTriggers.EXTRA_DIALOG_MESSAGE,
                intent.getStringExtra(OverlayTriggers.EXTRA_DIALOG_MESSAGE));
        svc.putExtra(OverlayTriggers.EXTRA_DIALOG_BUTTONS, buttons);
        svc.putExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID, sessionId);
        svc.putExtra(OverlayTriggers.EXTRA_MENU_TITLE,
                intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE));
        svc.putExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE,
                intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE));
        context.startService(svc);
    }
}
