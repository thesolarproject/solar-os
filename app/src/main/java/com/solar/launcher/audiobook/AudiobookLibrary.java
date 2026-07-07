package com.solar.launcher.audiobook;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.MainActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 2026-07-06: Audiobook scan root + chapter lists — separate from music library.
 */
public final class AudiobookLibrary {

    private AudiobookLibrary() {}

    /** Primary volume Audiobooks folder (Y1/Y2 aware). */
    public static File getRoot() {
        File primary = DeviceFeatures.getPrimaryStorageRoot();
        return new File(primary, "Audiobooks");
    }

    public static boolean isAudioFile(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".m4b")
                || n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".ogg");
    }

    public static boolean isUnderAudiobookRoot(File f) {
        if (f == null) return false;
        String root = getRoot().getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.equals(root) || p.startsWith(root + File.separator);
    }

    /** Walk audiobook tree and collect tagged rows. */
    public static List<MainActivity.SongItem> scanAll() {
        List<MainActivity.SongItem> out = new ArrayList<MainActivity.SongItem>();
        File root = getRoot();
        if (!root.exists()) root.mkdirs();
        walk(root, out);
        return out;
    }

    private static void walk(File dir, List<MainActivity.SongItem> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) walk(f, out);
            else if (isAudioFile(f)) {
                String title = titleFromName(f.getName());
                String book = dir.getName();
                String author = dir.getParentFile() != null && !dir.getParentFile().equals(getRoot())
                        ? dir.getParentFile().getName() : "Unknown Author";
                out.add(new MainActivity.SongItem(f, title, author, book, "Audiobook", "", 0, 0));
            }
        }
    }

    static String titleFromName(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static List<String> collectAuthors(List<MainActivity.SongItem> library) {
        Set<String> set = new HashSet<String>();
        if (library != null) {
            for (MainActivity.SongItem s : library) {
                if (s.artist != null && !s.artist.trim().isEmpty()) set.add(s.artist.trim());
            }
        }
        List<String> out = new ArrayList<String>(set);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static List<String> collectBooks(List<MainActivity.SongItem> library, String author) {
        Set<String> set = new HashSet<String>();
        if (library != null) {
            for (MainActivity.SongItem s : library) {
                if (author != null && !author.equals(s.artist)) continue;
                if (s.album != null && !s.album.trim().isEmpty()) set.add(s.album.trim());
            }
        }
        List<String> out = new ArrayList<String>(set);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static List<File> chaptersForBook(File bookDir) {
        List<File> out = new ArrayList<File>();
        if (bookDir == null || !bookDir.isDirectory()) return out;
        File[] files = bookDir.listFiles();
        if (files == null) return out;
        for (File f : files) if (isAudioFile(f)) out.add(f);
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return out;
    }
}
