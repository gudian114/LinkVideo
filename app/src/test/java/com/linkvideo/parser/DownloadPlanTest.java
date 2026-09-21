package com.linkvideo.parser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadPlanTest {
    @Test public void usesCombinedMp4Directly() throws Exception {
        MediaStream stream = new MediaStream("https://video.example/file.mp4", "视频", "combined",
                "video/mp4", "avc1.64001f", Collections.emptyMap());
        DownloadPlan plan = DownloadPlan.create(result(stream));
        assertFalse(plan.requiresMux());
        assertEquals(stream, plan.video);
    }

    @Test public void selectsAvcAndAacForBilibiliMp4Mux() throws Exception {
        MediaStream hevc = new MediaStream("https://video.example/hevc.m4s", "1080p", "video",
                "video/mp4", "hev1.1.6.L120", Collections.emptyMap());
        MediaStream avc = new MediaStream("https://video.example/avc.m4s", "1080p", "video",
                "video/mp4", "avc1.640032", Collections.emptyMap());
        MediaStream audio = new MediaStream("https://video.example/audio.m4s", "音频", "audio",
                "audio/mp4", "mp4a.40.2", Collections.emptyMap());
        DownloadPlan plan = DownloadPlan.create(result(hevc, avc, audio));
        assertTrue(plan.requiresMux());
        assertEquals(avc, plan.video);
        assertEquals(audio, plan.audio);
    }

    @Test public void refusesIncompleteOrUnsupportedTracks() {
        MediaStream videoOnly = new MediaStream("https://video.example/avc.m4s", "画面", "video",
                "video/mp4", "avc1.640032", Collections.emptyMap());
        Exception error = org.junit.Assert.assertThrows(Exception.class,
                () -> DownloadPlan.create(result(videoOnly)));
        assertTrue(error.getMessage().contains("音频"));
    }

    @Test public void refusesCombinedStreamWhoseContainerIsNotConfirmedMp4() {
        MediaStream unknown = new MediaStream("https://video.example/file.flv", "视频", "combined",
                "", Collections.emptyMap());
        org.junit.Assert.assertThrows(Exception.class, () -> DownloadPlan.create(result(unknown)));
    }

    private static VideoResult result(MediaStream... streams) {
        return new VideoResult("哔哩哔哩", "测试", "", "", "https://www.bilibili.com/video/test",
                "", 1, Arrays.asList(streams));
    }
}
