package com.solar.launcher;

import com.solar.launcher.xposed.bridge.extract.SystemErrorDialogRouting;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JVM tests for AMS crash/ANR → overlay routing policy (no Xposed on device). 2026-07-08 */
public class SystemErrorDialogRoutingTest {

    @Test
    public void systemAnrUsesOverlayWhenAvailable() {
        // 2026-07-08 — All ANRs including system_server replace when overlay host present.
        assertTrue(SystemErrorDialogRouting.shouldReplaceAnr("system", true));
        assertTrue(SystemErrorDialogRouting.shouldReplaceAnr("android", true));
        assertTrue(SystemErrorDialogRouting.isSystemAnrProcess("system"));
        assertFalse(SystemErrorDialogRouting.shouldReplaceAnr("system", false));
    }

    @Test
    public void thirdPartyAnrUsesOverlayWhenAvailable() {
        assertTrue(SystemErrorDialogRouting.shouldReplaceAnr("com.example.app", true));
        assertFalse(SystemErrorDialogRouting.shouldReplaceAnr("com.example.app", false));
    }

    @Test
    public void solarAnrAutoWaitsBeforeOverlay() {
        assertTrue(SystemErrorDialogRouting.shouldAutoWaitBeforeSolarAnrOverlay("com.solar.launcher"));
        assertTrue(SystemErrorDialogRouting.shouldAutoWaitBeforeSolarAnrOverlay(
                "com.solar.launcher:overlay"));
        assertFalse(SystemErrorDialogRouting.shouldAutoWaitBeforeSolarAnrOverlay("org.rockbox"));
    }

    @Test
    public void anrButtonsWaitFirstForWheel() {
        Map<Integer, String> byCode = new LinkedHashMap<Integer, String>();
        byCode.put(SystemErrorDialogRouting.ANR_FORCE_CLOSE, "Close app");
        byCode.put(SystemErrorDialogRouting.ANR_WAIT, "Wait");
        byCode.put(SystemErrorDialogRouting.ANR_WAIT_AND_REPORT, "Report");
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> codes = new ArrayList<Integer>();
        SystemErrorDialogRouting.orderAnrButtonsForWheel(byCode, labels, codes);
        assertArrayEquals(new String[] {"Wait", "Close app", "Report"}, labels.toArray());
        assertArrayEquals(
                new int[] {
                        SystemErrorDialogRouting.ANR_WAIT,
                        SystemErrorDialogRouting.ANR_FORCE_CLOSE,
                        SystemErrorDialogRouting.ANR_WAIT_AND_REPORT
                },
                toIntArray(codes));
    }

    @Test
    public void crashButtonsCloseBeforeReport() {
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<Integer> codes = new ArrayList<Integer>();
        SystemErrorDialogRouting.orderCrashButtonsForWheel(
                "Close app", "Report", true, labels, codes);
        assertArrayEquals(new String[] {"Close app", "Report"}, labels.toArray());
        assertEquals(SystemErrorDialogRouting.CRASH_FORCE_QUIT, (int) codes.get(0));
        assertEquals(SystemErrorDialogRouting.CRASH_FORCE_QUIT_AND_REPORT, (int) codes.get(1));
    }

    @Test
    public void overlaySelectionMapsToHandlerCode() {
        int[] codes = new int[] {
                SystemErrorDialogRouting.ANR_WAIT,
                SystemErrorDialogRouting.ANR_FORCE_CLOSE
        };
        assertEquals(SystemErrorDialogRouting.ANR_WAIT,
                SystemErrorDialogRouting.handlerCodeForOverlaySelection(
                        codes, 1, SystemErrorDialogRouting.ANR_FORCE_CLOSE));
        assertEquals(SystemErrorDialogRouting.ANR_FORCE_CLOSE,
                SystemErrorDialogRouting.handlerCodeForOverlaySelection(
                        codes, 2, SystemErrorDialogRouting.ANR_WAIT));
        assertEquals(SystemErrorDialogRouting.ANR_FORCE_CLOSE,
                SystemErrorDialogRouting.handlerCodeForOverlaySelection(
                        codes, -1, SystemErrorDialogRouting.ANR_FORCE_CLOSE));
    }

    private static int[] toIntArray(ArrayList<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
