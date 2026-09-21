package com.linkvideo.parser;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ImageResource implements Serializable {
    private static final long serialVersionUID = 1L;

    public final List<String> urls;
    public final Map<String, String> headers;
    public final int width;
    public final int height;

    public ImageResource(List<String> urls, Map<String, String> headers, int width, int height) {
        Set<String> safeUrls = new LinkedHashSet<>();
        if (urls != null) {
            for (String value : urls) {
                if (isHttps(value)) safeUrls.add(value);
            }
        }
        if (safeUrls.isEmpty()) throw new IllegalArgumentException("图片没有可用的 HTTPS 地址");
        this.urls = Collections.unmodifiableList(new ArrayList<>(safeUrls));
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(
                headers == null ? Collections.emptyMap() : headers));
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public String primaryUrl() { return urls.get(0); }

    private static boolean isHttps(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (Exception ignored) { return false; }
    }
}
