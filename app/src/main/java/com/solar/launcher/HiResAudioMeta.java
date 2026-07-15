package com.solar.launcher;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Locale;

/**
 * 2026-07-15 — Best-effort hi-res sample-rate / bit-depth for Now Playing.
 * Layman: show “96 kHz · 24-bit” when the file advertises it.
 * Reversal: callers ignore null — NP looks as before.
 */
public final class HiResAudioMeta {

    public static final class Info {
        public final int sampleRateHz;
        public final int bitDepth;

        public Info(int sampleRateHz, int bitDepth) {
            this.sampleRateHz = sampleRateHz;
            this.bitDepth = bitDepth;
        }

        public String formatLabel() {
            if (sampleRateHz <= 0) return null;
            String rate = sampleRateHz >= 1000
                    ? String.format(Locale.US, "%d kHz", sampleRateHz / 1000)
                    : sampleRateHz + " Hz";
            if (bitDepth > 0) return rate + " · " + bitDepth + "-bit";
            return rate;
        }
    }

    private HiResAudioMeta() {}

    public static Info read(File file) {
        if (file == null || !file.isFile()) return null;
        String n = file.getName().toLowerCase(Locale.US);
        if (n.endsWith(".flac")) return readFlac(file);
        return null;
    }

    /** FLAC STREAMINFO: bytes 18–21 sample rate (20 bits) + bit depth bits nearby. */
    private static Info readFlac(File file) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            byte[] hdr = new byte[42];
            if (raf.read(hdr) < 42) return null;
            if (hdr[0] != 'f' || hdr[1] != 'L' || hdr[2] != 'a' || hdr[3] != 'C') return null;
            // STREAMINFO block typically follows fLaC + header; sample rate at offset 18 in STREAMINFO payload.
            // Full header: 4 fLaC + 4 block header + STREAMINFO starts at 8.
            int sr = ((hdr[18] & 0xff) << 12) | ((hdr[19] & 0xff) << 4) | ((hdr[20] & 0xff) >> 4);
            int bps = ((hdr[20] & 0x0f) << 1) | ((hdr[21] & 0xff) >> 7);
            bps += 1;
            if (sr < 8000 || sr > 768000) return null;
            if (bps < 8 || bps > 32) bps = 0;
            return new Info(sr, bps);
        } catch (Exception e) {
            return null;
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
