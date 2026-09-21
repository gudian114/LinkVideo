package com.linkvideo.parser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public final class HttpFetcher implements HttpClient {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    public static final class Response {
        public final String finalUrl;
        public final String body;
        public final String cookies;
        public final int statusCode;
        public final String contentType;
        public final String contentEncoding;
        public final long serverTimeMillis;
        public Response(String finalUrl, String body) { this(finalUrl, body, ""); }
        public Response(String finalUrl, String body, String cookies) {
            this(finalUrl, body, cookies, 200, "", "", 0);
        }
        Response(String finalUrl, String body, String cookies, int statusCode,
                String contentType, String contentEncoding, long serverTimeMillis) {
            this.finalUrl = finalUrl;
            this.body = body;
            this.cookies = cookies == null ? "" : cookies;
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.contentEncoding = contentEncoding;
            this.serverTimeMillis = serverTimeMillis;
        }
    }

    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        final int statusCode;
        Failure(int statusCode) {
            super("平台页面请求失败（HTTP " + statusCode + "）");
            this.statusCode = statusCode;
        }
    }

    @Override public Response get(String url, Map<String, String> headers) throws Exception {
        return request("GET", url, headers, null);
    }

    @Override public Response post(String url, Map<String, String> headers, String body) throws Exception {
        return request("POST", url, headers, body == null ? "" : body);
    }

    private Response request(String method, String url, Map<String, String> headers, String body) throws Exception {
        checkInterrupted();
        String current = url;
        String receivedCookies = "";
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            checkInterrupted();
            URI uri = new URI(current);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !isAllowedPageHost(uri.getHost())) {
                throw new Exception("页面跳转到了不受信任的地址");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "text/html,application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!"Cookie".equalsIgnoreCase(header.getKey())) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            String requestCookies = mergeCookies(headers.get("Cookie"), receivedCookies);
            if (!requestCookies.isEmpty()) connection.setRequestProperty("Cookie", requestCookies);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    checkInterrupted();
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            checkInterrupted();
            receivedCookies = mergeCookies(receivedCookies, responseCookies(connection));
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null) throw new Exception("页面重定向缺少目标地址");
                current = uri.resolve(location).toString();
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new Failure(status);
            }
            try (InputStream in = connection.getInputStream()) {
                return new Response(current, readLimited(in), receivedCookies, status,
                        connection.getContentType(), connection.getContentEncoding(), connection.getDate());
            }
            } finally {
                connection.disconnect();
            }
        }
        throw new Exception("页面重定向次数过多");
    }

    static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("解析已取消");
    }

    private static String responseCookies(HttpURLConnection connection) {
        String cookies = "";
        for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
            if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) continue;
            for (String line : entry.getValue()) {
                if (line == null) continue;
                int end = line.indexOf(';');
                cookies = mergeCookies(cookies, end < 0 ? line : line.substring(0, end));
            }
        }
        return cookies;
    }

    static String mergeCookies(String first, String second) {
        Map<String, String> values = new LinkedHashMap<>();
        addCookies(values, first);
        addCookies(values, second);
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (result.length() > 0) result.append("; ");
            result.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return result.toString();
    }

    private static void addCookies(Map<String, String> target, String source) {
        if (source == null || source.isEmpty()) return;
        for (String part : source.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            String name = part.substring(0, equals).trim();
            if (!name.isEmpty()) target.put(name, part.substring(equals + 1).trim());
        }
    }

    private static String readLimited(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        while (true) {
            checkInterrupted();
            int count = in.read(buffer);
            checkInterrupted();
            if (count == -1) break;
            total += count;
            if (total > MAX_BYTES) throw new Exception("平台返回的页面过大，已停止解析");
            out.write(buffer, 0, count);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    static boolean isAllowedPageHost(String host) {
        return LinkInput.isDomain(host, "douyin.com") || LinkInput.isDomain(host, "iesdouyin.com")
                || LinkInput.isDomain(host, "kuaishou.com") || LinkInput.isDomain(host, "gifshow.com")
                || LinkInput.isDomain(host, "chenzhongtech.com")
                || "ttwid.bytedance.com".equalsIgnoreCase(host)
                || LinkInput.isDomain(host, "bilibili.com") || "b23.tv".equalsIgnoreCase(host);
    }

    public static Map<String, String> noHeaders() { return Collections.emptyMap(); }
}
