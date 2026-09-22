package com.linkvideo.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DownloadFileNamingTest {
    @Test public void firstFileUsesFixedDownloadFileName() {
        assertEquals("downloadfile.mp4",
                DownloadFileNaming.nextAvailableName(".mp4", Collections.emptyList()));
    }

    @Test public void duplicateFilesUseFirstAvailableNumber() {
        assertEquals("downloadfile (3).mp4", DownloadFileNaming.nextAvailableName(".mp4",
                Arrays.asList("downloadfile.mp4", "downloadfile (1).mp4", "downloadfile (2).mp4")));
    }

    @Test public void numberingFillsTheFirstGap() {
        assertEquals("downloadfile (1).jpg", DownloadFileNaming.nextAvailableName("jpg",
                Arrays.asList("downloadfile.jpg", "downloadfile (2).jpg")));
    }

    @Test public void collisionCheckIsCaseInsensitive() {
        assertEquals("downloadfile (1).png", DownloadFileNaming.nextAvailableName(".png",
                Collections.singletonList("DownloadFile.PNG")));
    }
}
