package com.solar.launcher;

import org.junit.Test;

import java.util.List;

public class SolarDonatorsClientTest {

    @Test
    public void selfCheck() {
        SolarDonatorsClient.selfCheck();
    }

    @Test
    public void parseEmptyXml() {
        List<SolarDonatorsClient.Donator> out = SolarDonatorsClient.parseDonatorsXml("");
        if (!out.isEmpty()) {
            throw new AssertionError("empty xml should yield empty list");
        }
    }

    @Test
    public void parseMessageAlias() {
        String xml = "<donators><donator name=\"Pat\" message=\"Thanks Solar\"/></donators>";
        List<SolarDonatorsClient.Donator> out = SolarDonatorsClient.parseDonatorsXml(xml);
        if (out.size() != 1 || !"Thanks Solar".equals(out.get(0).note)) {
            throw new AssertionError("message attr should map to note");
        }
    }
}
