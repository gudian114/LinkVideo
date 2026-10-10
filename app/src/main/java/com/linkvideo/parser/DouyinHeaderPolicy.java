package com.linkvideo.parser;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Adds an in-memory WebView cookie only to the exact trusted request origin. */
final class DouyinHeaderPolicy {
    private DouyinHeaderPolicy() { }

    static Map<String, String> forRequest(String url, Map<String, String> base,
                                          Function<String, String> cookieLookup) {
        Map<String, String> result = new LinkedHashMap<>(SerializableHeaders.copy(base));
        if (trusted(url) && cookieLookup != null) {
            String cookie;
            try { cookie = cookieLookup.apply(url); }
            catch (RuntimeException ignored) { cookie = ""; }
            if (cookie != null && !cookie.trim().isEmpty()) result.put("Cookie", cookie);
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean trusted(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                    && ("douyin.com".equalsIgnoreCase(host)
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".douyin.com"));
        } catch (Exception ignored) { return false; }
    }
}
