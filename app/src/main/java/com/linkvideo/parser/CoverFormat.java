package com.linkvideo.parser;

import java.util.Locale;

public final class CoverFormat {
    public final String mimeType;
    public final String extension;

    private CoverFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static CoverFormat fromContentType(String value) {
        String mime = value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/png".equals(mime)) return new CoverFormat(mime, ".png");
        if ("image/webp".equals(mime)) return new CoverFormat(mime, ".webp");
        if ("image/gif".equals(mime)) return new CoverFormat(mime, ".gif");
        return new CoverFormat("image/jpeg", ".jpg");
    }
}
