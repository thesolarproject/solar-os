package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class FlowLibraryRowsTest {

    @Test
    public void cacheReusesRowsUntilGenChanges() {
        FlowLibraryRows cache = new FlowLibraryRows();
        List<MainActivity.SongItem> lib = new ArrayList<MainActivity.SongItem>();
        lib.add(new MainActivity.SongItem(new File("/a.mp3"), "A", "B", "C", "Rock", "", 1, 1999));
        List<FlowCatalog.SongRow> first = cache.rows(lib, 1);
        List<FlowCatalog.SongRow> second = cache.rows(lib, 1);
        assertSame(first, second);
        cache.invalidate();
        List<FlowCatalog.SongRow> third = cache.rows(lib, 2);
        assertEquals(1, third.size());
        assertEquals(1999, third.get(0).year);
    }
}
