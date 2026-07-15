package com.solar.launcher.podcast;

import com.solar.launcher.AudioTags;

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-15 — Folder layout, nested seasons, ID3 show/episode, dual-root merge.
 */
public class PodcastLibraryTest {

    @After
    public void tearDown() {
        PodcastLibrary.resetTestHooks();
    }

    @Test
    public void selfCheck() {
        PodcastLibrary.selfCheck();
    }

    @Test
    public void folderLayout_listsShowAndEpisodeByPath() throws Exception {
        File root = tempDir("pod-folder");
        File show = new File(root, "Daily News");
        assertTrue(show.mkdirs());
        File ep = touch(new File(show, "Morning Brief.mp3"));

        PodcastLibrary.rootsOverrideForTest = singletonRoot(root);

        List<String> shows = PodcastLibrary.listSavedShows();
        assertEquals(1, shows.size());
        assertEquals("Daily News", shows.get(0));

        List<File> eps = PodcastLibrary.listSavedEpisodes("Daily News");
        assertEquals(1, eps.size());
        assertEquals(ep.getAbsolutePath(), eps.get(0).getAbsolutePath());
        assertEquals("Morning Brief", PodcastLibrary.episodeTitleFor(ep));
    }

    @Test
    public void nestedSeason_usesFirstSegmentAsShowWithoutTags() throws Exception {
        File root = tempDir("pod-nested");
        File season = new File(root, "MyShow/S01");
        assertTrue(season.mkdirs());
        File ep = touch(new File(season, "ep01.mp3"));

        PodcastLibrary.rootsOverrideForTest = singletonRoot(root);

        List<String> shows = PodcastLibrary.listSavedShows();
        assertEquals(1, shows.size());
        assertEquals("MyShow", shows.get(0));
        assertEquals(1, PodcastLibrary.listSavedEpisodes("MyShow").size());
        assertEquals(ep.getName(), PodcastLibrary.listSavedEpisodes("MyShow").get(0).getName());
    }

    @Test
    public void looseFile_withoutTags_unknownShow() throws Exception {
        File root = tempDir("pod-loose");
        assertTrue(root.mkdirs() || root.isDirectory());
        touch(new File(root, "orphan.mp3"));

        PodcastLibrary.rootsOverrideForTest = singletonRoot(root);

        List<String> shows = PodcastLibrary.listSavedShows();
        assertEquals(1, shows.size());
        assertEquals(PodcastLibrary.UNKNOWN_SHOW, shows.get(0));
        assertEquals(1, PodcastLibrary.listSavedEpisodes(PodcastLibrary.UNKNOWN_SHOW).size());
    }

    @Test
    public void id3Album_groupsShowAndTitle() throws Exception {
        File root = tempDir("pod-id3");
        assertTrue(root.mkdirs() || root.isDirectory());
        File flat = touch(new File(root, "weird-name.mp3"));

        AudioTags.Info meta = new AudioTags.Info();
        meta.album = "Tagged Show";
        meta.title = "Tagged Episode";
        PodcastLibrary.metaOverrideForTest = new HashMap<String, AudioTags.Info>();
        PodcastLibrary.metaOverrideForTest.put(flat.getAbsolutePath(), meta);
        PodcastLibrary.rootsOverrideForTest = singletonRoot(root);

        List<String> shows = PodcastLibrary.listSavedShows();
        assertEquals(1, shows.size());
        assertEquals("Tagged Show", shows.get(0));
        assertEquals("Tagged Episode", PodcastLibrary.episodeTitleFor(flat));
        assertEquals("Tagged Episode",
                PodcastLibrary.episodeFromSavedFile(flat, "Tagged Show").title);
    }

    @Test
    public void dualRoot_sameShowMergesAndDedupesByFilename() throws Exception {
        File a = tempDir("pod-a");
        File b = tempDir("pod-b");
        File showA = new File(a, "CrossCard");
        File showB = new File(b, "crosscard");
        assertTrue(showA.mkdirs());
        assertTrue(showB.mkdirs());
        touch(new File(showA, "ep.mp3"));
        touch(new File(showB, "ep.mp3"));
        touch(new File(showB, "other.mp3"));

        List<File> roots = new ArrayList<File>();
        roots.add(a);
        roots.add(b);
        PodcastLibrary.rootsOverrideForTest = roots;

        List<String> shows = PodcastLibrary.listSavedShows();
        assertEquals(1, shows.size());
        assertEquals("CrossCard", shows.get(0));

        List<File> eps = PodcastLibrary.listSavedEpisodes("CrossCard");
        assertEquals(2, eps.size());
        // Case-insensitive show key should still find files from lower-case folder.
        assertEquals(2, PodcastLibrary.listSavedEpisodes("crosscard").size());
    }

    @Test
    public void findSaved_searchesAllRoots() throws Exception {
        File a = tempDir("pod-find-a");
        File b = tempDir("pod-find-b");
        File showB = new File(b, PodcastLibrary.sanitize("Show One", 60));
        assertTrue(showB.mkdirs());
        String epTitle = "Ep Two";
        File expected = touch(new File(showB,
                PodcastLibrary.sanitize(epTitle, 80) + ".mp3"));

        List<File> roots = new ArrayList<File>();
        roots.add(a);
        roots.add(b);
        PodcastLibrary.rootsOverrideForTest = roots;

        File hit = PodcastLibrary.findSaved("Show One", epTitle, "https://x.test/x.mp3");
        assertEquals(expected.getAbsolutePath(), hit.getAbsolutePath());
    }

    @Test
    public void hasSavedContent_tracksList() throws Exception {
        File root = tempDir("pod-has");
        PodcastLibrary.rootsOverrideForTest = singletonRoot(root);
        assertFalse(PodcastLibrary.hasSavedContent());
        File show = new File(root, "S");
        assertTrue(show.mkdirs());
        touch(new File(show, "e.mp3"));
        assertTrue(PodcastLibrary.hasSavedContent());
    }

    private static List<File> singletonRoot(File root) {
        List<File> roots = new ArrayList<File>();
        roots.add(root);
        return roots;
    }

    private static File tempDir(String prefix) throws IOException {
        File f = File.createTempFile(prefix, "");
        if (!f.delete()) throw new IOException("delete " + f);
        if (!f.mkdir()) throw new IOException("mkdir " + f);
        return f;
    }

    private static File touch(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("mkdirs " + parent);
        }
        FileOutputStream out = new FileOutputStream(f);
        out.write(1);
        out.close();
        // Ensure length > 0 for library filters.
        if (f.length() <= 0) throw new IOException("empty " + f);
        return f;
    }
}
