package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class LinkInputTest {
    @Test public void extractsUrlFromChineseShareText() throws Exception {
        LinkInput value = LinkInput.parse("3.14 打开抖音看看 https://v.douyin.com/AbC_12/，复制此链接");
        assertEquals("https://v.douyin.com/AbC_12/", value.url);
        assertEquals("抖音", value.platform);
    }
    @Test public void acceptsBvId() throws Exception {
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", LinkInput.parse("BV1xx411c7mD").url);
    }
    @Test public void preservesPartSelection() throws Exception {
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD?p=2", LinkInput.parse("https://www.bilibili.com/video/BV1xx411c7mD?p=2").url);
    }
    @Test public void recognizesKuaishouShortLink() throws Exception {
        assertEquals("快手", LinkInput.parse("看看这个：https://v.kuaishou.com/abc123").platform);
    }
    @Test public void rejectsLookalikeHost() {
        assertThrows(Exception.class, () -> LinkInput.parse("https://douyin.com.evil.example/video/123"));
    }
    @Test public void rejectsCredentialsInUrl() {
        assertThrows(Exception.class, () -> LinkInput.parse("https://evil@www.douyin.com/video/123"));
    }
    @Test public void rejectsAmbiguousMultipleLinks() {
        assertThrows(Exception.class, () -> LinkInput.parse("https://v.douyin.com/a/ https://b23.tv/abc"));
    }
    @Test public void rejectsEmptyAndUnsupportedText() {
        assertThrows(Exception.class, () -> LinkInput.parse("  "));
        assertThrows(Exception.class, () -> LinkInput.parse("https://example.com/video.mp4"));
    }
}
