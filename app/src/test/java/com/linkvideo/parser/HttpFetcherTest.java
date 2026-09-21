package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpFetcherTest {
    @Test public void trustsKuaishouOwnedChenzhongtechLandingPages() {
        assertTrue(HttpFetcher.isAllowedPageHost("kph8gvfz.m.chenzhongtech.com"));
        assertFalse(HttpFetcher.isAllowedPageHost("chenzhongtech.com.evil.example"));
    }

    @Test public void trustsOnlyTheExactTtwidRegistrationHost() {
        assertTrue(HttpFetcher.isAllowedPageHost("ttwid.bytedance.com"));
        assertFalse(HttpFetcher.isAllowedPageHost("ttwid.bytedance.com.evil.example"));
    }
}
