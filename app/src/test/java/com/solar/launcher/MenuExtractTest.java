package com.solar.launcher;

import com.solar.launcher.xposed.bridge.extract.MenuExtract;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for MenuBuilder row extraction used by AppMenuHooks. */
public class MenuExtractTest {

    @Test
    public void extractsTitlesAndSubmenuFlags() {
        final Object copy = new Object();
        final Object paste = new Object();
        MenuExtract.Snapshot snap = MenuExtract.fromMenu(new MenuExtract.MenuItemReader() {
            @Override
            public List<?> visibleItems() {
                return Arrays.asList(copy, paste);
            }

            @Override
            public String title(Object item) {
                if (item == copy) return "Copy";
                if (item == paste) return "Paste";
                return "";
            }

            @Override
            public boolean hasSubmenu(Object item) {
                return item == paste;
            }
        });
        assertEquals(2, snap.size());
        assertEquals("Copy", snap.rows[0].title);
        assertFalse(snap.rows[0].hasSubmenu);
        assertEquals("Paste", snap.rows[1].title);
        assertTrue(snap.rows[1].hasSubmenu);
    }

    @Test
    public void emptyWhenNoVisibleItems() {
        MenuExtract.Snapshot snap = MenuExtract.fromMenu(new MenuExtract.MenuItemReader() {
            @Override
            public List<?> visibleItems() {
                return Collections.emptyList();
            }

            @Override
            public String title(Object item) {
                return "";
            }

            @Override
            public boolean hasSubmenu(Object item) {
                return false;
            }
        });
        assertEquals(0, snap.size());
    }
}
