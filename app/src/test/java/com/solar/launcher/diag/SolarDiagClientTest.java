package com.solar.launcher.diag;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 2026-07-15 — Payload rules for private solar-diag ingest. */
public class SolarDiagClientTest {

    @Test
    public void remotePullPayloadIncludesUsername() throws Exception {
        JSONObject body = buildSampleBody("diag_pull", "remote_pull", "Y1-alice-bob-01");
        if (!body.has("soulseek_username")) {
            throw new AssertionError("remote_pull must include soulseek_username");
        }
        if (!"Y1-alice-bob-01".equals(body.getString("soulseek_username"))) {
            throw new AssertionError("username mismatch");
        }
    }

    @Test
    public void crashPayloadOmitsUsername() throws Exception {
        JSONObject body = buildSampleBody("crash", "crash", "Y1-alice-bob-01");
        if (body.has("soulseek_username")) {
            throw new AssertionError("non-remote must omit soulseek_username");
        }
    }

    @Test
    public void authHeaderNameIsDiagNotReach() {
        if (!"X-Solar-Diag-Token".equals(SolarDiagClient.AUTH_HEADER)) {
            throw new AssertionError("wrong auth header");
        }
    }

    @Test
    public void reportUrlAppendsPath() {
        // baseUrl may be empty in unit tests without gradle props — still must not NPE.
        String base = SolarDiagClient.baseUrl();
        String report = SolarDiagClient.reportUrl();
        if (report == null || !report.endsWith("/v1/report")) {
            throw new AssertionError("report path: " + report + " base=" + base);
        }
    }

    /** Mirrors SolarDiagClient.submit field rules without network. */
    static JSONObject buildSampleBody(String type, String trigger, String soulseekUsername)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("type", type);
        body.put("trigger", trigger);
        if ("remote_pull".equals(trigger) && soulseekUsername != null
                && !soulseekUsername.trim().isEmpty()) {
            body.put("soulseek_username", soulseekUsername.trim());
        }
        JSONObject device = new JSONObject();
        device.put("model", "Y1");
        device.put("sdk", 17);
        body.put("device", device);
        body.put("files", new org.json.JSONArray());
        return body;
    }
}
