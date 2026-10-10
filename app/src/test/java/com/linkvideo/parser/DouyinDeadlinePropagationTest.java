package com.linkvideo.parser;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinDeadlinePropagationTest {
    @Test public void douyinNetworkReceivesOnlyTheRemainingParseBudget() throws Exception {
        String id = "7312345678901234567";
        String item = "{\"aweme_id\":\"" + id + "\",\"video\":{\"play_addr\":{\"url_list\":[\"https://video.example/play.mp4\"]}}}";
        final long[] supplied = {0L};
        HttpClient client = new HttpClient() {
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers) {
                throw new AssertionError("Untimed GET must not be used for Douyin");
            }
            @Override public HttpFetcher.Response get(String url, Map<String, String> headers,
                    long maxDurationMillis) {
                supplied[0] = maxDurationMillis;
                return new HttpFetcher.Response("https://www.douyin.com/video/" + id,
                        "window._ROUTER_DATA=" + item);
            }
        };

        ParsedResult result = new VideoParser(client).parse("https://v.douyin.com/test/");

        assertTrue(result instanceof VideoResult);
        assertTrue(supplied[0] > 0L && supplied[0] <= 90_000L);
    }
}
