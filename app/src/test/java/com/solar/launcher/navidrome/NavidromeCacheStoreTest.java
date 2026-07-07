package com.solar.launcher.navidrome;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NavidromeCacheStoreTest {

    @Test
    public void parseArtistsJson() throws Exception {
        String json = "{"
                + "\"subsonic-response\":{"
                + "\"status\":\"ok\","
                + "\"artists\":{\"index\":["
                + "{\"name\":\"A\",\"artist\":[{\"id\":\"1\",\"name\":\"Abba\",\"albumCount\":2}]}"
                + "]}}}";
        List<NavidromeArtist> artists = NavidromeCacheStore.parseArtistsJson(new JSONObject(json));
        assertFalse(artists.isEmpty());
        assertEquals("Abba", artists.get(0).name);
        assertEquals(2, artists.get(0).albumCount);
    }
}
