package com.solar.launcher.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 2026-07-06 — Context-menu modal present/dismiss for WM overlay hosts.
 * Layman: panel pop-in/out without dimming the whole screen behind it.
 * Technical: shared by Solar :overlay and companion GlobalContextOverlayService.
 * Reversal: delete module; inline back into app ScreenTransition only.
 */
public final class OverlayModalTransition {

    public static final int MODAL_MS = 200;
    private static final float MODAL_PANEL_START_SCALE = 0.94f;
    private static final String PREF_MENU_TRANSITIONS = "menu_transitions";

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);
    private static volatile boolean modalAnimating;

    private OverlayModalTransition() {}

    /**
     * 2026-07-16 — SOLAR_SETTINGS matches MainActivity menu_transitions.
     * Reversal: packageName + "_preferences".
     */
    public static boolean enabled(Context ctx) {
        if (ctx == null) return false;
        if (systemAnimationsDisabled(ctx)) return false;
        SharedPreferences prefs = ctx.getSharedPreferences("SOLAR_SETTINGS", Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_MENU_TRANSITIONS, true);
    }

    public static boolean isAnimating() {
        return modalAnimating;
    }

    public static boolean systemAnimationsDisabled(Context ctx) {
        if (ctx == null) return false;
        try {
            return Settings.Global.getFloat(ctx.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void prepareModalPresent(View scrim, View panel) {
        if (scrim != null) scrim.setAlpha(0f);
        prepareModalPresentPanelOnly(panel);
    }

    public static void prepareModalPresentPanelOnly(View panel) {
        if (panel != null) {
            panel.setPivotX(panel.getWidth() > 0 ? panel.getWidth() * 0.5f : 0f);
            panel.setPivotY(panel.getHeight() > 0 ? panel.getHeight() * 0.5f : 0f);
            panel.setScaleX(MODAL_PANEL_START_SCALE);
            panel.setScaleY(MODAL_PANEL_START_SCALE);
            panel.setAlpha(0f);
        }
    }

    public static void animatePresent(View scrim, View panel, Runnable onComplete) {
        if (scrim == null && panel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        modalAnimating = true;
        enableHardwareLayer(scrim);
        enableHardwareLayer(panel);
        final Runnable start = new Runnable() {
            @Override
            public void run() {
                if (panel != null) {
                    panel.setPivotX(panel.getWidth() * 0.5f);
                    panel.setPivotY(panel.getHeight() * 0.5f);
                }
                final Runnable done = new Runnable() {
                    @Override
                    public void run() {
                        modalAnimating = false;
                        if (panel != null) {
                            panel.setScaleX(1f);
                            panel.setScaleY(1f);
                            panel.setAlpha(1f);
                            clearHardwareLayer(panel);
                        }
                        if (scrim != null) {
                            scrim.setAlpha(1f);
                            clearHardwareLayer(scrim);
                        }
                        if (onComplete != null) onComplete.run();
                    }
                };
                if (panel != null) {
                    panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(MODAL_MS).setInterpolator(EASE)
                            .setListener(endListener(done)).start();
                } else {
                    done.run();
                    return;
                }
                if (scrim != null) {
                    scrim.animate().alpha(1f).setDuration(MODAL_MS).setInterpolator(EASE).start();
                }
            }
        };
        View anchor = panel != null ? panel : scrim;
        anchor.postOnAnimation(start);
    }

    public static void animatePresentPanelOnly(final View panel, final Runnable onComplete) {
        if (panel == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        modalAnimating = true;
        enableHardwareLayer(panel);
        panel.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                panel.setPivotX(panel.getWidth() * 0.5f);
                panel.setPivotY(panel.getHeight() * 0.5f);
                final Runnable done = new Runnable() {
                    @Override
                    public void run() {
                        modalAnimating = false;
                        panel.setScaleX(1f);
                        panel.setScaleY(1f);
                        panel.setAlpha(1f);
                        clearHardwareLayer(panel);
                        if (onComplete != null) onComplete.run();
                    }
                };
                panel.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(MODAL_MS).setInterpolator(EASE)
                        .setListener(endListener(done)).start();
            }
        });
    }

    public static void animateDismiss(View scrim, View panel, Runnable onComplete) {
        if (scrim == null && panel == null) {
            modalAnimating = false;
            if (onComplete != null) onComplete.run();
            return;
        }
        modalAnimating = true;
        enableHardwareLayer(scrim);
        enableHardwareLayer(panel);
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                modalAnimating = false;
                clearHardwareLayer(scrim);
                clearHardwareLayer(panel);
                if (onComplete != null) onComplete.run();
            }
        };
        boolean panelStarted = false;
        if (panel != null) {
            panelStarted = true;
            panel.setPivotX(panel.getWidth() * 0.5f);
            panel.setPivotY(panel.getHeight() * 0.5f);
            panel.animate().scaleX(MODAL_PANEL_START_SCALE).scaleY(MODAL_PANEL_START_SCALE).alpha(0f)
                    .setDuration(MODAL_MS).setInterpolator(EASE)
                    .setListener(endListener(done)).start();
        }
        if (scrim != null) {
            scrim.animate().alpha(0f).setDuration(MODAL_MS).setInterpolator(EASE).start();
        }
        if (!panelStarted) {
            done.run();
        }
    }

    public static void animateDismissPanelOnly(final View panel, final Runnable onComplete) {
        if (panel == null) {
            modalAnimating = false;
            if (onComplete != null) onComplete.run();
            return;
        }
        modalAnimating = true;
        enableHardwareLayer(panel);
        panel.setPivotX(panel.getWidth() * 0.5f);
        panel.setPivotY(panel.getHeight() * 0.5f);
        final Runnable done = new Runnable() {
            @Override
            public void run() {
                modalAnimating = false;
                clearHardwareLayer(panel);
                if (onComplete != null) onComplete.run();
            }
        };
        panel.animate().scaleX(MODAL_PANEL_START_SCALE).scaleY(MODAL_PANEL_START_SCALE).alpha(0f)
                .setDuration(MODAL_MS).setInterpolator(EASE)
                .setListener(endListener(done)).start();
    }

    private static void enableHardwareLayer(View v) {
        if (v != null) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private static void clearHardwareLayer(View v) {
        if (v != null) v.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    private static Animator.AnimatorListener endListener(final Runnable onComplete) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) onComplete.run();
            }
        };
    }
}
