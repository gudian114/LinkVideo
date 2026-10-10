package com.linkvideo.parser;

import org.junit.Test;
import java.util.Map;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class DouyinBrowserFallbackTest {
    private static final String ID = "7685159514829025465";
    private static final String PAGE = "https://www.douyin.com/video/" + ID;

    @Test public void allNativeDetails403CanRecoverThroughOfficialBrowserPage() throws Exception {
        Fake client = new Fake();
        VideoResult result = (VideoResult) new VideoParser(client).parse(PAGE);
        assertEquals(1, client.browserCalls);
        assertEquals("浏览器视频", result.title);
        assertEquals("https://video.example/public.mp4", result.streams.get(0).url);
        assertEquals(PAGE, result.streams.get(0).headers.get("Referer"));
        assertFalse(result.streams.get(0).headers.containsKey("Cookie"));
        assertTrue(client.browserBudget > 0 && client.browserBudget <= 90_000L);
    }

    @Test public void browserDoesNotSubstituteADifferentRecommendedVideo() throws Exception {
        Fake client = new Fake();
        client.itemId = "7685159514829025466";
        try { new VideoParser(client).parse(PAGE); fail(); }
        catch (ParseFailure expected) {
            assertEquals(1, client.browserCalls);
            assertTrue(expected.details, expected.details.contains("网页内核"));
        }
    }

    @Test public void browserFailureRemainsDiagnosableWithoutRetryLoop() throws Exception {
        Fake client = new Fake();
        client.browserFailure = new HttpFetcher.Failure(403);
        try { new VideoParser(client).parse(PAGE); fail(); }
        catch (ParseFailure expected) {
            assertEquals(1, client.browserCalls);
            assertTrue(expected.details, expected.details.contains("网页内核"));
            assertTrue(expected.details, expected.details.contains("HTTP 403"));
        }
    }

    @Test public void cancellationDuringBrowserLoadStopsTheAttempt() throws Exception {
        Fake client = new Fake();
        client.browserFailure = new CancellationException();
        try { new VideoParser(client).parse(PAGE); fail(); }
        catch (CancellationException expected) { assertEquals(1, client.browserCalls); }
    }

    @Test public void verifiedWebViewIdentityCanResolveNativelyWithoutLoadingAWorkPage() throws Exception {
        HttpClient client = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("/detail/")) {
                    assertEquals("ttwid=verified-browser", headers.get("Cookie"));
                    assertTrue(headers.get("User-Agent").contains("Chrome/124.0"));
                    return new HttpFetcher.Response(url, "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + ID
                            + "\",\"desc\":\"免网页视频\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/native.mp4\"]}}}}");
                }
                if (url.contains("iteminfo"))
                    throw new AssertionError("Legacy must be skipped without same-origin share cookies");
                return new HttpFetcher.Response(PAGE, "<html></html>");
            }
            @Override public String browserCookies(String origin) { return "ttwid=verified-browser"; }
            @Override public boolean supportsBrowser() { return true; }
            @Override public HttpFetcher.Response browserGet(String page, String request, Map<String, String> headers) {
                throw new AssertionError("A valid browser identity should avoid loading the work page");
            }
        };

        VideoResult result = (VideoResult) new VideoParser(client).parse(PAGE);

        assertEquals("免网页视频", result.title);
    }

    @Test public void exactCanonicalShareLandingPageIsNotFetchedTwice() throws Exception {
        final int[] shareGets = {0};
        String share = "https://www.iesdouyin.com/share/video/" + ID + "/";
        HttpClient client = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) {
                if (url.equals(share)) shareGets[0]++;
                return new HttpFetcher.Response(share, "<html></html>");
            }
            @Override public boolean supportsBrowser() { return true; }
            @Override public HttpFetcher.Response browserGet(String page, String request, Map<String, String> headers) {
                return new HttpFetcher.Response(page, "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + ID
                        + "\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/browser.mp4\"]}}}}", "");
            }
        };

        new VideoParser(client).parse(share);

        assertEquals(1, shareGets[0]);
    }

    @Test public void legacyShareCookiesAreNotSentToWwwDetail() throws Exception {
        HttpClient client = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("iteminfo")) {
                    assertTrue(headers.getOrDefault("Cookie", "").contains("legacy-only"));
                    assertFalse(headers.getOrDefault("Cookie", "").contains("browser-identity"));
                    return new HttpFetcher.Response(url, "{\"status_code\":11110}");
                }
                if (url.contains("/detail/")) {
                    String cookie = headers.getOrDefault("Cookie", "");
                    assertTrue(cookie.contains("browser-identity"));
                    assertFalse(cookie.contains("legacy-only"));
                    return new HttpFetcher.Response(url, "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + ID
                            + "\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/scoped.mp4\"]}}}}", "");
                }
                if (url.contains("www.iesdouyin.com/share/"))
                    return new HttpFetcher.Response(url, "<html></html>", "legacy-only=canary");
                return new HttpFetcher.Response(PAGE, "<html></html>");
            }
            @Override public String browserCookies(String origin) { return "ttwid=browser-identity"; }
        };

        VideoResult result = (VideoResult) new VideoParser(client).parse(PAGE);

        assertEquals("https://video.example/scoped.mp4", result.streams.get(0).url);
    }

    private static final class Fake implements HttpClient {
        int browserCalls;
        long browserBudget;
        String itemId = ID;
        Exception browserFailure;
        @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
            if (url.contains("/detail/")) throw new HttpFetcher.Failure(403);
            if (url.contains("iteminfo")) return new HttpFetcher.Response(url, "{\"status_code\":11110}");
            return new HttpFetcher.Response(PAGE, "<html>window._ROUTER_DATA={}</html>", "ttwid=share");
        }
        @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) {
            return new HttpFetcher.Response(url, "{\"status_code\":0}", "ttwid=registered");
        }
        @Override public boolean supportsBrowser() { return true; }
        @Override public HttpFetcher.Response browserGet(String pageUrl, String requestUrl, Map<String, String> headers) throws Exception {
            browserCalls++;
            assertEquals(PAGE, pageUrl);
            assertTrue(requestUrl.startsWith("https://www.douyin.com/aweme/v1/web/aweme/detail/?"));
            assertTrue(requestUrl.contains("aweme_id=" + ID));
            if (browserFailure != null) throw browserFailure;
            return new HttpFetcher.Response(pageUrl, "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + itemId
                    + "\",\"desc\":\"浏览器视频\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/public.mp4\"]}}}}", "ttwid=browser");
        }
        @Override public HttpFetcher.Response browserGet(String pageUrl, String requestUrl,
                Map<String, String> headers, long maxDurationMillis) throws Exception {
            browserBudget = maxDurationMillis;
            return browserGet(pageUrl, requestUrl, headers);
        }
    }
}
