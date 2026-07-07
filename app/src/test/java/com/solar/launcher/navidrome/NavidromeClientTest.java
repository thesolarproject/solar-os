package com.solar.launcher.navidrome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NavidromeClientTest {

    @Test
    public void normalizeServerUrl_addsSchemeAndDefaultPortForLan() {
        assertEquals("http://192.168.1.10:4533", NavidromeClient.normalizeServerUrl("192.168.1.10"));
        assertEquals("http://192.168.1.10:4533", NavidromeClient.normalizeServerUrl("192.168.1.10:4533"));
        assertEquals("http://10.0.0.5:4533", NavidromeClient.normalizeServerUrl("http://10.0.0.5"));
    }

    @Test
    public void normalizeServerUrl_keepsPublicHttpsHost() {
        assertEquals("https://music.example.com", NavidromeClient.normalizeServerUrl("https://music.example.com/"));
        assertEquals("http://music.example.com", NavidromeClient.normalizeServerUrl("music.example.com"));
    }

    @Test
    public void normalizeServerUrl_keepsExplicitPort() {
        assertEquals("http://192.168.0.2:8080", NavidromeClient.normalizeServerUrl("http://192.168.0.2:8080"));
    }

    /** 2026-07-06: Mirrors TlsHelper RESTRICTED_TLS-only — http LAN Navidrome URLs fail this way. */
    @Test
    public void restrictedTlsClient_blocksCleartextHttp() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Collections.singletonList(ConnectionSpec.RESTRICTED_TLS))
                .build();
        try {
            client.newCall(new Request.Builder().url("http://192.168.1.10:4533/rest/ping").build()).execute();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            assertTrue("expected cleartext block, got: " + e.getMessage(),
                    msg.contains("cleartext") || msg.contains("not permitted"));
            return;
        }
        throw new AssertionError("expected cleartext HTTP to be blocked");
    }

    /** 2026-07-06: TlsHelper fix — http allowed; fails on network not cleartext policy. */
    @Test
    public void mixedSpecsClient_allowsCleartextHttp() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.CLEARTEXT))
                .build();
        try {
            client.newCall(new Request.Builder().url("http://192.168.1.10:4533/rest/ping").build()).execute();
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("cleartext") || msg.contains("not permitted")) {
                throw new AssertionError("cleartext should be allowed: " + e.getMessage());
            }
            // connection refused / timeout is fine — cleartext path was taken
        }
    }
}
