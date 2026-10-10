package com.linkvideo.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SerializableHeadersTest {
    @Test public void stripsAuthenticationHeadersCaseInsensitively() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Referer", "https://www.douyin.com/video/1");
        source.put("cOoKiE", "ttwid=secret-canary");
        source.put("Authorization", "Bearer secret-canary");
        source.put("proxy-authorization", "secret-canary");

        Map<String, String> safe = SerializableHeaders.copy(source);

        assertEquals(Collections.singletonMap("Referer", "https://www.douyin.com/video/1"), safe);
        assertFalse(safe.toString().contains("secret-canary"));
    }

    @Test public void streamAndImageNeverRetainCookieCanary() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("User-Agent", "mobile");
        source.put("Cookie", "ttwid=secret-canary");

        MediaStream stream = new MediaStream("https://video.example/a.mp4", "视频", "combined",
                "video/mp4", source);
        ImageResource image = new ImageResource(Collections.singletonList("https://img.example/a.jpg"),
                source, 1, 1);

        assertEquals("mobile", stream.headers.get("User-Agent"));
        assertEquals("mobile", image.headers.get("User-Agent"));
        assertFalse(stream.headers.containsKey("Cookie"));
        assertFalse(image.headers.containsKey("Cookie"));
    }
}
