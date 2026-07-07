package com.solar.launcher;

import org.junit.Test;

import java.util.List;

public class LrcParserTest {

    @Test
    public void parsesTimestampedLines() {
        List<LrcParser.Line> lines = LrcParser.parse("[00:12.50]Hello world\n[01:00]Second line");
        if (lines.size() != 2) throw new AssertionError("expected 2 lines");
        if (lines.get(0).timestampMs != 12500L) throw new AssertionError("bad ts0");
        if (!"Hello world".equals(lines.get(0).text)) throw new AssertionError("bad text0");
        if (lines.get(1).timestampMs != 60000L) throw new AssertionError("bad ts1");
    }

    @Test
    public void indexForPositionFindsActiveLine() {
        List<LrcParser.Line> lines = LrcParser.parse("[00:10.00]A\n[00:20.00]B\n[00:30.00]C");
        int idx = LrcParser.indexForPositionMs(lines, 15000L);
        if (idx != 0) throw new AssertionError("expected line 0 at 15s");
        idx = LrcParser.indexForPositionMs(lines, 20000L);
        if (idx != 1) throw new AssertionError("expected line 1 at 20s");
    }

    @Test
    public void hasSyncedTimestamps() {
        List<LrcParser.Line> synced = LrcParser.parse("[00:01]Hi");
        if (!LrcParser.hasSyncedTimestamps(synced)) throw new AssertionError("should be synced");
        List<LrcParser.Line> plain = LrcParser.parse("Just text");
        if (LrcParser.hasSyncedTimestamps(plain)) throw new AssertionError("should not be synced");
    }
}
