package com.solar.launcher;

import java.io.File;
import java.io.IOException;

/**
 * 2026-07-15 — Resolve Wi‑Fi Transfer browse/download/upload paths under Music or Audiobooks.
 * Layman: only let the PC reach files inside the Music/Audiobooks folders you chose.
 * Reversal: callers that used {@code new File(root, path)} with no checks — restore at own risk.
 */
public final class SolarWebPaths {

    private SolarWebPaths() {}

    /**
     * Resolve {@code rel} under {@code root}. Rejects absolute paths, {@code ..}, and escapes.
     * @return file inside root, or null if unsafe/missing containment
     */
    public static File resolveUnder(File root, String rel) {
        if (root == null || rel == null || rel.isEmpty()) return null;
        if (rel.charAt(0) == '/' || rel.indexOf(':') >= 0) return null;
        if (rel.contains("..")) return null;
        File candidate = new File(root, rel);
        // Absolute/relative child that ignored parent (new File(root, "/abs") case already rejected).
        return contained(root, candidate);
    }

    /** Same rules for Audiobooks root; {@code AUDIOBOOKS} or {@code AUDIOBOOKS/rel}. */
    public static File resolveAudiobooks(File audiobooksRoot, String tokenOrRel) {
        if (audiobooksRoot == null || tokenOrRel == null || tokenOrRel.isEmpty()) return null;
        if ("AUDIOBOOKS".equals(tokenOrRel)) return contained(audiobooksRoot, audiobooksRoot);
        if (!tokenOrRel.startsWith("AUDIOBOOKS/")) return null;
        String rest = tokenOrRel.substring("AUDIOBOOKS/".length());
        return resolveUnder(audiobooksRoot, rest);
    }

    /**
     * Basename-only upload name — rejects path separators and {@code ..}.
     * 2026-07-15 — Do not strip dirs (that made {@code ../x.mp3} look like {@code x.mp3}).
     */
    public static String safeUploadName(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return null;
        if (".".equals(name) || "..".equals(name) || name.contains("..")) return null;
        return name;
    }

    public static File contained(File root, File candidate) {
        if (root == null || candidate == null) return null;
        try {
            File rootCan = root.getCanonicalFile();
            File candCan = candidate.getCanonicalFile();
            String r = rootCan.getAbsolutePath();
            String c = candCan.getAbsolutePath();
            if (c.equals(r) || c.startsWith(r + File.separator)) return candCan;
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
