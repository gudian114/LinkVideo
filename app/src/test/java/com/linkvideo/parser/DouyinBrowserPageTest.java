package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinBrowserPageTest {
    private static final String ID = "7685159514829025465";
    private static final String PAGE = "https://www.douyin.com/video/" + ID;
    private static final String API = "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + ID + "&aid=1128&app_name=aweme";

    @Test public void acceptsVideoAndImagePagesForTheSameWorkOnly() throws Exception {
        assertEquals(ID, new DouyinBrowserPage(PAGE, API).itemId);
        assertEquals(ID, new DouyinBrowserPage(PAGE.replace("/video/", "/note/"), API).itemId);
        reject(PAGE, API.replace(ID, "7685159514829025466"));
        reject(PAGE, API + "&extra=anything");
    }

    @Test public void rejectsOtherOriginsSchemesAndCredentials() throws Exception {
        reject(PAGE.replace("https:", "http:"), API);
        reject(PAGE.replace("www.douyin.com", "www.douyin.com.example.org"), API);
        reject(PAGE.replace("www.douyin.com", "user@www.douyin.com"), API);
        reject(PAGE, API.replace("www.douyin.com", "www.douyin.com:444"));
        reject("file:///sdcard/one", API);
        reject(PAGE, API.replace("/aweme/v1/web/aweme/detail/", "/passport/"));
    }

    @Test public void readinessRequiresSuccessfulMatchingItem() throws Exception {
        DouyinBrowserPage page = new DouyinBrowserPage(PAGE, API);
        assertFalse(page.hasItem("<html>验证页面</html>"));
        assertFalse(page.hasItem("{\"status_code\":0,\"aweme_detail\":null}"));
        assertFalse(page.hasItem("{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"other\"}}"));
        assertFalse(page.hasItem("{\"status_code\":11110,\"aweme_detail\":{\"aweme_id\":\"" + ID + "\"}}"));
        assertTrue(page.hasItem("{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + ID + "\"}}"));
    }

    private static void reject(String page, String api) throws Exception {
        try { new DouyinBrowserPage(page, api); fail("unexpected accepted address"); }
        catch (IllegalArgumentException expected) { }
    }
}
