package com.solar.launcher.audiobook;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AudiobookBookmarksTest {

    @Test
    public void formatMs() {
        assertEquals("1:05", AudiobookBookmarks.formatMs(65000));
        assertEquals("1:01:05", AudiobookBookmarks.formatMs(3665000));
    }
}
