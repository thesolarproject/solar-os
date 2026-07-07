package com.solar.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses LRC sidecar files — [mm:ss.xx] lyric lines for timed sync.
 * 2026-07-06 — supports [mm:ss] and [mm:ss.cc] tags.
 */
public final class LrcParser {
    private static final Pattern TAG =
            Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)");

    public static final class Line {
        public final long timestampMs;
        public final String text;

        public Line(long timestampMs, String text) {
            this.timestampMs = timestampMs;
            this.text = text != null ? text : "";
        }
    }

    private LrcParser() {}

    /** Parse raw LRC text into timestamped lines; unsynced body lines get timestamp -1. */
    public static List<Line> parse(String raw) {
        List<Line> out = new ArrayList<Line>();
        if (raw == null || raw.isEmpty()) return out;
        String[] rows = raw.split("\\r?\\n");
        for (String row : rows) {
            if (row == null) continue;
            String trimmed = row.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = TAG.matcher(trimmed);
            boolean matched = false;
            while (m.find()) {
                matched = true;
                long ms = parseTimestamp(m.group(1), m.group(2), m.group(3));
                String text = m.group(4) != null ? m.group(4).trim() : "";
                if (!text.isEmpty()) out.add(new Line(ms, text));
            }
            if (!matched && !trimmed.startsWith("[") && !trimmed.startsWith("ar:")
                    && !trimmed.startsWith("ti:") && !trimmed.startsWith("al:")) {
                out.add(new Line(-1L, trimmed));
            }
        }
        return out;
    }

    /** True when at least one line has a non-negative timestamp. */
    public static boolean hasSyncedTimestamps(List<Line> lines) {
        if (lines == null) return false;
        for (Line line : lines) {
            if (line.timestampMs >= 0) return true;
        }
        return false;
    }

    /** Index of active line for playback position, or -1 when before first tag. */
    public static int indexForPositionMs(List<Line> syncedLines, long positionMs) {
        if (syncedLines == null || syncedLines.isEmpty()) return -1;
        int active = -1;
        for (int i = 0; i < syncedLines.size(); i++) {
            Line line = syncedLines.get(i);
            if (line.timestampMs < 0) continue;
            if (line.timestampMs <= positionMs) active = i;
            else break;
        }
        return active;
    }

    private static long parseTimestamp(String minStr, String secStr, String fracStr) {
        int min = parseInt(minStr, 0);
        int sec = parseInt(secStr, 0);
        int fracMs = 0;
        if (fracStr != null && !fracStr.isEmpty()) {
            int frac = parseInt(fracStr, 0);
            if (fracStr.length() == 2) fracMs = frac * 10;
            else if (fracStr.length() == 3) fracMs = frac;
            else fracMs = frac;
        }
        return min * 60_000L + sec * 1000L + fracMs;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** Join unsynced lines for plain-text display. */
    public static String joinPlainText(List<Line> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            if (line.text.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.text);
        }
        return sb.toString();
    }
}
