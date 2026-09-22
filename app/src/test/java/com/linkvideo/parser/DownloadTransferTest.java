package com.linkvideo.parser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DownloadTransferTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void copiesKnownSizeAndReportsProgress() throws Exception {
        byte[] payload = "secure apk bytes".getBytes("UTF-8");
        File part = new File(temporary.getRoot(), "update.part");
        List<DownloadTransfer.Progress> progress = new ArrayList<>();
        long copied = DownloadTransfer.copy(new ByteArrayInputStream(payload), part,
                payload.length, 1024, new CancellationToken(), progress::add);
        assertEquals(payload.length, copied);
        assertArrayEquals(payload, Files.readAllBytes(part.toPath()));
        assertEquals(100, progress.get(progress.size() - 1).percent());
    }

    @Test public void unknownSizeUsesIndeterminateProgress() throws Exception {
        File part = new File(temporary.getRoot(), "unknown.part");
        List<DownloadTransfer.Progress> progress = new ArrayList<>();
        DownloadTransfer.copy(new ByteArrayInputStream(new byte[] {1, 2, 3}), part,
                0, 1024, new CancellationToken(), progress::add);
        assertEquals(-1, progress.get(progress.size() - 1).percent());
        assertEquals(3L, progress.get(progress.size() - 1).downloadedBytes());
    }

    @Test public void readFailureDeletesPartialFile() throws Exception {
        File part = new File(temporary.getRoot(), "failure.part");
        assertThrows(IOException.class, () -> DownloadTransfer.copy(failingStream(), part,
                0, 1024, new CancellationToken(), ignored -> { }));
        assertFalse(part.exists());
    }

    @Test public void cancellationDeletesPartialFile() throws Exception {
        File part = new File(temporary.getRoot(), "cancel.part");
        CancellationToken token = new CancellationToken();
        assertThrows(IOException.class, () -> DownloadTransfer.copy(
                new ByteArrayInputStream(new byte[70_000]), part, 70_000, 100_000, token,
                progress -> token.cancel()));
        assertTrue(token.isCanceled());
        assertFalse(part.exists());
    }

    @Test public void exceedingMaximumDeletesPartialFile() throws Exception {
        File part = new File(temporary.getRoot(), "large.part");
        IOException error = assertThrows(IOException.class, () -> DownloadTransfer.copy(
                new ByteArrayInputStream(new byte[20]), part, 0, 10,
                new CancellationToken(), ignored -> { }));
        assertTrue(error.getMessage().contains("过大"));
        assertFalse(part.exists());
    }

    @Test public void incompleteKnownSizeDeletesPartialFile() throws Exception {
        File part = new File(temporary.getRoot(), "short.part");
        assertThrows(IOException.class, () -> DownloadTransfer.copy(
                new ByteArrayInputStream(new byte[5]), part, 10, 100,
                new CancellationToken(), ignored -> { }));
        assertFalse(part.exists());
    }

    private static InputStream failingStream() {
        return new InputStream() {
            private int count;
            @Override public int read() throws IOException {
                if (count++ >= 4) throw new IOException("interrupted network");
                return 65;
            }
        };
    }
}
