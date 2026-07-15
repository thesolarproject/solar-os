package com.solar.launcher.audiobook;

import com.solar.launcher.AudioTags;
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
 * 2026-07-15: Multi-volume roots, tag read, embedded .m4b chapters.
 */
public final class AudiobookLibrary {

    private AudiobookLibrary() {}

    /** Primary Audiobooks folder for new saves — prefers new-media root when ctx given. */
    public static File getRoot() {
        File primary = DeviceFeatures.getPrimaryStorageRoot();
        return new File(primary, "Audiobooks");
    }

    /**
     * Preferred Audiobooks folder for folder browse / new writes.
     * 2026-07-15 — Honors Primary storage pref; scans still use {@link #getRoots()}.
     */
    public static File getRoot(android.content.Context ctx) {
        File base = ctx != null
                ? DeviceFeatures.getNewMediaRoot(ctx)
                : DeviceFeatures.getPrimaryStorageRoot();
        File root = new File(base, "Audiobooks");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    /** All Audiobooks/ folders across volumes. */
    public static List<File> getRoots() {
        List<File> roots = DeviceFeatures.getAudiobookRoots();
        if (roots == null || roots.isEmpty()) {
            roots = new ArrayList<File>();
            roots.add(getRoot());
        }
        for (File r : roots) {
            if (r != null && !r.exists()) r.mkdirs();
        }
        return roots;
    }

    public static boolean isAudioFile(File f) {
        if (f == null || !f.isFile()) return false;
        String n = f.getName().toLowerCase(Locale.US);
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".m4b")
                || n.endsWith(".flac") || n.endsWith(".wav") || n.endsWith(".ogg");
    }

    public static boolean isUnderAudiobookRoot(File f) {
        if (f == null) return false;
        String p = f.getAbsolutePath();
        for (File root : getRoots()) {
            if (root == null) continue;
            String rootPath = root.getAbsolutePath();
            if (p.equals(rootPath) || p.startsWith(rootPath + File.separator)) return true;
        }
        return false;
    }

    /** Walk audiobook trees and collect tagged rows. */
    public static List<MainActivity.SongItem> scanAll() {
        List<MainActivity.SongItem> out = new ArrayList<MainActivity.SongItem>();
        for (File root : getRoots()) {
            if (root == null) continue;
            if (!root.exists()) root.mkdirs();
            walk(root, root, out);
        }
        return out;
    }

    private static void walk(File root, File dir, List<MainActivity.SongItem> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walk(root, f, out);
            } else if (isAudioFile(f)) {
                out.add(songFromFile(root, f, dir));
            }
        }
    }

    /** Build SongItem with tags when present; folder names as author/book fallback. */
    static MainActivity.SongItem songFromFile(File root, File f, File dir) {
        String title = titleFromName(f.getName());
        String book = dir.getName();
        String author = "Unknown Author";
        if (dir.getParentFile() != null && !dir.getParentFile().equals(root)) {
            author = dir.getParentFile().getName();
        } else if (dir.equals(root)) {
            // Single file under Audiobooks/ — author unknown, book = filename
            book = title;
        }
        try {
            AudioTags.Info tags = AudioTags.read(f, null);
            if (tags != null) {
                if (tags.title != null && !tags.title.trim().isEmpty()) title = tags.title.trim();
                if (tags.artist != null && !tags.artist.trim().isEmpty()) author = tags.artist.trim();
                if (tags.album != null && !tags.album.trim().isEmpty()) book = tags.album.trim();
            }
        } catch (Exception ignored) {
        }
        return new MainActivity.SongItem(f, title, author, book, "Audiobook", "", 0, 0);
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

    /** Multi-file chapters in a book folder, sorted by name. */
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

    /**
     * 2026-07-15 — Chapter list for a book: embedded .m4b chapters, else sibling audio files.
     * Returns synthetic Chapter entries with startMs for embedded; multi-file uses 0 and path via title index.
     */
    public static List<Mp4ChapterParser.Chapter> resolveChapters(File bookFileOrDir) {
        List<Mp4ChapterParser.Chapter> out = new ArrayList<Mp4ChapterParser.Chapter>();
        if (bookFileOrDir == null) return out;
        if (bookFileOrDir.isFile()) {
            List<Mp4ChapterParser.Chapter> embedded = Mp4ChapterParser.parse(bookFileOrDir);
            if (!embedded.isEmpty()) return embedded;
            out.add(new Mp4ChapterParser.Chapter(titleFromName(bookFileOrDir.getName()), 0L));
            return out;
        }
        List<File> files = chaptersForBook(bookFileOrDir);
        for (int i = 0; i < files.size(); i++) {
            out.add(new Mp4ChapterParser.Chapter(titleFromName(files.get(i).getName()), i));
        }
        return out;
    }

    /** Prefer cover.jpg beside the book file/folder. */
    public static File folderCover(File bookFileOrDir) {
        if (bookFileOrDir == null) return null;
        File dir = bookFileOrDir.isDirectory() ? bookFileOrDir : bookFileOrDir.getParentFile();
        if (dir == null) return null;
        String[] names = { "cover.jpg", "cover.jpeg", "cover.png", "folder.jpg" };
        for (String n : names) {
            File c = new File(dir, n);
            if (c.isFile()) return c;
        }
        return null;
    }
}
