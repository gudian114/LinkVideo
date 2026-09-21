package com.linkvideo.parser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoParser {
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";
    private static final String MOBILE_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148";
    private static final Pattern BVID = Pattern.compile("(?i)BV[0-9A-Za-z]{10}");
    private static final Pattern AID = Pattern.compile("(?i)(?:av)([0-9]+)");
    private static final Pattern DOUYIN_ID = Pattern.compile("/(?:video|note|share/(?:video|slides|note))/(\\d+)");
    private static final Pattern DOUYIN_BODY_ID = Pattern.compile("(?:aweme_id|awemeId|itemId)[\\\"'=:\\s]+(\\d{15,20})|/(?:video|note)/(\\d{15,20})");
    private static final Pattern KUAISHOU_ID = Pattern.compile("/(?:short-video|photo|fw/long-video)/([^/?#]+)");
    private final HttpClient http;

    public VideoParser() { this(new HttpFetcher()); }
    VideoParser(HttpClient http) { this.http = http; }

    public ParsedResult parse(String text) throws Exception {
        return parse(text, percent -> { });
    }

    public ParsedResult parse(String text, ProgressListener progress) throws Exception {
        ParseDiagnostics diagnostics = new ParseDiagnostics(http);
        try {
            return new VideoParser(diagnostics).parseInternal(text, progress);
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (InterruptedException cancelled) { throw cancellation(cancelled); }
        catch (Exception error) {
            if (diagnostics.isEmpty()) throw error;
            diagnostics.failure("解析结束", error);
            throw new ParseFailure(error, diagnostics.report());
        }
    }

    private ParsedResult parseInternal(String text, ProgressListener progress) throws Exception {
        ProgressListener checked = percent -> {
            HttpFetcher.checkInterrupted();
            progress.onProgress(percent);
        };
        checked.onProgress(0);
        LinkInput input = LinkInput.parse(text);
        checked.onProgress(10);
        ParsedResult result;
        if ("哔哩哔哩".equals(input.platform)) result = parseBilibili(input.url, checked);
        else if ("抖音".equals(input.platform)) result = parseDouyin(input.url, checked);
        else if ("快手".equals(input.platform)) result = parseKuaishou(input.url, checked);
        else throw new Exception("暂不支持该平台");
        checked.onProgress(100);
        return result;
    }

    private VideoResult parseBilibili(String sourceUrl, ProgressListener progress) throws Exception {
        String resolved = sourceUrl;
        if (new URI(sourceUrl).getHost().equalsIgnoreCase("b23.tv")) {
            resolved = http.get(sourceUrl, headers("User-Agent", DESKTOP_UA)).finalUrl;
        }
        progress.onProgress(25);
        Matcher bv = BVID.matcher(resolved);
        Matcher av = AID.matcher(resolved);
        String idQuery;
        if (bv.find()) idQuery = "bvid=" + bv.group();
        else if (av.find()) idQuery = "aid=" + av.group(1);
        else throw new Exception("没有从哔哩哔哩链接中找到 BV 或 av 编号");

        String viewUrl = "https://api.bilibili.com/x/web-interface/view?" + idQuery;
        JSONObject view = requireData(http.get(viewUrl, headers("User-Agent", DESKTOP_UA)).body, "视频信息");
        progress.onProgress(50);
        int part = queryInt(resolved, "p", 1);
        JSONArray pages = view.optJSONArray("pages");
        long cid = view.optLong("cid", 0);
        String partTitle = "";
        if (pages != null && pages.length() > 0) {
            if (part < 1 || part > pages.length()) throw new Exception("链接指定的分P不存在");
            JSONObject page = pages.optJSONObject(part - 1);
            cid = page == null ? 0 : page.optLong("cid", 0);
            partTitle = page == null ? "" : page.optString("part", "");
        }
        if (cid == 0) throw new Exception("平台没有返回视频分P标识");
        String playUrl = "https://api.bilibili.com/x/player/playurl?" + idQuery + "&cid=" + cid + "&qn=32&fnval=16&fourk=0";
        JSONObject play = requireData(http.get(playUrl, headers("User-Agent", DESKTOP_UA, "Referer", resolved)).body, "播放地址");
        progress.onProgress(75);
        Map<String, String> mediaHeaders = headers("Referer", resolved, "User-Agent", DESKTOP_UA);
        List<MediaStream> streams = new ArrayList<>();
        JSONObject dash = play.optJSONObject("dash");
        String note = "";
        if (dash != null) {
            appendDash(streams, dash.optJSONArray("video"), "video", mediaHeaders);
            appendDash(streams, dash.optJSONArray("audio"), "audio", mediaHeaders);
            if (!streams.isEmpty()) note = "平台返回分离的画面和音频；点击下载后会在手机本地封装为 MP4。";
        }
        JSONArray durl = play.optJSONArray("durl");
        if (streams.isEmpty() && durl != null) {
            String kind = durl.length() > 1 ? "segment" : "combined";
            String format = play.optString("format", "").toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < durl.length(); i++) {
                JSONObject segment = durl.optJSONObject(i);
                String url = segment == null ? "" : https(segment.optString("url", ""));
                String mime = format.contains("mp4") || hasMp4Path(url) ? "video/mp4" : "application/octet-stream";
                if (!url.isEmpty()) streams.add(new MediaStream(url, durl.length() > 1 ? "片段 " + (i + 1) : "视频", kind, mime, mediaHeaders));
            }
            if (durl.length() > 1) note = "平台返回多个视频片段；下载阶段需要按顺序合并。";
        }
        if (dash != null && !streams.isEmpty()) {
            progress.onProgress(85);
            try {
                String previewUrl = "https://api.bilibili.com/x/player/playurl?" + idQuery + "&cid=" + cid
                        + "&qn=16&fnval=0&fourk=0&platform=html5";
                JSONObject preview = requireData(http.get(previewUrl,
                        headers("User-Agent", DESKTOP_UA, "Referer", resolved)).body, "预览地址");
                JSONArray previewUrls = preview.optJSONArray("durl");
                JSONObject first = previewUrls == null || previewUrls.length() != 1
                        ? null : previewUrls.optJSONObject(0);
                String url = first == null ? "" : https(first.optString("url", ""));
                if (!url.isEmpty()) streams.add(new MediaStream(url, "带声音预览", "preview",
                        "video/mp4", mediaHeaders));
            } catch (CancellationException cancelled) { throw cancelled; }
            catch (InterruptedException cancelled) { throw cancellation(cancelled); }
            catch (Exception ignored) { }
        }
        requireStreams(streams, "哔哩哔哩");
        progress.onProgress(90);
        JSONObject owner = view.optJSONObject("owner");
        String title = view.optString("title", "未命名视频");
        if (pages != null && pages.length() > 1 && !partTitle.isEmpty()) title += " · " + partTitle;
        return new VideoResult("哔哩哔哩", title, owner == null ? "" : owner.optString("name", ""),
                https(view.optString("pic", "")), resolved, note, view.optDouble("duration", 0), streams);
    }

    private ParsedResult parseDouyin(String sourceUrl, ProgressListener progress) throws Exception {
        HttpFetcher.Response initial = http.get(sourceUrl, pageHeaders(MOBILE_UA));
        progress.onProgress(30);
        String resolved = initial.finalUrl;
        String videoId = douyinId(resolved, initial.body);
        if (videoId == null || !videoId.matches("\\d+")) {
            throw new Exception("抖音短链已跳转到首页，链接可能失效或平台要求验证；请从原视频重新复制分享链接");
        }
        JSONObject item = findDouyinInPage(initial.body, videoId);
        String shareUrl = resolved;
        String cookies = initial.cookies;
        String mediaUserAgent = MOBILE_UA;
        if (item == null) {
            progress.onProgress(45);
            shareUrl = douyinShareUrl(resolved, videoId);
            HttpFetcher.Response share = http.get(shareUrl, pageHeaders(MOBILE_UA, "Referer", resolved));
            cookies = HttpFetcher.mergeCookies(cookies, share.cookies);
            item = findDouyinInPage(share.body, videoId);
            progress.onProgress(55);
        }
        if (item == null && !cookies.isEmpty()) {
            progress.onProgress(65);
            item = fetchDouyinApi(videoId, shareUrl, cookies);
        }
        if (item == null) {
            progress.onProgress(70);
            String session = fetchDouyinSession();
            if (!session.isEmpty()) {
                cookies = HttpFetcher.mergeCookies(cookies, session);
                progress.onProgress(75);
                item = fetchDouyinDetail(videoId, cookies);
            }
            if (item == null) {
                progress.onProgress(85);
                item = fetchDouyinSignedDetail(videoId, cookies);
            }
        }
        if (item == null && http.supportsBrowser()) {
            progress.onProgress(88);
            String pageUrl = "https://www.douyin.com/" + (shareUrl.contains("/slides/") ? "note/" : "video/") + videoId;
            try {
                HttpFetcher.Response browser = http.browserGet(pageUrl,
                        "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + videoId + "&aid=1128&app_name=aweme",
                        headers("User-Agent", DESKTOP_UA));
                if (browser.statusCode >= 400) throw new HttpFetcher.Failure(browser.statusCode);
                JSONObject data = new JSONObject(browser.body);
                if (data.optInt("status_code", -1) == 0) item = findDouyinItem(data, videoId);
                if (item != null) {
                    cookies = HttpFetcher.mergeCookies(cookies, browser.cookies);
                    mediaUserAgent = DESKTOP_UA;
                    shareUrl = pageUrl;
                }
            } catch (CancellationException cancelled) { throw cancelled; }
            catch (InterruptedException cancelled) { throw cancellation(cancelled); }
            catch (Exception error) { recordFailure("网页内核读取", error); throw error; }
        }
        if (item == null) throw new Exception("抖音页面未返回公开作品数据，可能需要登录或验证");
        progress.onProgress(90);
        Map<String, String> mediaHeaders = cookies.isEmpty()
                ? headers("Referer", shareUrl, "User-Agent", mediaUserAgent)
                : headers("Referer", shareUrl, "User-Agent", mediaUserAgent, "Cookie", cookies);
        JSONObject author = item.optJSONObject("author");
        List<ImageResource> images = douyinImages(item, mediaHeaders);
        if (!images.isEmpty()) {
            return new ImagePostResult("抖音", item.optString("desc", "未命名图文"),
                    author == null ? "" : author.optString("nickname", ""), resolved,
                    "公开分享页图文解析结果；若图片地址过期，请重新解析。", images);
        }

        JSONObject video = item.optJSONObject("video");
        List<MediaStream> streams = new ArrayList<>();
        String media = firstUrl(video == null ? null : video.optJSONObject("play_addr_h264"));
        if (media.isEmpty()) media = firstUrl(video == null ? null : video.optJSONObject("play_addr"));
        media = watermarkFree(media);
        if (!media.isEmpty()) streams.add(new MediaStream(media, "视频", "combined", "video/mp4", mediaHeaders));
        requireStreams(streams, "抖音");
        Set<String> covers = new LinkedHashSet<>();
        if (video != null) {
            for (String field : new String[]{"cover", "origin_cover", "raw_cover", "dynamic_cover"}) {
                JSONObject holder = video.optJSONObject(field);
                if (holder != null) appendCoverUrls(covers, holder.optJSONArray("url_list"));
            }
        }
        return new VideoResult("抖音", item.optString("desc", "未命名视频"), author == null ? "" : author.optString("nickname", ""),
                new ArrayList<>(covers), resolved,
                "公开分享页解析结果；若媒体地址过期，请重新解析。", video == null ? 0 : video.optDouble("duration", 0) / 1000.0, streams);
    }

    private JSONObject fetchDouyinApi(String videoId, String shareUrl, String cookies) {
        try {
            String legacy = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=" + videoId;
            JSONObject item = findDouyinApiItem(http.get(legacy, headers("User-Agent", MOBILE_UA,
                    "Accept", "application/json, text/plain, */*", "Referer", shareUrl,
                    "X-Requested-With", "XMLHttpRequest", "Cookie", cookies)).body);
            if (item != null) return item;
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (InterruptedException cancelled) { throw cancellation(cancelled); }
        catch (Exception error) { recordFailure("旧版详情处理", error); }
        return fetchDouyinDetail(videoId, cookies);
    }

    private JSONObject fetchDouyinDetail(String videoId, String cookies) {
        try {
            String url = "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + videoId
                    + "&aid=1128&app_name=aweme";
            String body = http.get(url, headers("User-Agent", MOBILE_UA,
                    "Accept", "application/json, text/plain, */*", "Referer", "https://www.douyin.com/video/" + videoId,
                    "X-Requested-With", "XMLHttpRequest", "Cookie", cookies)).body;
            return findDouyinApiItem(body);
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (InterruptedException cancelled) { throw cancellation(cancelled); }
        catch (Exception error) { recordFailure("普通详情处理", error); return null; }
    }

    private JSONObject fetchDouyinSignedDetail(String videoId, String cookies) {
        try {
            String query = "aid=6383&device_platform=webapp&aweme_id=" + videoId;
            String url = "https://www.douyin.com/aweme/v1/web/aweme/detail/?" + query
                    + "&a_bogus=" + URLEncoder.encode(DouyinRequestSignature.sign(query), "UTF-8");
            String body = http.get(url, headers("User-Agent", DouyinRequestSignature.USER_AGENT,
                    "Accept", "application/json, text/plain, */*", "Referer", "https://www.douyin.com/",
                    "Accept-Language", "zh-CN,zh;q=0.9", "Cookie", cookies)).body;
            JSONObject response = new JSONObject(body);
            return response.optInt("status_code", -1) == 0 ? findDouyinItem(response, videoId) : null;
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (InterruptedException cancelled) { throw cancellation(cancelled); }
        catch (Exception error) { recordFailure("签名详情生成或读取", error); return null; }
    }

    private String fetchDouyinSession() {
        try {
            String body = "{\"region\":\"cn\",\"aid\":1128,\"needFid\":false,"
                    + "\"service\":\"www.douyin.com\",\"migrate_source\":0,\"cbUrlProtocol\":\"https\","
                    + "\"app_name\":\"aweme_web\",\"device_platform\":\"web\"}";
            HttpFetcher.Response response = http.post("https://ttwid.bytedance.com/ttwid/union/register/",
                    headers("User-Agent", DESKTOP_UA, "Content-Type", "application/json", "Accept", "*/*",
                            "Origin", "https://www.douyin.com", "Referer", "https://www.douyin.com/"), body);
            String cookies = response.cookies;
            try {
                String callback = new JSONObject(response.body).optString("redirect_url", "");
                URI target = new URI(callback);
                if ("https".equalsIgnoreCase(target.getScheme()) && target.getUserInfo() == null
                        && "www.douyin.com".equalsIgnoreCase(target.getHost())
                        && (target.getPort() == -1 || target.getPort() == 443)
                        && "/ttwid/union/register/callback/".equals(target.getPath())) {
                    HttpFetcher.Response completed = http.get(callback,
                            headers("User-Agent", DESKTOP_UA, "Referer", "https://www.douyin.com/", "Cookie", cookies));
                    cookies = HttpFetcher.mergeCookies(cookies, completed.cookies);
                }
            } catch (CancellationException cancelled) { throw cancelled; }
            catch (InterruptedException cancelled) { throw cancellation(cancelled); }
            catch (Exception error) { recordFailure("匿名回调处理", error); }
            return cookies;
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (InterruptedException cancelled) { throw cancellation(cancelled); }
        catch (Exception error) { recordFailure("匿名注册处理", error); }
        return "";
    }

    private void recordFailure(String stage, Exception error) {
        if (http instanceof ParseDiagnostics) ((ParseDiagnostics) http).failure(stage, error);
    }

    private static CancellationException cancellation(InterruptedException cause) {
        Thread.currentThread().interrupt();
        CancellationException cancelled = new CancellationException("解析已取消");
        cancelled.initCause(cause);
        return cancelled;
    }

    private static JSONObject findDouyinApiItem(String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject item = root.optJSONObject("aweme_detail");
            if (item != null) return item;
            JSONObject data = root.optJSONObject("data");
            if (data != null && data.optJSONObject("aweme_detail") != null) return data.optJSONObject("aweme_detail");
            JSONArray items = root.optJSONArray("item_list");
            if (items == null) items = root.optJSONArray("aweme_list");
            return items == null ? null : items.optJSONObject(0);
        } catch (Exception ignored) { return null; }
    }

    private static String douyinId(String url, String body) {
        Matcher direct = DOUYIN_ID.matcher(url == null ? "" : url);
        if (direct.find()) return direct.group(1);
        String query = firstNonEmpty(queryValue(url, "modal_id"), queryValue(url, "aweme_id"));
        if (query != null && query.matches("\\d{15,20}")) return query;
        Matcher embedded = DOUYIN_BODY_ID.matcher(body == null ? "" : body);
        if (!embedded.find()) return null;
        return firstNonEmpty(embedded.group(1), embedded.group(2));
    }

    private static String douyinShareUrl(String resolved, String videoId) {
        String path = "";
        try { path = new URI(resolved).getPath(); } catch (Exception ignored) { }
        String type = path != null && (path.contains("/note/") || path.contains("/slides/")
                || path.contains("/share/note/")) ? "slides" : "video";
        return "https://www.iesdouyin.com/share/" + type + "/" + videoId + "/";
    }

    private ParsedResult parseKuaishou(String sourceUrl, ProgressListener progress) throws Exception {
        HttpFetcher.Response page = http.get(sourceUrl, pageHeaders(MOBILE_UA));
        progress.onProgress(30);
        Matcher id = KUAISHOU_ID.matcher(page.finalUrl);
        String photoId = id.find() ? id.group(1) : queryValue(page.finalUrl, "photoId");
        if (photoId == null || photoId.isEmpty()) throw new Exception("没有从快手链接中找到作品编号");
        JSONObject root;
        boolean initState = false;
        try {
            root = assignmentJson(page.body, "window.__APOLLO_STATE__");
        } catch (Exception e) {
            try {
                root = assignmentJson(page.body, "window.INIT_STATE");
                initState = true;
            } catch (Exception ignored) {
                throw new Exception("快手页面没有返回媒体地址，可能需要网页验证");
            }
        }
        JSONObject photo = initState ? findKuaishouInitPhoto(root, photoId) : findVisionPhoto(root, photoId);
        if (photo == null) throw new Exception("快手页面没有返回媒体地址，可能需要网页验证");
        progress.onProgress(60);
        Map<String, String> mediaHeaders = headers("Referer", page.finalUrl, "User-Agent", MOBILE_UA);
        String author = firstNonEmpty(photo.optString("userName", ""), photo.optString("user_name", ""));
        List<ImageResource> images = kuaishouImages(photo, root, photoId, mediaHeaders);
        progress.onProgress(90);
        if (!images.isEmpty()) {
            return new ImagePostResult("快手", firstNonEmpty(photo.optString("caption", ""), "未命名图文"),
                    author, page.finalUrl, "公开分享页图文解析结果；若图片地址过期，请重新解析。", images);
        }
        List<MediaStream> streams = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addMedia(streams, seen, photo.optString("photoUrl", ""), "H.264 视频", "combined", mediaHeaders);
        addMedia(streams, seen, photo.optString("photoH265Url", ""), "H.265 视频", "combined", mediaHeaders);
        appendKuaishouResource(streams, seen, photo.opt("videoResource"), mediaHeaders);
        appendUrlArray(streams, seen, photo.optJSONArray("mainMvUrls"), "H.264 视频", mediaHeaders);
        requireStreams(streams, "快手");
        Set<String> covers = new LinkedHashSet<>();
        addCoverUrl(covers, photo.optString("coverUrl", ""));
        appendCoverUrls(covers, photo.optJSONArray("coverUrls"));
        addCoverUrl(covers, photo.optString("coverThumbnailUrl", ""));
        appendCoverUrls(covers, photo.optJSONArray("webpCoverUrls"));
        double duration = photo.optDouble("duration", 0);
        if (initState && duration > 10_000) duration /= 1000.0;
        return new VideoResult("快手", firstNonEmpty(photo.optString("caption", ""), "未命名视频"), author,
                new ArrayList<>(covers), page.finalUrl, "公开页面解析结果；若媒体地址过期，请重新解析。",
                duration, streams);
    }

    private static JSONObject requireData(String body, String label) throws Exception {
        JSONObject response = new JSONObject(body);
        if (response.optInt("code", -1) != 0 || response.optJSONObject("data") == null) {
            throw new Exception("哔哩哔哩未返回" + label + "：" + response.optString("message", "未知错误"));
        }
        return response.getJSONObject("data");
    }

    private static void appendDash(List<MediaStream> out, JSONArray formats, String kind, Map<String, String> headers) {
        if (formats == null) return;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (format == null) continue;
            String url = https(firstNonEmpty(format.optString("baseUrl", ""), format.optString("base_url", "")));
            if (url.isEmpty()) continue;
            String label = kind.equals("video") ? format.optInt("width") + "×" + format.optInt("height") + " 画面" : "音频 " + format.optInt("bandwidth") / 1000 + " kbps";
            out.add(new MediaStream(url, label, kind, format.optString("mimeType", ""),
                    format.optString("codecs", ""), headers));
        }
    }

    private static JSONObject findDouyinInPage(String html, String videoId) {
        try { return findDouyinItem(assignmentJson(html, "window._ROUTER_DATA"), videoId); }
        catch (Exception ignored) { return null; }
    }

    private static JSONObject assignmentJson(String html, String marker) throws Exception {
        int markerAt = html.indexOf(marker);
        if (markerAt < 0) throw new Exception("页面中没有找到解析数据");
        int start = html.indexOf('{', markerAt + marker.length());
        if (start < 0) throw new Exception("页面解析数据不完整");
        return new JSONObject(extractBalancedObject(html, start));
    }

    static String extractBalancedObject(String text, int start) throws Exception {
        int depth = 0;
        boolean string = false, escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (string) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') string = false;
            } else if (c == '"') string = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return text.substring(start, i + 1);
        }
        throw new Exception("页面解析数据没有完整结束");
    }

    private static JSONObject findDouyinItem(Object value, String videoId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject video = object.optJSONObject("video");
            String objectId = firstNonEmpty(object.optString("aweme_id", ""), object.optString("awemeId", ""));
            JSONArray images = object.optJSONArray("images");
            JSONObject imagePost = object.optJSONObject("image_post_info");
            JSONArray postImages = imagePost == null ? null : imagePost.optJSONArray("images");
            if (videoId.equals(objectId) && ((video != null && (video.optJSONObject("play_addr") != null
                    || video.optJSONObject("play_addr_h264") != null))
                    || (images != null && images.length() > 0)
                    || (postImages != null && postImages.length() > 0))) return object;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findDouyinItem(object.opt(keys.next()), videoId);
                if (found != null) return found;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findDouyinItem(array.opt(i), videoId);
                if (found != null) return found;
            }
        }
        return null;
    }

    static List<ImageResource> douyinImages(JSONObject item, Map<String, String> headers) {
        Map<String, ImageResource> unique = new LinkedHashMap<>();
        JSONArray legacy = item == null ? null : item.optJSONArray("images");
        appendDouyinImages(unique, legacy, false, headers);
        JSONObject post = item == null ? null : item.optJSONObject("image_post_info");
        appendDouyinImages(unique, post == null ? null : post.optJSONArray("images"), true, headers);
        return new ArrayList<>(unique.values());
    }

    private static void appendDouyinImages(Map<String, ImageResource> out, JSONArray images,
                                            boolean nested, Map<String, String> headers) {
        if (images == null) return;
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            JSONObject holder = nested && image != null ? image.optJSONObject("display_image") : image;
            if (holder == null) continue;
            JSONArray rawUrls = holder.optJSONArray("url_list");
            List<String> urls = new ArrayList<>();
            if (rawUrls != null) {
                for (int j = rawUrls.length() - 1; j >= 0; j--) {
                    String url = https(rawUrls.optString(j, ""));
                    if (!url.isEmpty() && !urls.contains(url)) urls.add(url);
                }
            }
            if (urls.isEmpty()) continue;
            ImageResource resource = new ImageResource(urls, headers,
                    holder.optInt("width", 0), holder.optInt("height", 0));
            out.putIfAbsent(resource.primaryUrl(), resource);
        }
    }

    private static JSONObject findVisionPhoto(Object value, String photoId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.opt(key);
                if (("VisionVideoDetailPhoto:" + photoId).equals(key) && child instanceof JSONObject) {
                    return (JSONObject) child;
                }
                JSONObject found = findVisionPhoto(child, photoId);
                if (found != null) return found;
            }
            String objectId = firstNonEmpty(object.optString("photoId", ""), object.optString("id", ""));
            if (photoId.equals(objectId) && "VisionVideoDetailPhoto".equals(object.optString("__typename"))) return object;
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findVisionPhoto(array.opt(i), photoId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JSONObject findKuaishouInitPhoto(Object value, String shortPhotoId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (matchesKuaishouPhoto(object, shortPhotoId)) return object;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findKuaishouInitPhoto(object.opt(keys.next()), shortPhotoId);
                if (found != null) return found;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findKuaishouInitPhoto(array.opt(i), shortPhotoId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean matchesKuaishouPhoto(JSONObject object, String photoId) {
        if (object == null) return false;
        String objectId = firstNonEmpty(object.optString("photoId", ""), object.optString("id", ""));
        if (photoId.equals(objectId)) return true;
        String shareInfo = object.optString("share_info", "");
        return photoId.equals(queryValue("https://local.invalid/?" + shareInfo, "photoId"));
    }

    static List<ImageResource> kuaishouImages(JSONObject photo, JSONObject root, String photoId,
                                               Map<String, String> headers) {
        List<JSONObject> atlases = new ArrayList<>();
        JSONObject ext = objectValue(photo == null ? null : photo.opt("ext_params"));
        addObject(atlases, objectValue(ext == null ? null : ext.opt("atlas")));
        addObject(atlases, objectValue(photo == null ? null : photo.opt("atlas")));
        addObject(atlases, findKuaishouParentAtlas(root, photoId));
        for (JSONObject atlas : atlases) {
            List<ImageResource> resources = kuaishouAtlasResources(atlas, headers);
            if (!resources.isEmpty()) return resources;
        }
        return Collections.emptyList();
    }

    private static void addObject(List<JSONObject> values, JSONObject object) {
        if (object != null && !values.contains(object)) values.add(object);
    }

    private static JSONObject objectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) {
            try { return new JSONObject((String) value); } catch (Exception ignored) { }
        }
        return null;
    }

    private static JSONObject findKuaishouParentAtlas(Object value, String photoId) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject nestedPhoto = object.optJSONObject("photo");
            JSONObject atlas = objectValue(object.opt("atlas"));
            if (atlas != null && matchesKuaishouPhoto(nestedPhoto, photoId)) return atlas;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject found = findKuaishouParentAtlas(object.opt(keys.next()), photoId);
                if (found != null) return found;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findKuaishouParentAtlas(array.opt(i), photoId);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<ImageResource> kuaishouAtlasResources(JSONObject atlas, Map<String, String> headers) {
        JSONArray paths = atlas == null ? null : atlas.optJSONArray("list");
        if (paths == null || paths.length() == 0) return Collections.emptyList();
        List<String> cdns = kuaishouCdns(atlas);
        JSONArray sizes = atlas.optJSONArray("size");
        Map<String, ImageResource> unique = new LinkedHashMap<>();
        for (int i = 0; i < paths.length(); i++) {
            Object raw = paths.opt(i);
            String path = raw instanceof JSONObject
                    ? firstNonEmpty(((JSONObject) raw).optString("url", ""), ((JSONObject) raw).optString("path", ""))
                    : String.valueOf(raw == null ? "" : raw);
            List<String> urls = new ArrayList<>();
            if (path.startsWith("https://") || path.startsWith("//")) {
                String full = kuaishouImageUrl("", path);
                if (!full.isEmpty()) urls.add(full);
            } else {
                for (String cdn : cdns) {
                    String full = kuaishouImageUrl(cdn, path);
                    if (!full.isEmpty() && !urls.contains(full)) urls.add(full);
                }
            }
            if (urls.isEmpty()) continue;
            JSONObject size = sizes != null && sizes.length() == paths.length() ? sizes.optJSONObject(i) : null;
            ImageResource resource = new ImageResource(urls, headers,
                    size == null ? 0 : size.optInt("w", 0), size == null ? 0 : size.optInt("h", 0));
            unique.putIfAbsent(resource.primaryUrl(), resource);
        }
        return new ArrayList<>(unique.values());
    }

    private static List<String> kuaishouCdns(JSONObject atlas) {
        List<String> result = new ArrayList<>();
        Object direct = atlas.opt("cdn");
        if (direct instanceof String) addString(result, (String) direct);
        else if (direct instanceof JSONArray) appendCdnValues(result, (JSONArray) direct);
        appendCdnValues(result, atlas.optJSONArray("cdnList"));
        return result;
    }

    private static void appendCdnValues(List<String> out, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            if (value instanceof String) addString(out, (String) value);
            else if (value instanceof JSONObject) addString(out, ((JSONObject) value).optString("cdn", ""));
        }
    }

    private static void addString(List<String> out, String value) {
        if (value != null && !value.isEmpty() && !out.contains(value)) out.add(value);
    }

    static String kuaishouImageUrl(String cdn, String path) {
        if (path == null || path.isEmpty()) return "";
        String cleanPath = path.replace("\\u002F", "/").replace("\\u002f", "/");
        if (cleanPath.startsWith("//")) return https("https:" + cleanPath);
        if (cleanPath.startsWith("https://")) return https(cleanPath);
        if (cdn == null || cdn.isEmpty()) return "";
        String base = cdn.replace("\\u002F", "/").replace("\\u002f", "/");
        if (base.startsWith("//")) base = "https:" + base;
        else if (!base.startsWith("https://")) base = "https://" + base;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        while (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
        return https(base + "/" + cleanPath);
    }

    private static void appendUrlArray(List<MediaStream> out, Set<String> seen, JSONArray values,
                                       String label, Map<String, String> headers) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value != null) addMedia(out, seen, value.optString("url", ""), label, "combined", headers);
        }
    }

    private static void appendCoverUrls(Set<String> out, JSONArray values) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            if (value instanceof String) addCoverUrl(out, (String) value);
            else if (value instanceof JSONObject) addCoverUrl(out, ((JSONObject) value).optString("url", ""));
        }
    }

    private static void addCoverUrl(Set<String> out, String value) {
        String url = https(value);
        if (!url.isEmpty()) out.add(url);
    }

    private static void appendKuaishouResource(List<MediaStream> out, Set<String> seen, Object raw, Map<String, String> headers) {
        if (raw instanceof String) {
            try { raw = new JSONObject((String) raw); } catch (Exception ignored) { return; }
        }
        collectKuaishouUrls(out, seen, raw, headers);
    }

    private static void collectKuaishouUrls(List<MediaStream> out, Set<String> seen, Object value, Map<String, String> headers) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String url = object.optString("url", "");
            if (!url.isEmpty()) addMedia(out, seen, url, firstNonEmpty(object.optString("qualityLabel", ""), "视频流"), "combined", headers);
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectKuaishouUrls(out, seen, object.opt(keys.next()), headers);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectKuaishouUrls(out, seen, array.opt(i), headers);
        }
    }

    private static void addMedia(List<MediaStream> out, Set<String> seen, String raw, String label, String kind, Map<String, String> headers) {
        String url = https(raw);
        if (!url.isEmpty() && seen.add(url)) out.add(new MediaStream(url, label, kind, "video/mp4", headers));
    }

    private static String firstUrl(JSONObject holder) {
        if (holder == null) return "";
        JSONArray urls = holder.optJSONArray("url_list");
        return urls == null || urls.length() == 0 ? "" : https(urls.optString(0, ""));
    }

    private static String watermarkFree(String url) {
        return url == null ? "" : url.replace("/playwm/", "/play/");
    }

    private static String https(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            URI uri = new URI(value.replace("\\u0026", "&"));
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? uri.toString() : "";
        } catch (Exception ignored) { return ""; }
    }

    private static boolean hasMp4Path(String value) {
        try {
            String path = new URI(value).getPath();
            return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
        } catch (Exception ignored) { return false; }
    }

    private static void requireStreams(List<MediaStream> streams, String platform) throws Exception {
        if (streams.isEmpty()) throw new Exception(platform + "页面没有返回媒体地址，可能需要登录、网页验证或该作品并非视频");
    }

    private static int queryInt(String url, String key, int fallback) {
        try { return Integer.parseInt(firstNonEmpty(queryValue(url, key), String.valueOf(fallback))); }
        catch (Exception ignored) { return fallback; }
    }

    private static String queryValue(String url, String key) {
        try {
            String query = new URI(url).getRawQuery();
            if (query == null) return null;
            for (String item : query.split("&")) {
                String[] pair = item.split("=", 2);
                if (pair[0].equals(key)) return pair.length > 1 ? pair[1] : "";
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static String firstNonEmpty(String first, String second) { return first == null || first.isEmpty() ? second : first; }

    private static Map<String, String> headers(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) result.put(pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> pageHeaders(String userAgent, String... pairs) {
        String[] all = new String[pairs.length + 6];
        all[0] = "User-Agent";
        all[1] = userAgent;
        all[2] = "Accept";
        all[3] = "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8";
        all[4] = "Accept-Language";
        all[5] = "zh-CN,zh;q=0.9";
        System.arraycopy(pairs, 0, all, 6, pairs.length);
        return headers(all);
    }
}
