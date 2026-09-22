package com.linkvideo.parser;

import org.junit.Test;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class ParseDiagnosticsTest {
    private static final String LINK = "https://www.douyin.com/video/7681939019656603131";

    @Test public void fallbackErrorsSurviveFinalGenericFailureWithoutSecrets() throws Exception {
        HttpClient fake = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
                if (url.contains("/detail/")) throw new SocketTimeoutException("secret-token " + url);
                return new HttpFetcher.Response(LINK, "<html>私人文案</html>", "ttwid=secret-cookie");
            }
            @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) {
                return new HttpFetcher.Response(url, "{\"status_code\":0,\"redirect_url\":\"https://www.douyin.com/ttwid/union/register/callback/?ticket=secret-ticket\"}", "ttwid=secret-cookie");
            }
        };
        String details = failure(new VideoParser(fake)).details;
        assertTrue(details, details.contains("签名详情"));
        assertTrue(details, details.contains("SocketTimeoutException"));
        assertTrue(details, details.contains("匿名注册回调"));
        assertTrue(details, details.contains("ttwid=有"));
        assertFalse(details, details.contains("secret"));
        assertFalse(details, details.contains("私人文案"));
        assertFalse(details, details.contains("7681939019656603131"));
        assertFalse(details, details.contains("a_bogus="));
    }

    @Test public void emptyAndMalformedJsonAreDistinguishedAndTraceIsPerAttempt() throws Exception {
        VideoParser parser = new VideoParser((url, headers) -> new HttpFetcher.Response(LINK,
                url.contains("/detail/") ? "" : "<html>页面</html>"));
        String first = failure(parser).details;
        String second = failure(parser).details;
        assertTrue(first, first.contains("空响应"));
        assertEquals(first.split("GET ").length, second.split("GET ").length);
        assertTrue(ParseDiagnostics.responseShape("{bad-json").contains("JSON无法读取"));
        assertTrue(ParseDiagnostics.responseShape("{\"status_code\":0,\"aweme_detail\":null}").contains("作品数据=无"));
        assertTrue(ParseDiagnostics.responseShape("{\"status_code\":0,\"aweme_detail\":{\"desc\":\"不要复制正文\"}}").contains("作品数据=有"));
        assertFalse(ParseDiagnostics.responseShape("{\"status_code\":\"secret\",\"message\":\"secret\"}").contains("secret"));
    }

    @Test public void cancellationIsNotTurnedIntoAParseFailure() throws Exception {
        VideoParser parser = new VideoParser((url, headers) -> { throw new CancellationException(); });
        try { parser.parse(LINK); fail("expected cancellation"); }
        catch (CancellationException expected) { }
    }

    @Test public void httpStatusIsKeptWithoutCopyingExceptionMessages() throws Exception {
        String details = failure(new VideoParser((url, headers) -> { throw new HttpFetcher.Failure(403); })).details;
        assertTrue(details, details.contains("HTTP 403"));
        assertTrue(details, details.contains("请求失败"));
        assertFalse(details, details.contains(LINK));
    }

    @Test public void invalidInputIsNotGivenAFabricatedNetworkTrace() throws Exception {
        try { new VideoParser((url, headers) -> { throw new AssertionError(); }).parse("hello"); fail(); }
        catch (Exception expected) { assertFalse(expected instanceof ParseFailure); }
    }

    @Test public void browserTimeoutKeepsItsStageDiagnosticsWithoutTheExceptionSecret() throws Exception {
        HttpClient browser = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) { throw new AssertionError(); }
            @Override public HttpFetcher.Response browserGet(String page, String url, Map<String, String> headers) throws Exception {
                throw new SocketTimeoutException("secret-cookie");
            }
            @Override public String browserDiagnostics() { return "网页状态：页面网络失败，网络错误=-2\n"; }
        };
        ParseDiagnostics trace = new ParseDiagnostics(browser);
        try { trace.browserGet(LINK, LINK + "?secret=query", java.util.Collections.emptyMap()); fail(); }
        catch (SocketTimeoutException expected) { }
        assertTrue(trace.report(), trace.report().contains("页面网络失败"));
        assertTrue(trace.report(), trace.report().contains("网络错误=-2"));
        assertFalse(trace.report(), trace.report().contains("secret"));
    }

    private static ParseFailure failure(VideoParser parser) throws Exception {
        try { parser.parse(LINK); fail("expected parse failure"); }
        catch (ParseFailure expected) { return expected; }
        return null;
    }
}
