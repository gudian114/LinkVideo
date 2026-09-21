package com.linkvideo.parser;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** One parse attempt only. Never records URLs, query strings, bodies or cookie values. */
final class ParseDiagnostics implements HttpClient {
    private final HttpClient delegate;
    private final StringBuilder events = new StringBuilder();
    private final long started = System.currentTimeMillis();

    ParseDiagnostics(HttpClient delegate) { this.delegate = delegate; }

    @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
        return request("GET", url, headers, null, null);
    }

    @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) throws Exception {
        return request("POST", url, headers, body, null);
    }

    @Override public boolean supportsBrowser() { return delegate.supportsBrowser(); }

    @Override public HttpFetcher.Response browserGet(String pageUrl, String requestUrl, Map<String, String> headers) throws Exception {
        return request("网页内核", requestUrl, headers, null, pageUrl);
    }

    private HttpFetcher.Response request(String method, String url, Map<String, String> headers, String body, String pageUrl) throws Exception {
        long start = System.nanoTime();
        events.append(method).append(' ').append(stage(url)).append(" 请求ttwid=")
                .append(hasTtwid(headers.get("Cookie")) ? "有" : "无").append('\n');
        try {
            HttpFetcher.Response response = pageUrl != null ? delegate.browserGet(pageUrl, url, headers)
                    : "POST".equals(method) ? delegate.post(url, headers, body) : delegate.get(url, headers);
            events.append("  HTTP ").append(response.statusCode).append("，耗时=")
                    .append((System.nanoTime() - start) / 1_000_000).append("ms，字符数=").append(response.body.length())
                    .append("，类型=").append(contentType(response.contentType))
                    .append("，编码=").append(encoding(response.contentEncoding))
                    .append("，返回ttwid=").append(hasTtwid(response.cookies) ? "有" : "无").append('\n');
            events.append("  落地：").append(stage(response.finalUrl)).append("；").append(responseShape(response.body)).append('\n');
            if (response.serverTimeMillis > 0) events.append("  本机与服务器时间差约=")
                    .append((System.currentTimeMillis() - response.serverTimeMillis) / 1000).append("秒\n");
            return response;
        } catch (Exception error) {
            events.append("  请求失败，耗时=").append((System.nanoTime() - start) / 1_000_000).append("ms；");
            failure("网络", error);
            throw error;
        } finally {
            if (pageUrl != null) events.append(delegate.browserDiagnostics());
        }
    }

    void failure(String stage, Exception error) {
        events.append(stage).append("：").append(error.getClass().getSimpleName());
        if (error instanceof HttpFetcher.Failure) events.append("，HTTP ").append(((HttpFetcher.Failure) error).statusCode);
        Throwable cause = error.getCause();
        if (cause != null) events.append("，原因类型=").append(cause.getClass().getSimpleName());
        events.append('\n');
    }

    boolean isEmpty() { return events.length() == 0; }

    String report() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "解析诊断 v1\n本机时间：" + format.format(new Date(started)) + "\n" + events;
    }

    private static boolean hasTtwid(String cookies) {
        if (cookies == null) return false;
        for (String part : cookies.split(";")) if (part.trim().startsWith("ttwid=") && part.trim().length() > 6) return true;
        return false;
    }

    private static String stage(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String host = uri.getHost();
            if ("ttwid.bytedance.com".equals(host)) return "匿名注册";
            if (path.equals("/ttwid/union/register/callback/")) return "匿名注册回调";
            if (path.contains("/aweme/detail/")) return url.contains("a_bogus=") ? "签名详情" : "普通详情";
            if (path.contains("/iteminfo/")) return "旧版详情";
            if ("v.douyin.com".equals(host)) return "抖音短链";
            if (path.startsWith("/share/")) return "分享页";
            if (path.startsWith("/video/") || path.startsWith("/note/")) return "作品页";
            if ("www.douyin.com".equals(host) && (path.isEmpty() || "/".equals(path))) return "抖音首页";
            return "平台页面";
        } catch (Exception ignored) { return "地址读取失败"; }
    }

    static String responseShape(String body) {
        String value = body.trim();
        if (value.isEmpty()) return "空响应";
        if (value.startsWith("{")) {
            try {
                JSONObject root = new JSONObject(value);
                Object code = root.opt("status_code");
                String status = code instanceof Number ? "，status_code=" + ((Number) code).longValue() : "";
                JSONObject data = root.optJSONObject("data");
                JSONArray items = root.optJSONArray("item_list");
                if (items == null) items = root.optJSONArray("aweme_list");
                boolean hasItem = root.optJSONObject("aweme_detail") != null
                        || data != null && data.optJSONObject("aweme_detail") != null || items != null && items.length() > 0;
                return "JSON" + status + "，作品数据=" + (hasItem ? "有" : "无");
            } catch (Exception ignored) { return "JSON无法读取"; }
        }
        if (value.startsWith("<")) return "HTML，路由数据=" + (value.contains("_ROUTER_DATA") ? "有" : "无")
                + "，videoInfoRes=" + (value.contains("videoInfoRes") ? "有" : "无");
        return "非JSON/HTML内容";
    }

    private static String contentType(String value) {
        if (value == null || value.isEmpty()) return "未声明";
        String type = value.toLowerCase(Locale.ROOT);
        if (type.contains("json")) return "JSON";
        if (type.contains("html")) return "HTML";
        if (type.contains("text/plain")) return "文本";
        return "其他";
    }

    private static String encoding(String value) {
        if (value == null || value.isEmpty()) return "未声明";
        if ("identity".equalsIgnoreCase(value)) return "identity";
        if ("gzip".equalsIgnoreCase(value)) return "gzip";
        if ("deflate".equalsIgnoreCase(value)) return "deflate";
        if ("br".equalsIgnoreCase(value)) return "br";
        return "其他";
    }
}
