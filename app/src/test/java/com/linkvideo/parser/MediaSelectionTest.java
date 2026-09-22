package com.linkvideo.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MediaSelectionTest {
    @Test public void previewPrefersCombinedPreviewStreamOverDashVideo() throws Exception {
        MediaStream video = new MediaStream("https://video.example/dash.m4s", "高清画面", "video", "video/mp4", Collections.emptyMap());
        MediaStream preview = new MediaStream("https://video.example/preview.mp4", "预览", "preview", "video/mp4", Collections.emptyMap());
        assertEquals("https://video.example/preview.mp4", MediaSelection.preview(result(video, preview)).url);
    }

    @Test public void prefersCombinedVideoForBrowserAndClipboard() throws Exception {
        MediaStream videoOnly = stream("https://video.example/video.m4s", "video");
        MediaStream combined = stream("https://video.example/full.mp4", "combined");
        assertEquals(combined, MediaSelection.playable(result(videoOnly, combined)));
    }

    @Test public void fallsBackToVideoTrackForDashResults() throws Exception {
        MediaStream audio = stream("https://video.example/audio.m4s", "audio");
        MediaStream video = stream("https://video.example/video.m4s", "video");
        assertEquals(video, MediaSelection.playable(result(audio, video)));
    }

    private static MediaStream stream(String url, String kind) {
        return new MediaStream(url, "", kind, "video/mp4", Collections.emptyMap());
    }

    private static VideoResult result(MediaStream... streams) {
        return new VideoResult("平台", "标题", "", "https://image.example/cover.jpg",
                "https://example.com/source", "", 1, Arrays.asList(streams));
    }
}
