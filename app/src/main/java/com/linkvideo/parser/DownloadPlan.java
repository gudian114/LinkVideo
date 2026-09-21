package com.linkvideo.parser;

import java.util.Locale;

public final class DownloadPlan {
    public final MediaStream video;
    public final MediaStream audio;

    private DownloadPlan(MediaStream video, MediaStream audio) {
        this.video = video;
        this.audio = audio;
    }

    public static DownloadPlan create(VideoResult result) throws Exception {
        for (MediaStream stream : result.streams) {
            if ("combined".equals(stream.kind) && isMp4(stream)) {
                return new DownloadPlan(stream, null);
            }
        }

        MediaStream video = null;
        MediaStream audio = null;
        for (MediaStream stream : result.streams) {
            if (video == null && "video".equals(stream.kind) && isAvc(stream)) video = stream;
            if (audio == null && "audio".equals(stream.kind) && isAac(stream)) audio = stream;
        }
        if (video == null) throw new Exception("没有找到可封装为 MP4 的 H.264 画面轨");
        if (audio == null) throw new Exception("没有找到可封装为 MP4 的 AAC 音频轨");
        return new DownloadPlan(video, audio);
    }

    public boolean requiresMux() { return audio != null; }

    private static boolean isMp4(MediaStream stream) {
        return "video/mp4".equalsIgnoreCase(stream.mimeType);
    }

    private static boolean isAvc(MediaStream stream) {
        String codec = stream.codecs.toLowerCase(Locale.ROOT);
        return isMp4(stream) && (codec.isEmpty() || codec.startsWith("avc1") || codec.startsWith("avc3"));
    }

    private static boolean isAac(MediaStream stream) {
        String codec = stream.codecs.toLowerCase(Locale.ROOT);
        return "audio/mp4".equalsIgnoreCase(stream.mimeType)
                && (codec.isEmpty() || codec.startsWith("mp4a"));
    }
}
