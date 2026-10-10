package com.linkvideo.parser;

import android.webkit.CookieManager;
import java.util.Map;

final class DouyinRuntimeHeaders {
    private DouyinRuntimeHeaders() { }

    static Map<String, String> forRequest(String url, Map<String, String> base) {
        return DouyinHeaderPolicy.forRequest(url, base, requestUrl -> {
            try {
                String value = CookieManager.getInstance().getCookie(requestUrl);
                return value == null ? "" : value;
            } catch (RuntimeException ignored) { return ""; }
        });
    }
}
