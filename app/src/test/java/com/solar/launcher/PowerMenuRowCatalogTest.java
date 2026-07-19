package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Power menu IPC rows are restart + shutdown only in Solar-only builds. */
public class PowerMenuRowCatalogTest {

    @Test
    public void powerRowCountIsTwo() {
        assertEquals(2, PowerMenuRowCatalog.powerRowCountForTest());
    }

    @Test
    public void powerActionTokensAreRestartShutdownOnly() {
        assertArrayEquals(new String[] { "restart", "shutdown" },
                PowerMenuRowCatalog.powerActionTokensForTest());
    }

    @Test
    public void powerActionTokensWithUsbPrependEnable() {
        assertArrayEquals(new String[] { "enable_usb_storage", "restart", "shutdown" },
                PowerMenuRowCatalog.powerActionTokensWithUsbForTest());
    }

    @Test
    public void dispatchRejectsNullContextAndOutOfRangeIndex() {
        assertFalse(PowerMenuRowCatalog.dispatch(null, 0));
        assertFalse(PowerMenuRowCatalog.dispatch(null, -1));
        assertFalse(PowerMenuRowCatalog.dispatch(null, 2));
    }
}
