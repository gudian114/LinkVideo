package com.linkvideo.parser;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public final class ImageFormat {
    public final String mimeType;
    public final String extension;

    private ImageFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static ImageFormat detect(String contentType, String finalUrl, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("图片内容为空");
        ImageFormat signature = fromSignature(bytes);
        if (signature != null) return signature;
        if (looksLikeHtml(bytes)) throw new IOException("平台返回的内容不是图片");
        String mime = normalizeMime(contentType);
        ImageFormat typed = fromMime(mime);
        if (typed != null) return typed;
        ImageFormat path = fromPath(finalUrl);
        if (path != null && (mime.isEmpty() || "application/octet-stream".equals(mime))) return path;
        throw new IOException("平台返回的图片格式不受支持");
    }

    private static ImageFormat fromSignature(byte[] bytes) {
        if (bytes.length >= 3 && unsigned(bytes[0]) == 0xff && unsigned(bytes[1]) == 0xd8
                && unsigned(bytes[2]) == 0xff) return new ImageFormat("image/jpeg", ".jpg");
        if (bytes.length >= 8 && unsigned(bytes[0]) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26
                && bytes[7] == 10) return new ImageFormat("image/png", ".png");
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP"))
            return new ImageFormat("image/webp", ".webp");
        if (bytes.length >= 6 && (ascii(bytes, 0, "GIF87a") || ascii(bytes, 0, "GIF89a")))
            return new ImageFormat("image/gif", ".gif");
        return null;
    }

    private static ImageFormat fromMime(String mime) {
        if ("image/jpeg".equals(mime) || "image/jpg".equals(mime)) return new ImageFormat("image/jpeg", ".jpg");
        if ("image/png".equals(mime)) return new ImageFormat(mime, ".png");
        if ("image/webp".equals(mime)) return new ImageFormat(mime, ".webp");
        if ("image/gif".equals(mime)) return new ImageFormat(mime, ".gif");
        return null;
    }

    private static ImageFormat fromPath(String value) {
        try {
            String path = new URI(value == null ? "" : value).getPath();
            String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return new ImageFormat("image/jpeg", ".jpg");
            if (lower.endsWith(".png")) return new ImageFormat("image/png", ".png");
            if (lower.endsWith(".webp")) return new ImageFormat("image/webp", ".webp");
            if (lower.endsWith(".gif")) return new ImageFormat("image/gif", ".gif");
        } catch (Exception ignored) { }
        return null;
    }

    private static boolean looksLikeHtml(byte[] bytes) {
        int length = Math.min(bytes.length, 64);
        String prefix;
        try { prefix = new String(bytes, 0, length, "UTF-8").trim().toLowerCase(Locale.ROOT); }
        catch (Exception ignored) { return false; }
        return prefix.startsWith("<html") || prefix.startsWith("<!doctype")
                || prefix.startsWith("<script") || prefix.contains("<body");
    }

    private static String normalizeMime(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static int unsigned(byte value) { return value & 0xff; }

    private static boolean ascii(byte[] bytes, int at, String value) {
        if (at < 0 || bytes.length < at + value.length()) return false;
        for (int i = 0; i < value.length(); i++) if (bytes[at + i] != (byte) value.charAt(i)) return false;
        return true;
    }
}
