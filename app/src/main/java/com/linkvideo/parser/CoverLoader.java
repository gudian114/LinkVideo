package com.linkvideo.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.function.LongSupplier;

/** Fetches a cover independently of the Android bitmap decoder. */
final class CoverLoader {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long ATTEMPT_NANOS = 4_000_000_000L;
    private static final long TOTAL_NANOS = 12_000_000_000L;
    interface Decoder<T> { T decode(byte[] bytes) throws IOException; }
    interface Connections { HttpURLConnection open(URL url) throws IOException; }

    private final Connections connections;
    private final LongSupplier clock;

    CoverLoader() { this(url -> (HttpURLConnection) url.openConnection(), System::nanoTime); }

    CoverLoader(Connections connections, LongSupplier clock) {
        this.connections = connections;
        this.clock = clock;
    }

    <T> T load(VideoResult result, Decoder<T> decoder) throws IOException {
        long deadline = clock.getAsLong() + TOTAL_NANOS;
        IOException last = new IOException("没有可用的封面地址");
        int attempts = 0;
        for (String url : result.coverUrls) {
            if (attempts++ == 8) break;
            remainingMillis(deadline);
            try {
                return read(url, result.sourceUrl, Math.min(deadline, clock.getAsLong() + ATTEMPT_NANOS), decoder);
            } catch (IOException error) {
                if (Thread.currentThread().isInterrupted()) throw error;
                last = error;
            }
        }
        throw last;
    }

    private <T> T read(String coverUrl, String referer, long deadline, Decoder<T> decoder) throws IOException {
        URL url = new URL(coverUrl);
        for (int hop = 0; hop <= 4; hop++) {
            remainingMillis(deadline);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost().isEmpty()
                    || url.getUserInfo() != null) throw new IOException("封面地址必须使用 HTTPS");
            HttpURLConnection connection = connections.open(url);
            connection.setConnectTimeout(remainingMillis(deadline));
            connection.setReadTimeout(remainingMillis(deadline));
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
            try {
                connection.connect();
                connection.setReadTimeout(remainingMillis(deadline));
                int response = connection.getResponseCode();
                remainingMillis(deadline);
                if (response >= 300 && response < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == 4) throw new IOException("封面跳转失败");
                    url = new URL(url, location);
                    continue;
                }
                if (response != HttpURLConnection.HTTP_OK) throw new IOException("封面读取失败（HTTP " + response + "）");
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("封面过大");
                try (InputStream input = connection.getInputStream();
                        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while (true) {
                        connection.setReadTimeout(remainingMillis(deadline));
                        count = input.read(buffer);
                        if (count == -1) break;
                        if (output.size() + count > MAX_BYTES) throw new IOException("封面过大");
                        output.write(buffer, 0, count);
                    }
                    remainingMillis(deadline);
                    T image = decoder.decode(output.toByteArray());
                    if (image == null) throw new IOException("封面格式不支持");
                    return image;
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("封面跳转过多");
    }

    private int remainingMillis(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("封面加载已取消");
        long nanos = deadline - clock.getAsLong();
        if (nanos <= 0) throw new SocketTimeoutException("封面加载超时");
        return (int) Math.max(1, nanos / 1_000_000L);
    }
}
