package com.linkvideo.parser;

import java.io.Serializable;
import java.util.Map;

public final class MediaStream implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String url;
    public final String label;
    public final String kind;
    public final String mimeType;
    public final String codecs;
    public final Map<String, String> headers;

    public MediaStream(String url, String label, String kind, String mimeType, Map<String, String> headers) {
        this(url, label, kind, mimeType, "", headers);
    }

    public MediaStream(String url, String label, String kind, String mimeType, String codecs,
                       Map<String, String> headers) {
        this.url = url;
        this.label = label;
        this.kind = kind;
        this.mimeType = mimeType;
        this.codecs = codecs == null ? "" : codecs;
        this.headers = SerializableHeaders.copy(headers);
    }
}
