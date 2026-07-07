package com.solar.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * App-process Xposed hooks forward context menus here — no signature permission required.
 * Extras must stay primitive (String/String[]) so Solar never unmarshals foreign parcelables.
 */
public final class AppMenuOverlayReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!OverlayTriggers.ACTION_SHOW_OVERLAY_APP_MENU.equals(intent.getAction())) return;
        String[] titles = intent.getStringArrayExtra(OverlayTriggers.EXTRA_MENU_TITLES);
        if (titles == null || titles.length == 0) return;
        String sessionId = intent.getStringExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID);
        if (sessionId == null || sessionId.length() == 0) return;

        OverlayHandoffRestoreReceiver.notifyPause(context);
        Intent svc = new Intent(context, SolarOverlayService.class);
        svc.setAction(OverlayTriggers.ACTION_SHOW_OVERLAY_APP_MENU);
        svc.putExtra(OverlayTriggers.EXTRA_MENU_TITLES, titles);
        svc.putExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID, sessionId);
        svc.putExtra(OverlayTriggers.EXTRA_MENU_TITLE,
                intent.getStringExtra(OverlayTriggers.EXTRA_MENU_TITLE));
        svc.putExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE,
                intent.getStringExtra(OverlayTriggers.EXTRA_MENU_CALLER_PACKAGE));
        boolean[] hasSubmenu = intent.getBooleanArrayExtra(OverlayTriggers.EXTRA_MENU_HAS_SUBMENU);
        if (hasSubmenu != null) {
            svc.putExtra(OverlayTriggers.EXTRA_MENU_HAS_SUBMENU, hasSubmenu);
        }
        context.startService(svc);
    }
}
