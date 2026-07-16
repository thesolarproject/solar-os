package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioTagWriterTest {

    @Test
    public void wavReturnsUnsupported() {
        File wav = new File("/music/track.wav");
        assertEquals(AudioTagWriter.EmbedResult.UNSUPPORTED_FORMAT, AudioTagWriter.capabilityFor(wav));
        assertFalse(AudioTagWriter.supportsEmbedding(wav));
    }

    @Test
    public void unknownExtensionReturnsUnsupported() {
        File txt = new File("/music/readme.txt");
        assertEquals(AudioTagWriter.EmbedResult.UNSUPPORTED_FORMAT, AudioTagWriter.capabilityFor(txt));
    }

    @Test
    public void mp3SupportsEmbedding() {
        File mp3 = new File("/music/track.mp3");
        assertTrue(AudioTagWriter.supportsEmbedding(mp3));
    }

    @Test
    public void flacGateDependsOnSdk() {
        // 2026-07-16 — API ≤17 rejects FLAC (VerifyError); host unit tests often report SDK 0.
        File flac = new File("/music/track.flac");
        if (android.os.Build.VERSION.SDK_INT <= 17) {
            assertFalse(AudioTagWriter.supportsEmbedding(flac));
            assertEquals(AudioTagWriter.EmbedResult.UNSUPPORTED_FORMAT,
                    AudioTagWriter.capabilityFor(flac));
        } else {
            assertTrue(AudioTagWriter.supportsEmbedding(flac));
        }
    }

    @Test
    public void sidecarBaseNameStripsAudioExtensions() {
        assertEquals("Album-Song", AudioTagWriter.sidecarBaseName("Album-Song.wav"));
        assertEquals("Album-Song", AudioTagWriter.sidecarBaseName("Album-Song.flac"));
    }
}
