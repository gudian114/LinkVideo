package com.linkvideo.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class DownloadTransfer {
    interface ProgressListener { void onProgress(Progress progress); }

    static final class Progress {
        private final long downloadedBytes;
        private final long expectedBytes;

        Progress(long downloadedBytes, long expectedBytes) {
            this.downloadedBytes = downloadedBytes;
            this.expectedBytes = expectedBytes;
        }

        long downloadedBytes() { return downloadedBytes; }
        long expectedBytes() { return expectedBytes; }
        int percent() {
            if (expectedBytes <= 0) return -1;
            return (int) Math.min(100, downloadedBytes * 100L / expectedBytes);
        }
    }

    private DownloadTransfer() { }

    static long copy(InputStream source, File partial, long expectedBytes, long maximumBytes,
            CancellationToken token, ProgressListener listener) throws IOException {
        if (source == null || partial == null || token == null || listener == null) {
            throw new IllegalArgumentException("下载参数不完整");
        }
        if (expectedBytes < 0 || maximumBytes <= 0 || expectedBytes > maximumBytes) {
            throw new IOException("安装包过大或大小无效");
        }
        File parent = partial.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建下载目录");
        }
        long total = 0;
        boolean complete = false;
        try (InputStream input = source; OutputStream output = new FileOutputStream(partial, false)) {
            byte[] buffer = new byte[32 * 1024];
            while (true) {
                token.throwIfCanceled();
                int count = input.read(buffer);
                if (count < 0) break;
                if (count == 0) continue;
                total += count;
                if (total > maximumBytes) throw new IOException("安装包过大");
                output.write(buffer, 0, count);
                token.throwIfCanceled();
                listener.onProgress(new Progress(total, expectedBytes));
            }
            output.flush();
            if (expectedBytes > 0 && total != expectedBytes) throw new IOException("下载大小不完整");
            complete = true;
            return total;
        } finally {
            if (!complete && partial.exists() && !partial.delete()) partial.deleteOnExit();
        }
    }
}
