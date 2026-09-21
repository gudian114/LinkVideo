package com.linkvideo.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

public final class ImageLoader {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final String DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36";

    interface Connections { HttpURLConnection open(URL url) throws IOException; }

    public static final class Payload {
        public final byte[] bytes;
        public final String contentType;
        public final String finalUrl;

        Payload(byte[] bytes, String contentType, String finalUrl) {
            this.bytes = bytes;
            this.contentType = contentType == null ? "" : contentType;
            this.finalUrl = finalUrl;
        }
    }

    private final Connections connections;

    public ImageLoader() { this(url -> (HttpURLConnection) url.openConnection()); }
    ImageLoader(Connections connections) { this.connections = connections; }

    public Payload load(ImageResource resource) throws IOException {
        if (resource == null) throw new IOException("图片信息不存在");
        IOException last = new IOException("没有可用的图片地址");
        for (String candidate : resource.urls) {
            checkCancelled();
            try {
                return read(candidate, resource.headers);
            } catch (IOException error) {
                if (Thread.currentThread().isInterrupted()) throw error;
                last = error;
            }
        }
        throw last;
    }

    private Payload read(String candidate, Map<String, String> headers) throws IOException {
        URL current = checkedUrl(candidate);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkCancelled();
            HttpURLConnection connection = connections.open(current);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/gif,image/*;q=0.8");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (!containsHeader(headers, "User-Agent")) connection.setRequestProperty("User-Agent", DEFAULT_UA);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey() != null && header.getValue() != null && !header.getValue().isEmpty())
                    connection.setRequestProperty(header.getKey(), header.getValue());
            }
            try {
                connection.connect();
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || hop == MAX_REDIRECTS) throw new IOException("图片地址跳转失败");
                    current = checkedUrl(new URL(current, location).toString());
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("图片读取失败（HTTP " + status + "）");
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("图片文件超过 20 MB");
                byte[] bytes = readLimited(connection.getInputStream());
                String contentType = connection.getContentType();
                ImageFormat.detect(contentType, current.toString(), bytes);
                return new Payload(bytes, contentType, current.toString());
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("图片地址跳转次数过多");
    }

    private static byte[] readLimited(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = source.read(buffer)) != -1) {
                checkCancelled();
                if (output.size() + count > MAX_BYTES) throw new IOException("图片文件超过 20 MB");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static URL checkedUrl(String value) throws IOException {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IOException("图片地址必须使用 HTTPS");
            return uri.toURL();
        } catch (IOException error) { throw error; }
        catch (Exception error) { throw new IOException("图片地址无效", error); }
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) if (name.equalsIgnoreCase(key)) return true;
        return false;
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("图片加载已取消");
    }
}
