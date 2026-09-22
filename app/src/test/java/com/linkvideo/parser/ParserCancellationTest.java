package com.linkvideo.parser;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class ParserCancellationTest {
    private static final String URL = "https://www.douyin.com/video/7123456789012345678";

    @Test public void interruptedParserDoesNotStartItsFirstRequest() throws Exception {
        int[] calls = {0};
        Thread.currentThread().interrupt();
        try {
            expectCancelled(() -> new VideoParser((url, headers) -> {
                calls[0]++;
                return new HttpFetcher.Response(URL, "");
            }).parse(URL));
            assertEquals(0, calls[0]);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test public void cancellationAfterPageFetchDoesNotStartTheShareFallback() throws Exception {
        int[] calls = {0};
        List<Integer> progress = new ArrayList<>();
        try {
            expectCancelled(() -> new VideoParser((url, headers) -> {
                calls[0]++;
                Thread.currentThread().interrupt();
                return new HttpFetcher.Response(URL, "");
            }).parse(URL, progress::add));
            assertEquals(1, calls[0]);
            assertFalse(progress.contains(100));
        } finally { Thread.interrupted(); }
    }

    @Test public void fallbackDoesNotSwallowCancellationAndRequestAnotherEndpoint() throws Exception {
        int[] calls = {0};
        expectCancelled(() -> new VideoParser((url, headers) -> {
            if (++calls[0] == 3) throw new CancellationException("cancelled");
            return new HttpFetcher.Response(URL, "", "anonymous=1");
        }).parse(URL));
        assertEquals(3, calls[0]);
    }

    @Test public void interruptedFallbackRestoresTheInterruptFlag() throws Exception {
        int[] calls = {0};
        try {
            expectCancelled(() -> new VideoParser((url, headers) -> {
                if (++calls[0] == 3) throw new InterruptedException("cancelled");
                return new HttpFetcher.Response(URL, "", "anonymous=1");
            }).parse(URL));
            assertEquals(3, calls[0]);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test public void interruptedFetcherStopsBeforeOpeningAConnection() throws Exception {
        Thread.currentThread().interrupt();
        try { expectCancelled(() -> new HttpFetcher().get("not a URL", HttpFetcher.noHeaders())); }
        finally { Thread.interrupted(); }
    }

    @Test public void interruptedBodyReadDoesNotReadAnyBytes() throws Exception {
        int[] reads = {0};
        InputStream body = new ByteArrayInputStream(new byte[]{1}) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                reads[0]++;
                return super.read(bytes, offset, length);
            }
        };
        Thread.currentThread().interrupt();
        try { expectCancelled(() -> readBody(body)); assertEquals(0, reads[0]); }
        finally { Thread.interrupted(); }
    }

    @Test public void cancellationDuringReadDoesNotConsumeTheNextChunk() throws Exception {
        int[] reads = {0};
        InputStream body = new ByteArrayInputStream(new byte[20000]) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                reads[0]++;
                Thread.currentThread().interrupt();
                return super.read(bytes, offset, length);
            }
        };
        try { expectCancelled(() -> readBody(body)); assertEquals(1, reads[0]); }
        finally { Thread.interrupted(); }
    }

    private interface Action { void run() throws Exception; }

    private static void expectCancelled(Action action) throws Exception {
        try { action.run(); fail("Cancellation must propagate"); }
        catch (CancellationException expected) { }
    }

    private static String readBody(InputStream body) throws Exception {
        Method read = HttpFetcher.class.getDeclaredMethod("readLimited", InputStream.class);
        read.setAccessible(true);
        try { return (String) read.invoke(null, body); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }
}
