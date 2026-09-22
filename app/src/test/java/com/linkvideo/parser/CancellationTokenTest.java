package com.linkvideo.parser;

import org.junit.Test;

import java.io.InterruptedIOException;

import static org.junit.Assert.assertThrows;

public class CancellationTokenTest {
    @Test public void cancellationAfterTransferStopsFinalizationCheckpoint() {
        CancellationToken token = new CancellationToken();
        token.cancel();
        assertThrows(InterruptedIOException.class, token::throwIfCanceled);
    }
}
