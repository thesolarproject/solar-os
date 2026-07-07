package com.solar.launcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.Arrays;

/**
 * Reads embedded lyrics from ID3v2 USLT frames (MP3) without extra libraries.
 * 2026-07-06 — scans ID3v2 tag headers only; FLAC LYRICS comment best-effort.
 */
public final class Id3UsltReader {
    private static final byte[] ID3 = {'I', 'D', '3'};

    private Id3UsltReader() {}

    /** Embedded unsynced lyrics, or null when absent. */
    public static String readFromAudioFile(File file) {
        if (file == null || !file.isFile()) return null;
        String lower = file.getName().toLowerCase(Locale.US);
        if (lower.endsWith(".mp3")) return readMp3Uslt(file);
        if (lower.endsWith(".flac")) return readFlacLyricsComment(file);
        return null;
    }

    private static String readMp3Uslt(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] header = new byte[10];
            if (in.read(header) != 10) return null;
            if (header[0] != ID3[0] || header[1] != ID3[1] || header[2] != ID3[2]) return null;
            int tagSize = synchsafeSize(header, 6);
            int version = header[3] & 0xff;
            byte[] tag = new byte[tagSize];
            int read = 0;
            while (read < tagSize) {
                int n = in.read(tag, read, tagSize - read);
                if (n <= 0) break;
                read += n;
            }
            return scanFramesForUslt(tag, version);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private static String scanFramesForUslt(byte[] tag, int versionMajor) {
        int pos = 0;
        int version = versionMajor;
        while (pos + 10 <= tag.length) {
            String id = new String(tag, pos, 4, Charset.forName("ISO-8859-1"));
            int size;
            int header = 10;
            if (version >= 4) {
                size = synchsafeSize(tag, pos + 4);
            } else {
                size = u32(tag, pos + 4);
            }
            if (size <= 0 || pos + header + size > tag.length) break;
            if ("USLT".equals(id)) {
                String text = decodeUslt(tag, pos + header, size);
                if (text != null && !text.trim().isEmpty()) return text.trim();
            }
            pos += header + size;
        }
        return null;
    }

    private static String decodeUslt(byte[] tag, int offset, int size) {
        if (size < 4) return null;
        int textStart = offset + 4;
        int textEnd = offset + size;
        for (int i = textStart; i < textEnd; i++) {
            if (tag[i] == 0) {
                textEnd = i;
                break;
            }
        }
        if (textStart >= textEnd) return null;
        try {
            return new String(tag, textStart, textEnd - textStart, Charset.forName("UTF-8"));
        } catch (Exception e) {
            return new String(tag, textStart, textEnd - textStart, Charset.forName("ISO-8859-1"));
        }
    }

    private static String readFlacLyricsComment(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] header = new byte[4];
            if (in.read(header) != 4) return null;
            if (header[0] != 'f' || header[1] != 'L' || header[2] != 'a' || header[3] != 'C') return null;
            while (true) {
                int b = in.read();
                if (b < 0) break;
                boolean last = (b & 0x80) != 0;
                int type = b & 0x7f;
                int len = readFlacVarInt(in);
                if (len < 0) break;
                if (type == 4) {
                    byte[] block = new byte[len];
                    if (readFully(in, block) != len) return null;
                    return scanVorbisComments(block, "LYRICS");
                }
                if (!skipFully(in, len)) return null;
                if (last) break;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
        return null;
    }

    private static String scanVorbisComments(byte[] block, String key) {
        if (block.length < 8) return null;
        int count = (int) u32le(block, 4);
        int pos = 8;
        for (int i = 0; i < count && pos + 4 <= block.length; i++) {
            int len = (int) u32le(block, pos);
            pos += 4;
            if (len < 0 || pos + len > block.length) break;
            String entry = new String(block, pos, len, Charset.forName("UTF-8"));
            pos += len;
            if (entry.toUpperCase(Locale.US).startsWith(key + "=")) {
                return entry.substring(key.length() + 1);
            }
        }
        return null;
    }

    private static int synchsafeSize(byte[] buf, int offset) {
        return ((buf[offset] & 0x7f) << 21) | ((buf[offset + 1] & 0x7f) << 14)
                | ((buf[offset + 2] & 0x7f) << 7) | (buf[offset + 3] & 0x7f);
    }

    private static int u32(byte[] buf, int offset) {
        return ((buf[offset] & 0xff) << 24) | ((buf[offset + 1] & 0xff) << 16)
                | ((buf[offset + 2] & 0xff) << 8) | (buf[offset + 3] & 0xff);
    }

    private static long u32le(byte[] buf, int offset) {
        return (buf[offset] & 0xffL) | ((buf[offset + 1] & 0xffL) << 8)
                | ((buf[offset + 2] & 0xffL) << 16) | ((buf[offset + 3] & 0xffL) << 24);
    }

    private static int readFlacVarInt(InputStream in) throws java.io.IOException {
        int value = 0;
        for (int i = 0; i < 8; i++) {
            int b = in.read();
            if (b < 0) return -1;
            value = (value << 7) | (b & 0x7f);
            if ((b & 0x80) == 0) return value;
        }
        return -1;
    }

    private static int readFully(InputStream in, byte[] buf) throws java.io.IOException {
        int read = 0;
        while (read < buf.length) {
            int n = in.read(buf, read, buf.length - read);
            if (n <= 0) break;
            read += n;
        }
        return read;
    }

    private static boolean skipFully(InputStream in, int len) throws java.io.IOException {
        byte[] skip = new byte[Math.min(len, 4096)];
        int remaining = len;
        while (remaining > 0) {
            int n = in.read(skip, 0, Math.min(skip.length, remaining));
            if (n <= 0) return false;
            remaining -= n;
        }
        return true;
    }
}
