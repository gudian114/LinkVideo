package com.linkvideo.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VideoResult implements ParsedResult {
    private static final long serialVersionUID = 1L;

    public final String platform;
    public final String title;
    public final String author;
    public final String coverUrl;
    public final List<String> coverUrls;
    public final String sourceUrl;
    public final String note;
    public final double durationSeconds;
    public final List<MediaStream> streams;

    public VideoResult(String platform, String title, String author, String coverUrl, String sourceUrl,
                       String note, double durationSeconds, List<MediaStream> streams) {
        this(platform, title, author, coverUrl == null || coverUrl.isEmpty()
                ? Collections.emptyList() : Collections.singletonList(coverUrl), sourceUrl,
                note, durationSeconds, streams);
    }

    public VideoResult(String platform, String title, String author, List<String> coverUrls, String sourceUrl,
                       String note, double durationSeconds, List<MediaStream> streams) {
        this.platform = platform;
        this.title = title;
        this.author = author;
        this.coverUrls = Collections.unmodifiableList(new ArrayList<>(coverUrls));
        this.coverUrl = this.coverUrls.isEmpty() ? "" : this.coverUrls.get(0);
        this.sourceUrl = sourceUrl;
        this.note = note;
        this.durationSeconds = durationSeconds;
        this.streams = Collections.unmodifiableList(streams);
    }

    @Override public Kind kind() { return Kind.VIDEO; }
    @Override public String platform() { return platform; }
    @Override public String title() { return title; }
    @Override public String author() { return author; }
    @Override public String sourceUrl() { return sourceUrl; }
}
