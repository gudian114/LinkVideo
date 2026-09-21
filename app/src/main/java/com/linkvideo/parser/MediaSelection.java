package com.linkvideo.parser;

public final class MediaSelection {
    private MediaSelection() { }

    public static MediaStream playable(VideoResult result) throws Exception {
        if (result == null) throw new Exception("请先解析视频");
        for (MediaStream stream : result.streams) {
            if ("combined".equals(stream.kind)) return stream;
        }
        for (MediaStream stream : result.streams) {
            if ("video".equals(stream.kind)) return stream;
        }
        throw new Exception("解析结果中没有可打开的视频链接");
    }

    public static MediaStream preview(VideoResult result) throws Exception {
        if (result == null) throw new Exception("请先解析视频");
        for (MediaStream stream : result.streams) {
            if ("preview".equals(stream.kind)) return stream;
        }
        return playable(result);
    }
}
