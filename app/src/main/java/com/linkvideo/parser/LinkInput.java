package com.linkvideo.parser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkInput {
    private static final Pattern URL = Pattern.compile("https://[A-Za-z0-9._~:/?#@!$&()*+,;=%\\-\\[\\]]+");
    private static final Pattern BVID = Pattern.compile("(?i)(?<![A-Za-z0-9])BV[0-9A-Za-z]{10}(?![A-Za-z0-9])");
    private static final String TRAILING = ".,;:!?)]}，。；：！？）》】」』、";

    public final String url;
    public final String platform;
    private LinkInput(String url, String platform) { this.url = url; this.platform = platform; }

    public static LinkInput parse(String text) throws Exception {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("请粘贴视频分享链接");
        List<LinkInput> matches = new ArrayList<>();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            LinkInput supported = fromUrl(candidate);
            if (supported != null) matches.add(supported);
        }
        if (matches.isEmpty()) {
            Matcher bv = BVID.matcher(text);
            if (bv.find()) return new LinkInput("https://www.bilibili.com/video/" + bv.group(), "哔哩哔哩");
            throw new IllegalArgumentException("没有找到支持的抖音、快手或哔哩哔哩链接");
        }
        if (matches.size() > 1) throw new IllegalArgumentException("检测到多个支持的视频链接，请一次只粘贴一个");
        return matches.get(0);
    }

    private static LinkInput fromUrl(String value) throws Exception {
        URI uri = new URI(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (isDomain(host, "douyin.com") || isDomain(host, "iesdouyin.com")) return new LinkInput(value, "抖音");
        if (isDomain(host, "kuaishou.com") || isDomain(host, "gifshow.com")) return new LinkInput(value, "快手");
        if (isDomain(host, "bilibili.com") || host.equals("b23.tv")) return new LinkInput(value, "哔哩哔哩");
        return null;
    }

    static boolean isDomain(String host, String root) {
        if (host == null) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals(root) || normalized.endsWith("." + root);
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && TRAILING.indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }
}
