package com.solar.launcher.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

/**
 * Push/pop on clipped list hosts (settings, library browse) — iPod submenu drill.
 * Outgoing content slides off before rebuild; incoming slides on in the same motion family.
 */
public final class ListDrillTransition {

    private static final Interpolator EASE = ScreenTransition.PUSH_EASE;
    private static volatile boolean animating;

    private ListDrillTransition() {}

    public static boolean isAnimating() {
        return animating || ScreenTransition.isAnimating() || LayoutMorphTransition.isAnimating();
    }

    /**
     * 2026-07-16 — Read menu_transitions from SOLAR_SETTINGS (same store as MainActivity).
     * Was: package _preferences (always defaulted on). Reversal: restore _preferences file.
     */
    public static boolean enabled(Context ctx) {
        if (ctx == null) return false;
        if (ScreenTransition.systemAnimationsDisabled(ctx)) return false;
        SharedPreferences prefs = ctx.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_MENU_TRANSITIONS, true);
    }

    public static final String PREF_MENU_TRANSITIONS = "menu_transitions";

    public static void push(final ViewGroup host, final Runnable build) {
        run(host, build, true);
    }

    public static void pop(final ViewGroup host, final Runnable build) {
        run(host, build, false);
    }

    private static void run(final ViewGroup host, final Runnable build, final boolean forward) {
        if (host == null || build == null) {
            if (build != null) build.run();
            return;
        }
        // 2026-07-18 — Status throbber for every settings/library drill (anim on or off).
        // Layman: spinner while the submenu list is built. Technical: REASON_TRANSITION.
        // Was: no UiBusy on list drill — only root ScreenTransitionCoordinator felt busy.
        // Reversal: drop begin/clear below.
        UiBusy.beginAutoEnd(UiBusy.REASON_TRANSITION, 6_000L);
        if (!enabled(host.getContext()) || animating) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("path", "instant");
                d.put("forward", forward);
                d.put("enabled", enabled(host.getContext()));
                d.put("animating", animating);
                com.solar.launcher.Debug0f5debLog.log(host.getContext(),
                        "ListDrillTransition.run", "instant drill clear-same-stack", "H1", d);
            } catch (Exception ignored) {}
            // #endregion
            try {
                build.run();
            } finally {
                UiBusy.clear(UiBusy.REASON_TRANSITION);
            }
            return;
        }
        final int w = host.getWidth() > 0 ? host.getWidth() : host.getResources()
                .getDimensionPixelSize(com.solar.launcher.R.dimen.y1_screen_width);
        final float outEnd = forward ? -w : w;
        final float inStart = forward ? w : -w;
        final int halfMs = ScreenTransition.PUSH_MS / 2;

        host.setVisibility(View.VISIBLE);
        host.setAlpha(1f);
        host.setTranslationX(0f);
        animating = true;
        ScreenTransition.enableHardwareLayer(host);

        // Phase 1 — slide outgoing list off before rebuild.
        host.animate().translationX(outEnd).alpha(0.55f)
                .setDuration(halfMs).setInterpolator(EASE)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        host.setTranslationX(inStart);
                        host.setAlpha(1f);
                        build.run();
                        host.postOnAnimation(new Runnable() {
                            @Override
                            public void run() {
                                // Phase 2 — slide incoming list on.
                                host.animate().translationX(0f).alpha(1f)
                                        .setDuration(ScreenTransition.PUSH_MS).setInterpolator(EASE)
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                animating = false;
                                                host.setTranslationX(0f);
                                                ScreenTransition.clearHardwareLayer(host);
                                                UiBusy.clear(UiBusy.REASON_TRANSITION);
                                            }
                                        }).start();
                            }
                        });
                    }
                }).start();
    }

    /** Reset drill host after hard escape to home — cancels in-flight slide. */
    public static void resetHost(ViewGroup host) {
        if (host == null) return;
        host.animate().cancel();
        host.setTranslationX(0f);
        host.setAlpha(1f);
        ScreenTransition.clearHardwareLayer(host);
        animating = false;
    }
}
