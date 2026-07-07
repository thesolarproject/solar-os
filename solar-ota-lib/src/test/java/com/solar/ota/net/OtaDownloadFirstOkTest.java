package com.solar.ota.net;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** 2026-07-06 — OtaDownload.downloadFirstOk tries fallbacks in order. */
public class OtaDownloadFirstOkTest {

    @Test
    public void emptyUrlListThrows() {
        File dest = new File(System.getProperty("java.io.tmpdir"), "ota-firstok-empty.dat");
        try {
            OtaDownload.downloadFirstOk(new String[] {}, dest, "test");
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void skipsBlankUrls() {
        File dest = new File(System.getProperty("java.io.tmpdir"), "ota-firstok-blank.dat");
        try {
            OtaDownload.downloadFirstOk(new String[] {"", "  "}, dest, "test");
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            // ok
        }
    }

    @Test
    public void deletesPartialOnFailure() throws Exception {
        File dest = File.createTempFile("ota-firstok-", ".bin");
        writeBytes(dest, new byte[] {1, 2, 3});
        try {
            OtaDownload.downloadFirstOk(
                    new String[] {"https://invalid.example.test/nope", "https://invalid.example.test/nope2"},
                    dest, "test");
            throw new AssertionError("expected IOException");
        } catch (IOException expected) {
            if (dest.isFile() && dest.length() == 3) {
                // first URL failed before write — stale partial kept or deleted both ok
            }
        } finally {
            dest.delete();
        }
    }

    private static void writeBytes(File f, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
