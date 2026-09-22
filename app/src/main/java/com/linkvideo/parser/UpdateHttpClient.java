package com.linkvideo.parser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateHttpClient {
    static final String MANIFEST_URL =
            "https://github.com/gudian114/LinkVideo/releases/latest/download/latest.json";
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;
    private static final long MAX_APK_BYTES = 1024L * 1024L * 1024L;

    String fetchManifest(CancellationToken token) throws IOException {
        HttpURLConnection connection = open(new URL(MANIFEST_URL), token);
        try {
            return readUtf8(connection.getInputStream(), MAX_MANIFEST_BYTES, token);
        } finally {
            connection.disconnect();
        }
    }

    long downloadApk(UpdateManifest manifest, File partial, CancellationToken token,
            DownloadTransfer.ProgressListener listener) throws IOException {
        HttpURLConnection connection = open(new URL(manifest.apkUrl()), token);
        try {
            long serverSize = connection.getContentLengthLong();
            if (manifest.sizeBytes() > 0 && serverSize > 0 && serverSize != manifest.sizeBytes()) {
                throw new IOException("服务器返回的安装包大小与更新清单不一致");
            }
            long expected = manifest.sizeBytes() > 0 ? manifest.sizeBytes() : Math.max(0, serverSize);
            return DownloadTransfer.copy(connection.getInputStream(), partial, expected,
                    MAX_APK_BYTES, token, listener);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(URL initial, CancellationToken token) throws IOException {
        URL current = requireHttps(initial);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            token.throwIfCanceled();
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("User-Agent", "LinkVideo/0.25.0");
            connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*");
            int status = connection.getResponseCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (redirect == MAX_REDIRECTS) throw new IOException("更新地址重定向次数过多");
                current = resolveRedirect(current, location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IOException("更新服务器返回 HTTP " + status);
            }
            return connection;
        }
        throw new IOException("更新地址重定向次数过多");
    }

    static URL requireHttps(URL url) throws IOException {
        if (url == null || !"https".equalsIgnoreCase(url.getProtocol())
                || url.getHost() == null || url.getHost().trim().isEmpty()) {
            throw new IOException("更新地址必须使用 HTTPS");
        }
        return url;
    }

    static URL resolveRedirect(URL current, String location) throws IOException {
        if (location == null || location.trim().isEmpty()) throw new IOException("更新地址重定向无效");
        return requireHttps(new URL(current, location.trim()));
    }

    static String readUtf8(InputStream source, int maximumBytes, CancellationToken token)
            throws IOException {
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                token.throwIfCanceled();
                if (count == 0) continue;
                total += count;
                if (total > maximumBytes) throw new IOException("更新清单过大");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
