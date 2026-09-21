package com.linkvideo.parser;

final class PreviewAspect {
    private static final double MIN_HEIGHT_RATIO = 9.0 / 16.0;
    private static final double MAX_HEIGHT_RATIO = 16.0 / 9.0;

    private PreviewAspect() { }

    static int heightForWidth(int containerWidth, int mediaWidth, int mediaHeight) {
        if (containerWidth <= 0) return 0;
        double ratio = mediaWidth > 0 && mediaHeight > 0
                ? (double) mediaHeight / mediaWidth : MIN_HEIGHT_RATIO;
        ratio = Math.max(MIN_HEIGHT_RATIO, Math.min(MAX_HEIGHT_RATIO, ratio));
        return (int) Math.round(containerWidth * ratio);
    }
}
