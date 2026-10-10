package com.linkvideo.parser;

import java.util.Map;

interface HttpClient {
    DouyinRouteState DEFAULT_DOUYIN_ROUTES = DouyinRouteState.disabled();

    HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception;

    default HttpFetcher.Response get(String url, Map<String, String> headers,
                                     long maxDurationMillis) throws Exception {
        return get(url, headers);
    }

    default HttpFetcher.Response post(String url, Map<String, String> headers, String body) throws Exception {
        throw new UnsupportedOperationException("POST is not supported");
    }

    default HttpFetcher.Response post(String url, Map<String, String> headers, String body,
                                      long maxDurationMillis) throws Exception {
        return post(url, headers, body);
    }

    default boolean supportsBrowser() { return false; }

    default String browserDiagnostics() { return ""; }

    /** Returns the WebView cookie header for this exact origin. Values must never be logged or persisted. */
    default String browserCookies(String origin) { return ""; }

    default void flushBrowserCookies() { }

    default DouyinRouteState douyinRouteState() { return DEFAULT_DOUYIN_ROUTES; }

    default HttpFetcher.Response browserGet(String pageUrl, String requestUrl, Map<String, String> headers) throws Exception {
        throw new UnsupportedOperationException("Browser is not available");
    }

    default HttpFetcher.Response browserGet(String pageUrl, String requestUrl,
                                            Map<String, String> headers,
                                            long maxDurationMillis) throws Exception {
        return browserGet(pageUrl, requestUrl, headers);
    }
}
