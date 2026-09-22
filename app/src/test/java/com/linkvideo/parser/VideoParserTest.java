package com.linkvideo.parser;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class VideoParserTest {
    @Test public void addsBilibiliCombinedStreamForPreviewWithoutReplacingDashDownload() throws Exception {
        FakeHttp http = new FakeHttp()
                .reply("/x/web-interface/view?bvid=BV1xx411c7mD", "https://api.bilibili.com/view",
                        "{\"code\":0,\"data\":{\"title\":\"预览\",\"pages\":[{\"cid\":7}]}}")
                .reply("fnval=16", "https://api.bilibili.com/dash",
                        "{\"code\":0,\"data\":{\"dash\":{\"video\":[{\"baseUrl\":\"https://v.example/video.m4s\",\"mimeType\":\"video/mp4\"}],\"audio\":[{\"baseUrl\":\"https://a.example/audio.m4s\",\"mimeType\":\"audio/mp4\"}]}}}")
                .reply("fnval=0", "https://api.bilibili.com/progressive",
                        "{\"code\":0,\"data\":{\"durl\":[{\"url\":\"https://v.example/preview.mp4\"}]}}") ;
        VideoResult result = (VideoResult) new VideoParser(http).parse("BV1xx411c7mD");
        assertEquals("preview", MediaSelection.preview(result).kind);
        assertEquals("https://v.example/preview.mp4", MediaSelection.preview(result).url);
        assertTrue(DownloadPlan.create(result).requiresMux());
    }

    @Test public void retainsDistinctDouyinCoverAlternativesWithStaticBeforeDynamic() throws Exception {
        String body = "window._ROUTER_DATA={\"aweme_id\":\"7312345678901234567\",\"video\":{"
                + "\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]},"
                + "\"cover\":{\"url_list\":[\"\",\"https://image.example/one.jpg\",\"https://backup.example/two.jpg\"]},"
                + "\"origin_cover\":{\"url_list\":[\"https://image.example/one.jpg\",\"https://image.example/origin.jpg\"]},"
                + "\"dynamic_cover\":{\"url_list\":[\"https://image.example/dynamic.webp\"]}}}";
        FakeHttp http = new FakeHttp().reply("v.douyin.com",
                "https://www.douyin.com/video/7312345678901234567", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/current/");
        assertEquals(java.util.Arrays.asList("https://image.example/one.jpg", "https://backup.example/two.jpg",
                "https://image.example/origin.jpg", "https://image.example/dynamic.webp"), result.coverUrls);
        assertEquals("https://image.example/one.jpg", result.coverUrl);
    }

    @Test public void retainsKuaishouBackupCdnAndThumbnail() throws Exception {
        String body = "window.__APOLLO_STATE__={\"VisionVideoDetailPhoto:abc\":{"
                + "\"photoUrl\":\"https://video.example/k.mp4\",\"coverUrl\":\"https://image.example/one.jpg\","
                + "\"coverUrls\":[{\"url\":\"https://image.example/one.jpg\"},{\"url\":\"https://backup.example/two.jpg\"}],"
                + "\"coverThumbnailUrl\":\"https://image.example/thumb.jpg\"}}";
        FakeHttp http = new FakeHttp().reply("v.kuaishou.com", "https://www.kuaishou.com/short-video/abc", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.kuaishou.com/abc");
        assertEquals(java.util.Arrays.asList("https://image.example/one.jpg", "https://backup.example/two.jpg",
                "https://image.example/thumb.jpg"), result.coverUrls);
    }

    @Test public void usesDouyinOriginCoverWhenDefaultCoverIsMissing() throws Exception {
        String body = "window._ROUTER_DATA={\"aweme_id\":\"7312345678901234567\",\"video\":{"
                + "\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]},"
                + "\"origin_cover\":{\"url_list\":[\"https://image.example/origin.jpg\"]}}}";
        FakeHttp http = new FakeHttp().reply("v.douyin.com",
                "https://www.douyin.com/video/7312345678901234567", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/current/");
        assertEquals("https://image.example/origin.jpg", result.coverUrl);
    }

    @Test public void skipsEmptyKuaishouCoverEntryToFindUsableCover() throws Exception {
        String body = "window.INIT_STATE={\"photo\":{\"share_info\":\"photoId=abc\","
                + "\"mainMvUrls\":[{\"url\":\"https://video.example/k.mp4\"}],"
                + "\"coverUrls\":[{\"url\":\"\"},{\"url\":\"https://image.example/k.jpg\"}]}}";
        FakeHttp http = new FakeHttp().reply("v.kuaishou.com",
                "https://www.kuaishou.com/short-video/abc", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.kuaishou.com/abc");
        assertEquals("https://image.example/k.jpg", result.coverUrl);
    }

    @Test public void parsesBilibiliDashAsSeparateVideoAndAudio() throws Exception {
        FakeHttp http = new FakeHttp()
                .reply("/x/web-interface/view?bvid=BV1xx411c7mD", "https://api.bilibili.com/x/web-interface/view", "{\"code\":0,\"data\":{\"title\":\"演示视频\",\"pic\":\"https://i.example/cover.jpg\",\"duration\":61,\"owner\":{\"name\":\"作者\"},\"pages\":[{\"cid\":77,\"part\":\"正片\"}]}}")
                .reply("/x/player/playurl?", "https://api.bilibili.com/x/player/playurl", "{\"code\":0,\"data\":{\"dash\":{\"video\":[{\"baseUrl\":\"https://v.example/video.m4s\",\"mimeType\":\"video/mp4\",\"width\":1920,\"height\":1080,\"bandwidth\":800000}],\"audio\":[{\"baseUrl\":\"https://a.example/audio.m4s\",\"mimeType\":\"audio/mp4\",\"bandwidth\":128000}]}}}");

        VideoResult result = (VideoResult) new VideoParser(http).parse("BV1xx411c7mD");

        assertEquals("哔哩哔哩", result.platform);
        assertEquals("演示视频", result.title);
        assertEquals("作者", result.author);
        assertEquals(2, result.streams.size());
        assertEquals("video", result.streams.get(0).kind);
        assertEquals("audio", result.streams.get(1).kind);
        assertTrue(result.note.contains("封装为 MP4"));
    }

    @Test public void keepsEveryBilibiliDurlSegment() throws Exception {
        FakeHttp http = new FakeHttp()
                .reply("/x/web-interface/view?bvid=BV1xx411c7mD", "https://api.bilibili.com/view", "{\"code\":0,\"data\":{\"title\":\"分段\",\"pages\":[{\"cid\":5}]}}")
                .reply("/x/player/playurl?", "https://api.bilibili.com/play", "{\"code\":0,\"data\":{\"durl\":[{\"url\":\"https://v.example/1.flv\"},{\"url\":\"https://v.example/2.flv\"}]}}");
        VideoResult result = (VideoResult) new VideoParser(http).parse("BV1xx411c7mD");
        assertEquals(2, result.streams.size());
        assertEquals("segment", result.streams.get(0).kind);
        assertEquals("segment", result.streams.get(1).kind);
    }

    @Test public void parsesDouyinRouterData() throws Exception {
        String wrong = "{\"aweme_id\":\"999\",\"desc\":\"推荐视频\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/wrong.mp4\"]}}}";
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"测试抖音\",\"author\":{\"nickname\":\"小明\"},\"video\":{\"duration\":12345,\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]},\"cover\":{\"url_list\":[\"https://image.example/c.jpg\"]}}}";
        String body = "<script>window._ROUTER_DATA = {\"recommendation\":" + wrong + ",\"loaderData\":{\"video_(id)/page\":{\"videoInfoRes\":{\"item_list\":[" + item + "]}}}}</script>";
        FakeHttp http = new FakeHttp()
                .reply("v.douyin.com", "https://www.douyin.com/video/7312345678901234567", "")
                .reply("iesdouyin.com/share/video", "https://www.iesdouyin.com/share/video/7312345678901234567/", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/abc/");
        assertEquals("测试抖音", result.title);
        assertEquals("小明", result.author);
        assertEquals("combined", result.streams.get(0).kind);
        assertEquals(12.345, result.durationSeconds, 0.001);
    }

    @Test public void parsesDouyinDataFromResolvedLandingPageBeforeFallbackRequest() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"落地页视频\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]}}}";
        String body = "<script>window._ROUTER_DATA={\"loaderData\":{\"video_(id)/page\":{\"videoInfoRes\":{\"item_list\":[" + item + "]}}}}</script>";
        FakeHttp http = new FakeHttp()
                .reply("v.douyin.com", "https://www.iesdouyin.com/share/video/7312345678901234567/?share_sign=kept", body);

        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/current/");

        assertEquals("落地页视频", result.title);
        assertEquals("https://video.example/play.mp4", result.streams.get(0).url);
    }

    @Test public void parsesDouyinLegacyImagePostInOriginalOrder() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"图文\","
                + "\"author\":{\"nickname\":\"小明\"},\"images\":["
                + "{\"url_list\":[\"https://img.example/1-small.jpg\",\"https://img.example/1.jpg\"],\"width\":1080,\"height\":1440},"
                + "{\"url_list\":[\"https://img.example/2.jpg\"],\"width\":1440,\"height\":1080}]}";
        FakeHttp http = new FakeHttp().reply("douyin.com",
                "https://www.douyin.com/note/7312345678901234567", "window._ROUTER_DATA=" + item);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://www.douyin.com/note/7312345678901234567");

        assertEquals("图文", result.title);
        assertEquals("小明", result.author);
        assertEquals(2, result.images.size());
        assertEquals(java.util.Arrays.asList("https://img.example/1.jpg", "https://img.example/1-small.jpg"),
                result.images.get(0).urls);
        assertEquals("https://img.example/2.jpg", result.images.get(1).primaryUrl());
        assertEquals(1080, result.images.get(0).width);
        assertEquals(1440, result.images.get(0).height);
    }

    @Test public void parsesDouyinImagePostInfoAndDeduplicatesImages() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"新版图文\","
                + "\"image_post_info\":{\"images\":["
                + "{\"display_image\":{\"url_list\":[\"https://img.example/a-small.jpg\",\"https://img.example/a.jpg\"],\"width\":1000,\"height\":1500}},"
                + "{\"display_image\":{\"url_list\":[\"https://img.example/a.jpg\"]}}]}}";
        FakeHttp http = new FakeHttp().reply("douyin.com",
                "https://www.douyin.com/note/7312345678901234567", "window._ROUTER_DATA=" + item);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://www.douyin.com/note/7312345678901234567");

        assertEquals(1, result.images.size());
        assertEquals("https://img.example/a.jpg", result.images.get(0).primaryUrl());
        assertEquals(1000, result.images.get(0).width);
        assertEquals(1500, result.images.get(0).height);
    }

    @Test public void douyinNoteFallbackUsesSlidesSharePage() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"images\":["
                + "{\"url_list\":[\"https://img.example/note.jpg\"]}]}";
        FakeHttp http = new FakeHttp()
                .reply("v.douyin.com", "https://www.douyin.com/note/7312345678901234567", "")
                .reply("iesdouyin.com/share/slides", "https://www.iesdouyin.com/share/slides/7312345678901234567/",
                        "window._ROUTER_DATA=" + item);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://v.douyin.com/image-note/");

        assertEquals("https://img.example/note.jpg", result.images.get(0).primaryUrl());
    }

    @Test public void normalDouyinVideoStillReturnsVideoResult() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"video\":{"
                + "\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]}}}";
        FakeHttp http = new FakeHttp().reply("douyin.com",
                "https://www.douyin.com/video/7312345678901234567", "window._ROUTER_DATA=" + item);

        ParsedResult result = new VideoParser(http)
                .parse("https://www.douyin.com/video/7312345678901234567");

        assertTrue(result instanceof VideoResult);
        assertEquals(ParsedResult.Kind.VIDEO, result.kind());
    }

    @Test public void parsesDouyinWithCookiesFromSharePageApi() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"接口视频\","
                + "\"author\":{\"nickname\":\"小音\"},\"video\":{\"duration\":8000,"
                + "\"play_addr\":{\"url_list\":[\"https://video.example/api.mp4\"]}}}";
        final boolean[] apiCalled = {false};
        HttpClient http = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("v.douyin.com")) {
                    return new HttpFetcher.Response("https://www.douyin.com", "<script>{\"aweme_id\": \"7312345678901234567\"}</script>", "passport=x");
                }
                if (url.contains("iesdouyin.com/share/video")) {
                    return new HttpFetcher.Response(url, "<html></html>", "msToken=share-token");
                }
                if (url.contains("web/api/v2/aweme/iteminfo")) return new HttpFetcher.Response(url, "{}");
                if (url.contains("aweme/v1/web/aweme/detail")) {
                    assertTrue(headers.get("Cookie").contains("passport=x"));
                    assertTrue(headers.get("Cookie").contains("msToken=share-token"));
                    apiCalled[0] = true;
                    return new HttpFetcher.Response(url, "{\"status_code\":0,\"aweme_detail\":" + item + "}");
                }
                throw new Exception("unexpected URL: " + url);
            }
        };

        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/current/");

        assertTrue(apiCalled[0]);
        assertEquals("接口视频", result.title);
        assertEquals("小音", result.author);
        assertEquals("https://video.example/api.mp4", result.streams.get(0).url);
    }

    @Test public void obtainsTtwidWhenDouyinPageHasNoUsableCookie() throws Exception {
        String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"ttwid视频\","
                + "\"video\":{\"play_addr_h264\":{\"url_list\":[\"https://video.example/ttwid.mp4\"]}}}";
        final boolean[] registered = {false};
        HttpClient http = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("v.douyin.com")) {
                    return new HttpFetcher.Response("https://www.douyin.com/video/7312345678901234567", "");
                }
                if (url.contains("iesdouyin.com/share/video")) return new HttpFetcher.Response(url, "<html></html>");
                if (url.contains("aweme/v1/web/aweme/detail")) {
                    assertEquals("ttwid=registered-token", headers.get("Cookie"));
                    return new HttpFetcher.Response(url, "{\"status_code\":0,\"aweme_detail\":" + item + "}");
                }
                throw new Exception("unexpected URL: " + url);
            }

            @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) throws Exception {
                assertTrue(url.contains("ttwid.bytedance.com/ttwid/union/register"));
                assertTrue(body.contains("\"aid\":1128"));
                registered[0] = true;
                return new HttpFetcher.Response(url, "{}", "ttwid=registered-token");
            }
        };

        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/current/");

        assertTrue(registered[0]);
        assertEquals("ttwid视频", result.title);
        assertEquals("https://video.example/ttwid.mp4", result.streams.get(0).url);
    }

    @Test public void parsesKuaishouApolloState() throws Exception {
        String body = "<script>window.__APOLLO_STATE__={\"VisionVideoDetailPhoto:recommend\":{\"__typename\":\"VisionVideoDetailPhoto\",\"caption\":\"推荐视频\",\"photoUrl\":\"https://video.example/wrong.mp4\"},\"VisionVideoDetailPhoto:abc\":{\"__typename\":\"VisionVideoDetailPhoto\",\"caption\":\"快手示例\",\"userName\":\"阿快\",\"photoUrl\":\"https://video.example/k.mp4\",\"coverUrl\":\"https://image.example/k.jpg\",\"duration\":9}}</script>";
        FakeHttp http = new FakeHttp().reply("kuaishou.com", "https://www.kuaishou.com/short-video/abc", body);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.kuaishou.com/a1b2c3");
        assertEquals("快手示例", result.title);
        assertEquals("阿快", result.author);
        assertEquals("https://video.example/k.mp4", result.streams.get(0).url);
    }

    @Test public void parsesCurrentKuaishouInitStateFromTrustedLandingPage() throws Exception {
        String body = "<script>window.INIT_STATE={\"page\":{\"photo\":{"
                + "\"photoId\":\"5190117303615323672\","
                + "\"share_info\":\"userId=u1&photoId=3xbdkcqfvqjaf7u\","
                + "\"caption\":\"新版快手页面\",\"userName\":\"阿快\",\"duration\":191867,"
                + "\"mainMvUrls\":[{\"url\":\"https://video.example/current.mp4?clientCacheKey=3xbdkcqfvqjaf7u_b.mp4\"}],"
                + "\"coverUrls\":[{\"url\":\"https://image.example/current.jpg\"}]}}}}</script>";
        FakeHttp http = new FakeHttp().reply("v.kuaishou.com",
                "https://abc.m.chenzhongtech.com/fw/long-video/3xbdkcqfvqjaf7u?photoId=3xbdkcqfvqjaf7u", body);

        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.kuaishou.com/nHcZxDYa");

        assertEquals("新版快手页面", result.title);
        assertEquals("https://video.example/current.mp4?clientCacheKey=3xbdkcqfvqjaf7u_b.mp4", result.streams.get(0).url);
        assertEquals("https://image.example/current.jpg", result.coverUrl);
        assertEquals(191.867, result.durationSeconds, 0.001);
    }

    @Test public void parsesKuaishouAtlasCdnPathsAndSizes() throws Exception {
        String body = "window.INIT_STATE={\"page\":{\"photo\":{\"photoId\":\"abc\","
                + "\"caption\":\"快手图集\",\"userName\":\"阿快\",\"ext_params\":{\"atlas\":{"
                + "\"cdnList\":[{\"cdn\":\"https://ali.example\"},{\"cdn\":\"https://backup.example/\"}],"
                + "\"list\":[\"p/1.webp\",\"/p/2.webp\"],"
                + "\"size\":[{\"w\":1080,\"h\":1440},{\"w\":1440,\"h\":1080}]}}}}}";
        FakeHttp http = new FakeHttp().reply("kuaishou.com",
                "https://www.kuaishou.com/short-video/abc", body);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://www.kuaishou.com/short-video/abc");

        assertEquals("快手图集", result.title);
        assertEquals("阿快", result.author);
        assertEquals(2, result.images.size());
        assertEquals(java.util.Arrays.asList("https://ali.example/p/1.webp", "https://backup.example/p/1.webp"),
                result.images.get(0).urls);
        assertEquals(1080, result.images.get(0).width);
        assertEquals(1440, result.images.get(0).height);
    }

    @Test public void keepsKuaishouFullHttpsAndSchemeRelativeAtlasUrls() throws Exception {
        String body = "window.INIT_STATE={\"page\":{\"photo\":{\"id\":\"abc\",\"caption\":\"完整地址\"},"
                + "\"atlas\":{\"list\":[\"https://img.example/one.jpg\",\"//img.example/two.jpg\"]}}}";
        FakeHttp http = new FakeHttp().reply("kuaishou.com",
                "https://www.kuaishou.com/short-video/abc", body);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://www.kuaishou.com/short-video/abc");

        assertEquals("https://img.example/one.jpg", result.images.get(0).primaryUrl());
        assertEquals("https://img.example/two.jpg", result.images.get(1).primaryUrl());
    }

    @Test public void parsesKuaishouFwPhotoLandingRoute() throws Exception {
        String body = "window.INIT_STATE={\"page\":{\"photo\":{\"id\":\"abc\",\"ext_params\":{"
                + "\"atlas\":{\"cdn\":[\"https://img.example\"],\"list\":[\"one.jpg\"]}}}}}";
        FakeHttp http = new FakeHttp().reply("v.kuaishou.com",
                "https://v.m.chenzhongtech.com/fw/photo/abc", body);

        ImagePostResult result = (ImagePostResult) new VideoParser(http)
                .parse("https://v.kuaishou.com/atlas/");

        assertEquals("https://img.example/one.jpg", result.images.get(0).primaryUrl());
    }

    @Test public void rejectsKuaishouAtlasForDifferentPhoto() throws Exception {
        String body = "window.INIT_STATE={\"page\":{\"photo\":{\"photoId\":\"other\"},"
                + "\"atlas\":{\"cdn\":[\"https://img.example\"],\"list\":[\"wrong.jpg\"]}}}";
        FakeHttp http = new FakeHttp().reply("kuaishou.com",
                "https://www.kuaishou.com/short-video/abc", body);

        assertThrows(Exception.class, () -> new VideoParser(http)
                .parse("https://www.kuaishou.com/short-video/abc"));
    }

    @Test public void rejectsDouyinPageWithoutRequestedAweme() throws Exception {
        String wrong = "{\"aweme_id\":\"999\",\"desc\":\"推荐视频\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/wrong.mp4\"]}}}";
        String body = "<script>window._ROUTER_DATA={\"recommendation\":" + wrong + "}</script>";
        FakeHttp http = new FakeHttp()
                .reply("v.douyin.com", "https://www.douyin.com/video/7312345678901234567", "")
                .reply("iesdouyin.com/share/video", "https://www.iesdouyin.com/share/video/7312345678901234567/", body);
        assertThrows(Exception.class, () -> new VideoParser(http).parse("https://v.douyin.com/abc/"));
    }

    @Test public void explainsDouyinShortLinkThatNowRedirectsToHome() throws Exception {
        FakeHttp http = new FakeHttp().reply("v.douyin.com", "https://www.douyin.com", "<html></html>");
        Exception error = assertThrows(Exception.class,
                () -> new VideoParser(http).parse("https://v.douyin.com/expired/"));
        assertTrue(error.getMessage().contains("跳转到首页"));
    }

    @Test public void rejectsKuaishouPageWithoutRequestedPhoto() throws Exception {
        String body = "<script>window.__APOLLO_STATE__={\"VisionVideoDetailPhoto:other\":{\"__typename\":\"VisionVideoDetailPhoto\",\"caption\":\"推荐视频\",\"photoUrl\":\"https://video.example/wrong.mp4\"}}</script>";
        FakeHttp http = new FakeHttp().reply("kuaishou.com", "https://www.kuaishou.com/short-video/requested", body);
        assertThrows(Exception.class, () -> new VideoParser(http).parse("https://v.kuaishou.com/a1b2c3"));
    }

    @Test public void doesNotReportMetadataOnlyAsSuccess() throws Exception {
        FakeHttp http = new FakeHttp().reply("kuaishou.com", "https://www.kuaishou.com/short-video/abc", "<html>验证后继续</html>");
        Exception error = assertThrows(Exception.class, () -> new VideoParser(http).parse("https://v.kuaishou.com/a1b2c3"));
        assertTrue(error.getMessage().contains("媒体地址"));
    }

    @Test public void parsesNoteWithSignedDetailWhenSharePageHasNoImages() throws Exception {
        final boolean[] signedCalled = {false};
        final String id = "7685366931792203054";
        HttpClient http = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("v.douyin.com")) return new HttpFetcher.Response(
                        "https://www.iesdouyin.com/share/note/" + id + "/", "window._ROUTER_DATA={\"loaderData\":{\"note_(id)/page\":{\"itemId\":\"" + id + "\"}}}");
                if (url.contains("share/slides")) return new HttpFetcher.Response(url, "<html></html>");
                if (url.contains("aid=6383")) {
                    signedCalled[0] = true;
                    assertTrue(url.contains("device_platform=webapp"));
                    assertTrue(url.contains("aweme_id=" + id));
                    assertTrue(url.contains("&a_bogus="));
                    assertEquals("ttwid=anonymous-token", headers.get("Cookie"));
                    assertTrue(headers.get("User-Agent").contains("Chrome/90.0.4430.212"));
                    return new HttpFetcher.Response(url, "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + id + "\",\"desc\":\"大学生竞赛\",\"images\":[{\"url_list\":[\"https://img.example/college.jpg\"],\"width\":1080,\"height\":1440}]}}");
                }
                return new HttpFetcher.Response(url, "{\"status_code\":0}");
            }
            @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) {
                return new HttpFetcher.Response(url, "{}", "ttwid=anonymous-token");
            }
        };
        ImagePostResult result = (ImagePostResult) new VideoParser(http).parse("https://v.douyin.com/9frk6FTp2Dk/");
        assertTrue(signedCalled[0]);
        assertEquals("大学生竞赛", result.title);
        assertEquals("https://img.example/college.jpg", result.images.get(0).primaryUrl());
    }

    @Test public void signedDetailRejectsUnrelatedWork() {
        final boolean[] signedCalled = {false};
        final String id = "7685366931792203054";
        HttpClient http = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) {
                if (url.contains("v.douyin.com")) return new HttpFetcher.Response(
                        "https://www.iesdouyin.com/share/note/" + id + "/", "<html></html>");
                if (url.contains("aid=6383")) {
                    signedCalled[0] = true;
                    return new HttpFetcher.Response(url,
                            "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"999\",\"images\":[{\"url_list\":[\"https://img.example/wrong.jpg\"]}]}}");
                }
                return new HttpFetcher.Response(url, "{}");
            }
            @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) {
                return new HttpFetcher.Response(url, "{}", "ttwid=anonymous-token");
            }
        };
        assertThrows(Exception.class, () -> new VideoParser(http).parse("https://v.douyin.com/9frk6FTp2Dk/"));
        assertTrue(signedCalled[0]);
    }

    @Test public void reportsProgressWhileReadingBilibiliAndCompletesOnlyAfterResult() throws Exception {
        List<Integer> progress = new ArrayList<>();
        FakeHttp replies = new FakeHttp()
                .reply("/x/web-interface/view?", "https://api.bilibili.com/view",
                        "{\"code\":0,\"data\":{\"title\":\"进度视频\",\"pages\":[{\"cid\":7}]}}")
                .reply("/x/player/playurl?", "https://api.bilibili.com/play",
                        "{\"code\":0,\"data\":{\"format\":\"mp4\",\"durl\":[{\"url\":\"https://v.example/video.mp4\"}]}}") ;
        HttpClient http = (url, headers) -> {
            assertFalse(progress.isEmpty());
            assertTrue(progress.get(progress.size() - 1) > 0);
            assertTrue(progress.get(progress.size() - 1) < 100);
            return replies.get(url, headers);
        };

        VideoResult result = (VideoResult) new VideoParser(http).parse("BV1xx411c7mD", progress::add);

        assertEquals("进度视频", result.title);
        assertCompletedProgress(progress);
    }

    @Test public void reportsProgressForDouyinImagePost() throws Exception {
        String body = "window._ROUTER_DATA={\"aweme_id\":\"7312345678901234567\","
                + "\"desc\":\"图文进度\",\"images\":[{\"url_list\":[\"https://img.example/one.jpg\"]}]}";
        FakeHttp http = new FakeHttp().reply("v.douyin.com",
                "https://www.douyin.com/note/7312345678901234567", body);
        List<Integer> progress = new ArrayList<>();

        ImagePostResult result = (ImagePostResult) new VideoParser(http).parse(
                "https://v.douyin.com/progress/", progress::add);

        assertEquals("图文进度", result.title);
        assertEquals(1, result.images.size());
        assertCompletedProgress(progress);
    }

    @Test public void reportsProgressForKuaishouVideo() throws Exception {
        FakeHttp http = new FakeHttp().reply("v.kuaishou.com", "https://www.kuaishou.com/short-video/abc",
                "window.__APOLLO_STATE__={\"VisionVideoDetailPhoto:abc\":{\"photoUrl\":\"https://v.example/k.mp4\"}}");
        List<Integer> progress = new ArrayList<>();

        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.kuaishou.com/progress", progress::add);

        assertEquals("https://v.example/k.mp4", result.streams.get(0).url);
        assertCompletedProgress(progress);
    }

    @Test public void failedParseNeverReportsCompletedProgress() {
        FakeHttp http = new FakeHttp().reply("kuaishou.com", "https://www.kuaishou.com/short-video/abc",
                "<html>需要验证</html>");
        List<Integer> progress = new ArrayList<>();

        assertThrows(Exception.class, () -> new VideoParser(http).parse("https://v.kuaishou.com/fail", progress::add));

        assertFalse(progress.isEmpty());
        assertFalse(progress.contains(100));
    }

    private static void assertCompletedProgress(List<Integer> progress) {
        assertTrue(progress.size() >= 4);
        assertEquals(Integer.valueOf(0), progress.get(0));
        assertEquals(Integer.valueOf(100), progress.get(progress.size() - 1));
        int previous = 0;
        for (int percent : progress) {
            assertTrue(percent >= previous && percent <= 100);
            previous = percent;
        }
    }

    private static final class FakeHttp implements HttpClient {
        private final List<String[]> replies = new ArrayList<>();
        FakeHttp reply(String contains, String finalUrl, String body) { replies.add(new String[]{contains, finalUrl, body}); return this; }
        @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
            for (String[] r : replies) if (url.contains(r[0])) return new HttpFetcher.Response(r[1], r[2]);
            throw new Exception("unexpected URL: " + url);
        }
    }
}
