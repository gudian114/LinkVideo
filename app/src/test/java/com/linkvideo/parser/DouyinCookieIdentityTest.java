package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinCookieIdentityTest {
    @Test public void preferenceCookiesDoNotCountAsVerifiedIdentity() {
        assertFalse(DouyinCookieIdentity.hasStableIdentity("theme=dark; locale=zh-CN"));
        assertFalse(DouyinCookieIdentity.hasStableIdentity("notttwid=fake"));
    }

    @Test public void visitorOrSignedInCookiesCountAsIdentity() {
        assertTrue(DouyinCookieIdentity.hasStableIdentity("ttwid=visitor"));
        assertTrue(DouyinCookieIdentity.hasStableIdentity("foo=1; odin_tt=visitor"));
        assertTrue(DouyinCookieIdentity.hasStableIdentity("sessionid_ss=signed-in"));
    }

    @Test public void identityComparisonIgnoresUnrelatedCookieChanges() {
        assertFalse(DouyinCookieIdentity.changed("ttwid=same; theme=dark",
                "theme=light; ttwid=same"));
        assertTrue(DouyinCookieIdentity.changed("ttwid=old", "ttwid=new"));
        assertTrue(DouyinCookieIdentity.changed("", "ttwid=new"));
    }
}
