package com.linkvideo.parser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImagePostResult implements ParsedResult, Serializable {
    private static final long serialVersionUID = 1L;

    public final String platform;
    public final String title;
    public final String author;
    public final String sourceUrl;
    public final String note;
    public final List<ImageResource> images;

    public ImagePostResult(String platform, String title, String author, String sourceUrl,
                           String note, List<ImageResource> images) {
        if (images == null || images.isEmpty()) throw new IllegalArgumentException("图文结果没有图片");
        this.platform = value(platform);
        this.title = value(title);
        this.author = value(author);
        this.sourceUrl = value(sourceUrl);
        this.note = value(note);
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
    }

    @Override public Kind kind() { return Kind.IMAGE_POST; }
    @Override public String platform() { return platform; }
    @Override public String title() { return title; }
    @Override public String author() { return author; }
    @Override public String sourceUrl() { return sourceUrl; }

    private static String value(String value) { return value == null ? "" : value; }
}
