package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * One MediaPlayer per Stem pad — multi-Melody collapse for Y1. 2026-07-19
 */
public class LalalCollapsePadsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Four zone-3 files collapse to a single Melody pad. 2026-07-19 */
    @Test
    public void collapsesManyMelodyToOne() throws Exception {
        File dir = tmp.newFolder("stems");
        List<LalalClient.StemFile> raw = new ArrayList<LalalClient.StemFile>();
        raw.add(stem(dir, "vocals", 0));
        raw.add(stem(dir, "drum", 1));
        raw.add(stem(dir, "bass", 2));
        raw.add(stem(dir, "piano", 3));
        raw.add(stem(dir, "electric_guitar", 3));
        raw.add(stem(dir, "acoustic_guitar", 3));
        raw.add(stem(dir, "no_multistem", 3));
        List<LalalClient.StemFile> out = LalalClient.collapseToOnePadPerZone(raw);
        assertEquals(4, out.size());
        int z3 = 0;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).zone == 3) z3++;
        }
        assertEquals(1, z3);
        // Prefer residual when no melody alias. 2026-07-19
        assertEquals("no_multistem", out.get(3).id);
    }

    /** Explicit melody.mp3 wins over piano/guitars. 2026-07-19 */
    @Test
    public void prefersMelodyAlias() throws Exception {
        File dir = tmp.newFolder("stems2");
        List<LalalClient.StemFile> raw = new ArrayList<LalalClient.StemFile>();
        raw.add(stem(dir, "vocals", 0));
        raw.add(stem(dir, "drum", 1));
        raw.add(stem(dir, "bass", 2));
        raw.add(stem(dir, "piano", 3));
        raw.add(stem(dir, "melody", 3));
        List<LalalClient.StemFile> out = LalalClient.collapseToOnePadPerZone(raw);
        assertEquals(4, out.size());
        assertEquals("melody", out.get(3).id);
        assertTrue(out.get(3).file.getName().startsWith("melody"));
    }

    private static LalalClient.StemFile stem(File dir, String id, int zone) throws Exception {
        File f = new File(dir, id + ".mp3");
        assertTrue(f.createNewFile());
        return new LalalClient.StemFile(id, id, f, zone);
    }
}
