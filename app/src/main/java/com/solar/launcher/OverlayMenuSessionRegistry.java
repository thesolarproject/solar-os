package com.solar.launcher;

import android.os.Bundle;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-07-07 — In-memory app-menu sessions for companion IPC (:overlay process).
 * Layman: remembers open context-menu rows so the helper APK can paint live data.
 * Technical: keyed by hook session UUID; cleared on dismiss or tier teardown.
 * Reversal: delete; companion uses static fallback rows only.
 */
public final class OverlayMenuSessionRegistry {

    private static final ConcurrentHashMap<String, Session> SESSIONS =
            new ConcurrentHashMap<String, Session>();

    static final class Session {
        final String title;
        final String[] labels;
        final boolean[] hasSubmenu;
        final String callerPackage;

        Session(String title, String[] labels, boolean[] hasSubmenu, String callerPackage) {
            this.title = title;
            this.labels = labels;
            this.hasSubmenu = hasSubmenu;
            this.callerPackage = callerPackage;
        }
    }

    private OverlayMenuSessionRegistry() {}

    public static void put(String sessionId, String title, String[] labels, boolean[] hasSubmenu) {
        put(sessionId, title, labels, hasSubmenu, null);
    }

    public static void put(String sessionId, String title, String[] labels, boolean[] hasSubmenu,
            String callerPackage) {
        if (sessionId == null || sessionId.length() == 0) return;
        SESSIONS.put(sessionId, new Session(title, labels, hasSubmenu, callerPackage));
    }

    /**
     * 2026-07-07 — Companion IPC row pick → APP_MENU_RESULT broadcast to hooked app.
     * Layman: wheel selection in helper overlay reaches the third-party app menu.
     * 2026-07-08 — Keep session when has_submenu[index] or solar_home_* (drill / Home refresh).
     * Was: always remove. Now: submenu/Home stay until DISMISS or non-keep pick.
     * Reversal: restore unconditional remove — one-shot menus only.
     */
    public static boolean dispatchAction(android.content.Context context, String sessionId,
            int actionIndex) {
        if (context == null || sessionId == null) return false;
        Session s = SESSIONS.get(sessionId);
        if (s == null) return false;
        android.content.Intent result = new android.content.Intent(
                OverlayTriggers.ACTION_APP_MENU_RESULT);
        result.putExtra(OverlayTriggers.EXTRA_MENU_SESSION_ID, sessionId);
        result.putExtra(OverlayTriggers.EXTRA_SELECTED_INDEX, actionIndex);
        if (s.callerPackage != null && s.callerPackage.length() > 0) {
            result.setPackage(s.callerPackage);
        }
        context.sendBroadcast(result);
        boolean opensSub = s.hasSubmenu != null && actionIndex >= 0
                && actionIndex < s.hasSubmenu.length && s.hasSubmenu[actionIndex];
        boolean solarHome = sessionId.startsWith("solar_home_");
        if (!opensSub && !solarHome) {
            SESSIONS.remove(sessionId);
        }
        return true;
    }

    public static void remove(String sessionId) {
        if (sessionId != null) SESSIONS.remove(sessionId);
    }

    public static Bundle buildSnapshot(String sessionId) {
        Session s = sessionId != null ? SESSIONS.get(sessionId) : null;
        if (s == null || s.labels == null) return null;
        Bundle b = new Bundle();
        b.putString(OverlayMenuSnapshotBuilder.KEY_KIND, "app_menu");
        b.putString(OverlayMenuSnapshotBuilder.KEY_SESSION_ID, sessionId);
        if (s.title != null) b.putString(OverlayMenuSnapshotBuilder.KEY_TITLE, s.title);
        b.putStringArray(OverlayMenuSnapshotBuilder.KEY_LABELS, s.labels);
        if (s.hasSubmenu != null) {
            b.putBooleanArray("has_submenu", s.hasSubmenu);
        }
        return b;
    }
}
