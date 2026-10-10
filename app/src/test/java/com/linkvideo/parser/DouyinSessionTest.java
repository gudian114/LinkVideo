package com.linkvideo.parser;

import org.junit.Test;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class DouyinSessionTest {
    private static final String ID = "7685743076014777457";
    private static final String CALLBACK = "https://www.douyin.com/ttwid/union/register/callback/?aid=1128&ticket=public";
    private static final String ITEM = "{\"status_code\":0,\"aweme_detail\":{\"aweme_id\":\"" + ID
            + "\",\"desc\":\"保持强者的生活方式\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/sample.mp4\"]}}}}";

    @Test public void registrationCallbackCanSupplyTheMissingAnonymousCookie() throws Exception {
        HttpClient http = session("", "", "ttwid=callback-token", null, CALLBACK);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("保持强者的生活方式", result.title);
        assertEquals("https://video.example/sample.mp4", result.streams.get(0).url);
    }

    @Test public void callbackSessionKeepsAllAnonymousCookiesForDetailRequests() throws Exception {
        HttpClient http = session("", "", "ttwid=callback-token; msToken=callback-session", null, CALLBACK);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("保持强者的生活方式", result.title);
    }

    @Test public void existingBrowserSessionCanStillUseSignedDetail() throws Exception {
        HttpClient http = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) {
                if (url.contains("a_bogus=") && "ttwid=existing".equals(headers.get("Cookie"))) {
                    return new HttpFetcher.Response(url, ITEM);
                }
                if (url.contains("/aweme/detail/") || url.contains("/web/api/")) return new HttpFetcher.Response(url, "{}");
                return new HttpFetcher.Response("https://www.iesdouyin.com/share/video/" + ID + "/", "<html></html>");
            }
            @Override public String browserCookies(String origin) { return "ttwid=existing"; }
        };
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("保持强者的生活方式", result.title);
    }

    @Test public void anonymousRegistrationDoesNotSendCrossOriginShareCookies() throws Exception {
        HttpClient http = session("__ac_nonce=share", "ttwid=registered", "", null, CALLBACK);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("保持强者的生活方式", result.title);
    }

    @Test public void callbackNetworkFailureKeepsTheRegistrationCookie() throws Exception {
        HttpClient http = session("", "ttwid=registered", "", new IOException("callback offline"), CALLBACK);
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("https://video.example/sample.mp4", result.streams.get(0).url);
    }

    @Test public void callbackCancellationStopsInsteadOfContinuingDetailRequests() {
        HttpClient http = session("", "ttwid=registered", "", new CancellationException("cancel"), CALLBACK);
        assertThrows(CancellationException.class, () -> new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/"));
    }

    @Test public void registrationCannotSendCookiesToAnUnrelatedCallback() throws Exception {
        HttpClient http = session("", "ttwid=registered", "", null, "https://unrelated.example/ttwid/union/register/callback/");
        VideoResult result = (VideoResult) new VideoParser(http).parse("https://v.douyin.com/RqrrHs8Lh6A/");
        assertEquals("保持强者的生活方式", result.title);
    }

    private static HttpClient session(String pageCookie, String registrationCookie, String callbackCookie,
            Exception failure, String callback) {
        return new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.equals(CALLBACK)) {
                    if (failure != null) throw failure;
                    return new HttpFetcher.Response(url, "{}", callbackCookie);
                }
                if (url.contains("unrelated.example")) throw new AssertionError("Unrelated callback was requested");
                if (url.contains("/aweme/detail/")) {
                    String cookies = headers.get("Cookie");
                    String expected = callbackCookie.isEmpty() ? registrationCookie : callbackCookie;
                    if (cookies != null && !expected.isEmpty() && cookies.contains(expected)
                            && (pageCookie.isEmpty() || !cookies.contains(pageCookie))) {
                        return new HttpFetcher.Response(url, ITEM);
                    }
                    return new HttpFetcher.Response(url, "{}");
                }
                return new HttpFetcher.Response("https://www.iesdouyin.com/share/video/" + ID + "/", "<html></html>", pageCookie);
            }

            @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) {
                return new HttpFetcher.Response(url, "{\"status_code\":0,\"redirect_url\":\"" + callback + "\"}", registrationCookie);
            }
        };
    }
}
