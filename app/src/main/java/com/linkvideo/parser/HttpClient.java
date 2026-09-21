package com.linkvideo.parser;

import java.util.Map;

interface HttpClient {
    HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception;

    default HttpFetcher.Response post(String url, Map<String, String> headers, String body) throws Exception {
        throw new UnsupportedOperationException("POST is not supported");
    }

    default boolean supportsBrowser() { return false; }

    default String browserDiagnostics() { return ""; }

    default HttpFetcher.Response browserGet(String pageUrl, String requestUrl, Map<String, String> headers) throws Exception {
        throw new UnsupportedOperationException("Browser is not available");
    }
}
