package com.linkvideo.parser;

import java.util.Map;

interface RequestHeaderProvider {
    Map<String, String> forRequest(String url, Map<String, String> base);

    RequestHeaderProvider NONE = (url, base) -> SerializableHeaders.copy(base);
}
