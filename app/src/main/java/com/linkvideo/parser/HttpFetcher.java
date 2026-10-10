package com.linkvideo.parser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
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
        return request("GET", url, headers, null, 0L);
    }

    @Override public Response get(String url, Map<String, String> headers,
                                 long maxDurationMillis) throws Exception {
        return request("GET", url, headers, null, maxDurationMillis);
    }

    @Override public Response post(String url, Map<String, String> headers, String body) throws Exception {
        return request("POST", url, headers, body == null ? "" : body, 0L);
    }

    @Override public Response post(String url, Map<String, String> headers, String body,
                                  long maxDurationMillis) throws Exception {
        return request("POST", url, headers, body == null ? "" : body, maxDurationMillis);
    }

    private Response request(String method, String url, Map<String, String> headers,
                             String body, long maxDurationMillis) throws Exception {
        checkInterrupted();
        long deadline = deadlineNanos(maxDurationMillis);
        String current = url;
        String receivedCookies = "";
        String suppliedCookies = headers.get("Cookie");
        URI cookieOrigin = new URI(current);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            checkInterrupted();
            checkDeadline(deadline);
            URI uri = new URI(current);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || !isAllowedPageHost(uri.getHost())) {
                throw new Exception("页面跳转到了不受信任的地址");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMillis(deadline, 10_000));
            connection.setReadTimeout(timeoutMillis(deadline, 15_000));
            connection.setRequestMethod(method);
            connection.setRequestProperty("Accept", "text/html,application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!"Cookie".equalsIgnoreCase(header.getKey())) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            if (!sameCookieOrigin(cookieOrigin, uri)) {
                suppliedCookies = "";
                receivedCookies = "";
                cookieOrigin = uri;
            }
            String requestCookies = mergeCookies(suppliedCookies, receivedCookies);
            if (!requestCookies.isEmpty()) connection.setRequestProperty("Cookie", requestCookies);
            byte[] requestBody = null;
            if (body != null) {
                requestBody = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(requestBody.length);
            }
            connection.connect();
            checkDeadline(deadline);
            connection.setReadTimeout(timeoutMillis(deadline, 15_000));
            if (requestBody != null) {
                try (OutputStream output = connection.getOutputStream()) {
                    checkInterrupted();
                    checkDeadline(deadline);
                    output.write(requestBody);
                }
            }
            int status = connection.getResponseCode();
            checkInterrupted();
            checkDeadline(deadline);
            receivedCookies = mergeCookies(receivedCookies, responseCookies(connection));
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null) throw new Exception("页面重定向缺少目标地址");
                URI target = uri.resolve(location);
                if (!sameCookieOrigin(uri, target)) {
                    suppliedCookies = "";
                    receivedCookies = "";
                    cookieOrigin = target;
                }
                current = target.toString();
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new Failure(status);
            }
            connection.setReadTimeout(timeoutMillis(deadline, 15_000));
            try (InputStream in = connection.getInputStream()) {
                return new Response(current, readLimited(in, deadline), receivedCookies, status,
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

    static boolean sameCookieOrigin(URI first, URI second) {
        return first != null && second != null && first.getHost() != null && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost());
    }

    private static long deadlineNanos(long maxDurationMillis) {
        if (maxDurationMillis <= 0L) return Long.MAX_VALUE;
        long now = System.nanoTime();
        long delta = maxDurationMillis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : maxDurationMillis * 1_000_000L;
        return now > Long.MAX_VALUE - delta ? Long.MAX_VALUE : now + delta;
    }

    private static void checkDeadline(long deadline) throws SocketTimeoutException {
        if (deadline != Long.MAX_VALUE && System.nanoTime() >= deadline)
            throw new SocketTimeoutException("请求超过本次解析剩余时间");
    }

    private static int timeoutMillis(long deadline, int maximum) throws SocketTimeoutException {
        if (deadline == Long.MAX_VALUE) return maximum;
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) throw new SocketTimeoutException("请求超过本次解析剩余时间");
        long millis = (remaining + 999_999L) / 1_000_000L;
        return (int) Math.max(1L, Math.min((long) maximum, millis));
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

    private static String readLimited(InputStream in, long deadline) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        while (true) {
            checkInterrupted();
            checkDeadline(deadline);
            int count = in.read(buffer);
            checkInterrupted();
            checkDeadline(deadline);
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
