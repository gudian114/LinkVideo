package com.linkvideo.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinHeaderPolicyTest {
    @Test public void exactHostAndSubdomainReceiveRuntimeCookie() {
        for (String url : new String[] {
                "https://douyin.com/video/1", "https://www.douyin.com/aweme/detail" }) {
            Map<String, String> headers = DouyinHeaderPolicy.forRequest(url, base(),
                    ignored -> "ttwid=runtime-secret");
            assertEquals("ttwid=runtime-secret", headers.get("Cookie"));
            assertEquals("source", headers.get("Referer"));
        }
    }

    @Test public void lookalikeHttpAndUserInfoUrlsNeverReadOrReceiveCookie() {
        AtomicInteger lookups = new AtomicInteger();
        for (String url : new String[] {
                "https://douyin.com.evil.example/video/1",
                "https://evildouyin.com/video/1",
                "http://www.douyin.com/video/1",
                "https://user@www.douyin.com/video/1" }) {
            Map<String, String> headers = DouyinHeaderPolicy.forRequest(url, base(), ignored -> {
                lookups.incrementAndGet();
                return "ttwid=runtime-secret";
            });
            assertFalse(url, headers.containsKey("Cookie"));
        }
        assertEquals(0, lookups.get());
    }

    @Test public void redirectRecomputesOriginAndDoesNotForwardCookieToCdn() {
        Map<String, String> first = DouyinHeaderPolicy.forRequest(
                "https://www.douyin.com/aweme/v1/play/", base(), ignored -> "ttwid=runtime-secret");
        Map<String, String> redirected = DouyinHeaderPolicy.forRequest(
                "https://v3-dy.example-cdn.com/video.mp4", first, ignored -> "ttwid=should-not-run");

        assertEquals("ttwid=runtime-secret", first.get("Cookie"));
        assertFalse(redirected.containsKey("Cookie"));
        assertEquals("source", redirected.get("Referer"));
    }

    @Test public void blankCookieDoesNotCreateAnEmptyHeader() {
        assertEquals(Collections.singletonMap("Referer", "source"),
                DouyinHeaderPolicy.forRequest("https://www.douyin.com/video/1", base(), ignored -> ""));
    }

    private static Map<String, String> base() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", "source");
        headers.put("Cookie", "stale-secret");
        return headers;
    }
}
