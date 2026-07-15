package com.solar.launcher.xposed.bridge.extract;

import java.util.ArrayList;
import java.util.Map;

/**
 * 2026-07-06 — Pure rules for AMS crash/ANR dialog → Solar global overlay routing.
 * Layman: decides when to swap stock "has stopped" / "isn't responding" for the wheel menu.
 * Technical: no Xposed/binder — unit-testable policy shared by AppAnrHooks / AppErrorHooks.
 * Reversal: inline back into hooks; delete this class.
 */
public final class SystemErrorDialogRouting {

    /** KitKat {@code AppNotRespondingDialog} handler codes. */
    public static final int ANR_FORCE_CLOSE = 1;
    public static final int ANR_WAIT = 2;
    public static final int ANR_WAIT_AND_REPORT = 3;

    /** KitKat {@code AppErrorDialog} handler codes. */
    public static final int CRASH_FORCE_QUIT = 0;
    public static final int CRASH_FORCE_QUIT_AND_REPORT = 1;

    /** Wheel-friendly row order — Wait safest default at top. */
    private static final int[] ANR_WHEEL_ORDER =
            new int[] { ANR_WAIT, ANR_FORCE_CLOSE, ANR_WAIT_AND_REPORT };

    private SystemErrorDialogRouting() {}

    /**
     * Tier 1 gate — replace ANR when overlay host is installed (incl. system_server).
     * 2026-07-08 — Was: system/android ANR stayed on stock Holo. Now: companion overlay
     * replaces all ANRs; timed fail-open restores stock + wheel forwarder if paint misses.
     * Tier 3: {@code overlayAvailable=false} → stock Holo unchanged.
     */
    public static boolean shouldReplaceAnr(String processName, boolean overlayAvailable) {
        return overlayAvailable;
    }

    /**
     * Tier 1 gate — replace crash dialog when overlay host exists (incl. system crash).
     * Tier 3: fail-open to stock when Solar/companion missing or startService misses.
     */
    public static boolean shouldReplaceCrash(boolean overlayAvailable) {
        return overlayAvailable;
    }

    /**
     * 2026-07-08 — Informational: system ANR still benefits from fail-open wheel remap.
     * No longer a denylist for overlay replace — companion shell owns system ANRs too.
     */
    public static boolean isSystemAnrProcess(String processName) {
        return "system".equals(processName) || "android".equals(processName);
    }

    /** Solar main, :overlay, :hold — auto-WAIT before overlay paints. */
    public static boolean isSolarProcess(String processName) {
        return processName != null && processName.startsWith("com.solar.launcher");
    }

    /** Solar ANR: fire WAIT handler first so AMS clears harsh state before overlay rows. */
    public static boolean shouldAutoWaitBeforeSolarAnrOverlay(String processName) {
        return isSolarProcess(processName);
    }

    /** Wait → Close → Report — iPod-class safest-default ordering. */
    public static void orderAnrButtonsForWheel(Map<Integer, String> labelsByCode,
            ArrayList<String> labels, ArrayList<Integer> handlerCodes) {
        if (labelsByCode == null || labels == null || handlerCodes == null) return;
        for (int i = 0; i < ANR_WHEEL_ORDER.length; i++) {
            int code = ANR_WHEEL_ORDER[i];
            String label = labelsByCode.get(code);
            if (label != null && label.length() > 0) {
                labels.add(label);
                handlerCodes.add(code);
            }
        }
    }

    /** Close before Report — matches stock crash dialog emphasis. */
    public static void orderCrashButtonsForWheel(String closeLabel, String reportLabel,
            boolean includeReport, ArrayList<String> labels, ArrayList<Integer> handlerCodes) {
        if (labels == null || handlerCodes == null) return;
        if (closeLabel != null && closeLabel.length() > 0) {
            labels.add(closeLabel);
            handlerCodes.add(CRASH_FORCE_QUIT);
        }
        if (includeReport && reportLabel != null && reportLabel.length() > 0) {
            labels.add(reportLabel);
            handlerCodes.add(CRASH_FORCE_QUIT_AND_REPORT);
        }
    }

    /**
     * Map overlay row index to AMS handler code — index 1 = first action row (0 = detail header).
     * Cancel / invalid index uses first handler or {@code defaultCode}.
     */
    public static int handlerCodeForOverlaySelection(int[] handlerCodes, int overlayIndex,
            int defaultCode) {
        if (handlerCodes == null || handlerCodes.length == 0) return defaultCode;
        if (overlayIndex < 0) return defaultCode;
        int buttonIndex = overlayIndex - 1;
        if (buttonIndex < 0 || buttonIndex >= handlerCodes.length) return defaultCode;
        return handlerCodes[buttonIndex];
    }

    /** Default ANR rows when AlertController fields are empty — Wait then Close. */
    public static void defaultAnrButtons(ArrayList<String> labels, ArrayList<Integer> handlerCodes) {
        if (labels == null || handlerCodes == null) return;
        if (labels.isEmpty()) {
            labels.add("Wait");
            handlerCodes.add(ANR_WAIT);
            labels.add("Close");
            handlerCodes.add(ANR_FORCE_CLOSE);
        }
    }

    /** Default crash row when no Report receiver — Close only. */
    public static void defaultCrashButtons(ArrayList<String> labels, ArrayList<Integer> handlerCodes) {
        if (labels == null || handlerCodes == null) return;
        if (labels.isEmpty()) {
            labels.add("Close");
            handlerCodes.add(CRASH_FORCE_QUIT);
        }
    }
}
