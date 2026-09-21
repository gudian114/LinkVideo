package com.linkvideo.parser;

public final class ImageAspect {
    private ImageAspect() { }

    public static int heightForWidth(int targetWidth, int imageWidth, int imageHeight) {
        if (targetWidth <= 0) return 0;
        if (imageWidth <= 0 || imageHeight <= 0) return targetWidth;
        long scaled = Math.round((double) targetWidth * imageHeight / imageWidth);
        int minimum = Math.max(1, targetWidth / 3);
        int maximum = targetWidth * 3;
        return (int) Math.max(minimum, Math.min(maximum, scaled));
    }
}
