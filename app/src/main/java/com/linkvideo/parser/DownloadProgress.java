package com.linkvideo.parser;

import java.util.Locale;

public final class DownloadProgress {
    public final String message;
    public final long downloadedBytes;
    public final long totalBytes;

    public DownloadProgress(String message, long downloadedBytes, long totalBytes) {
        this.message = message == null ? "" : message;
        this.downloadedBytes = Math.max(0, downloadedBytes);
        this.totalBytes = Math.max(0, totalBytes);
    }

    public int percent() {
        if (totalBytes <= 0) return -1;
        return (int) Math.min(100, downloadedBytes * 100 / totalBytes);
    }

    public String detail() {
        if (totalBytes <= 0) {
            return downloadedBytes <= 0 ? "正在连接媒体服务器…" : "已下载 " + formatBytes(downloadedBytes);
        }
        return formatBytes(downloadedBytes) + " / " + formatBytes(totalBytes) + " · " + percent() + "%";
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(Locale.ROOT, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024) return String.format(Locale.ROOT, "%.1f MB", value);
        return String.format(Locale.ROOT, "%.2f GB", value / 1024.0);
    }
}
