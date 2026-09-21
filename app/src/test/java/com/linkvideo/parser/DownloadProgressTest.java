package com.linkvideo.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DownloadProgressTest {
    @Test public void reportsKnownSizeAsPercentAndBytes() {
        DownloadProgress progress = new DownloadProgress("下载中", 5 * 1024 * 1024L, 20 * 1024 * 1024L);
        assertEquals(25, progress.percent());
        assertEquals("5.0 MB / 20.0 MB · 25%", progress.detail());
    }

    @Test public void reportsDownloadedBytesWhenTotalIsUnknown() {
        DownloadProgress progress = new DownloadProgress("下载中", 1536, 0);
        assertEquals(-1, progress.percent());
        assertEquals("已下载 1.5 KB", progress.detail());
    }
}
