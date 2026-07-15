package com.solar.launcher.audiobook;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 2026-07-15 — Parse QuickTime/MP4 chapter markers from .m4b/.m4a (API 17 safe, no MMR).
 * Layman: read chapter titles and start times baked into audiobook files.
 * Reversal: return empty list → multi-file folder chapters remain.
 */
public final class Mp4ChapterParser {

    public static final class Chapter {
        public final String title;
        public final long startMs;

        public Chapter(String title, long startMs) {
            this.title = title != null ? title : "";
            this.startMs = Math.max(0L, startMs);
        }
    }

    private Mp4ChapterParser() {}

    /** Best-effort chapter list; empty if none or parse fails. */
    public static List<Chapter> parse(File file) {
        List<Chapter> out = new ArrayList<Chapter>();
        if (file == null || !file.isFile()) return out;
        String n = file.getName().toLowerCase(Locale.US);
        if (!(n.endsWith(".m4b") || n.endsWith(".m4a") || n.endsWith(".mp4"))) return out;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            long len = raf.length();
            parseAtoms(raf, 0L, len, out, 1.0);
        } catch (Exception ignored) {
            out.clear();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
        return out;
    }

    /**
     * Walk top-level and nested atoms looking for chap / tref+text / chpl (Nero).
     * Timescale often 1000 or from mdhd — we treat raw chapter times as milliseconds when
     * values look like ms, else seconds×1000 (heuristic for common Nero chpl).
     */
    private static void parseAtoms(RandomAccessFile raf, long start, long end,
            List<Chapter> out, double timescale) throws IOException {
        long pos = start;
        while (pos + 8 <= end && out.size() < 512) {
            raf.seek(pos);
            long size = readUInt32(raf);
            int type = raf.readInt();
            long header = 8;
            if (size == 1) {
                size = raf.readLong();
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end + 8) break;
            long dataStart = pos + header;
            long dataEnd = pos + size;
            String fourcc = fourcc(type);
            if ("moov".equals(fourcc) || "trak".equals(fourcc) || "mdia".equals(fourcc)
                    || "minf".equals(fourcc) || "stbl".equals(fourcc) || "udta".equals(fourcc)) {
                parseAtoms(raf, dataStart, dataEnd, out, timescale);
            } else if ("mdhd".equals(fourcc) && size >= header + 20) {
                timescale = readMdhdTimescale(raf, dataStart);
            } else if ("chpl".equals(fourcc)) {
                parseNeroChpl(raf, dataStart, dataEnd, out);
            } else if ("chap".equals(fourcc)) {
                // QuickTime chapter track reference — titles live in a linked text track;
                // without full demux we cannot resolve; leave Nero/other parsers.
            }
            pos += size;
        }
    }

    private static double readMdhdTimescale(RandomAccessFile raf, long dataStart) throws IOException {
        raf.seek(dataStart);
        int version = raf.read();
        raf.skipBytes(3);
        if (version == 1) {
            raf.skipBytes(16);
        } else {
            raf.skipBytes(8);
        }
        long ts = readUInt32(raf);
        return ts > 0 ? ts : 1000.0;
    }

    /** Nero chapter list atom (common in .m4b). */
    private static void parseNeroChpl(RandomAccessFile raf, long start, long end,
            List<Chapter> out) throws IOException {
        raf.seek(start);
        if (end - start < 9) return;
        raf.readInt(); // version/flags
        int count = raf.read() & 0xff;
        for (int i = 0; i < count && raf.getFilePointer() + 9 <= end; i++) {
            long timestamp = raf.readLong(); // 100 ns units in Nero
            long startMs = timestamp / 10000L;
            int nameLen = raf.read() & 0xff;
            if (raf.getFilePointer() + nameLen > end) break;
            byte[] nameBytes = new byte[nameLen];
            raf.readFully(nameBytes);
            String title = new String(nameBytes, "UTF-8").trim();
            if (title.isEmpty()) title = "Chapter " + (i + 1);
            out.add(new Chapter(title, startMs));
        }
    }

    private static long readUInt32(RandomAccessFile raf) throws IOException {
        return raf.readInt() & 0xffffffffL;
    }

    private static String fourcc(int type) {
        char[] c = new char[4];
        c[0] = (char) ((type >> 24) & 0xff);
        c[1] = (char) ((type >> 16) & 0xff);
        c[2] = (char) ((type >> 8) & 0xff);
        c[3] = (char) (type & 0xff);
        return new String(c);
    }

    /** JVM self-check with synthetic minimal chpl-less empty. */
    public static void selfCheck() {
        List<Chapter> empty = parse(null);
        if (empty == null || !empty.isEmpty()) throw new AssertionError("null file");
    }
}
