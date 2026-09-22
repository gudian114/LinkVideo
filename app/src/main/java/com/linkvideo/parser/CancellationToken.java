package com.linkvideo.parser;

import java.io.InterruptedIOException;

final class CancellationToken {
    private volatile boolean canceled;

    void cancel() { canceled = true; }
    boolean isCanceled() { return canceled; }

    void throwIfCanceled() throws InterruptedIOException {
        if (canceled || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("操作已取消");
        }
    }
}
