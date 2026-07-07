package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AlbumNamesTest {

    @Test
    public void isUnknownAlbumTreatsEmptyAndPlaceholder() {
        assertTrue(AlbumNames.isUnknownAlbum(null));
        assertTrue(AlbumNames.isUnknownAlbum(""));
        assertTrue(AlbumNames.isUnknownAlbum("Unknown Album"));
        assertTrue(AlbumNames.isUnknownAlbum("unknown album"));
        assertFalse(AlbumNames.isUnknownAlbum("OK Computer"));
    }

    @Test
    public void unknownAlbumRackKeyScopesByArtist() {
        String a = AlbumNames.unknownAlbumRackKey("Artist A");
        String b = AlbumNames.unknownAlbumRackKey("Artist B");
        assertTrue(a.contains("|"));
        assertTrue(b.contains("|"));
        assertFalse(a.equals(b));
    }
}
