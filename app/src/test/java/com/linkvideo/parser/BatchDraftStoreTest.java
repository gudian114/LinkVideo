package com.linkvideo.parser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BatchDraftStoreTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void missingDraftStartsWithOneEmptyBox() {
        BatchState restored = BatchDraftStore.load(new File(folder.getRoot(), "draft"));
        assertEquals(1, restored.size());
        assertEquals("", restored.entry(0).input);
    }

    @Test public void reopeningRestoresLinksResultsEditedCaptionAndSavedLocations() throws Exception {
        File file = new File(folder.getRoot(), "draft");
        BatchState batch = new BatchState();
        batch.entry(0).input = "分享文案 https://v.douyin.com/a/";
        batch.entry(0).acceptVideo(video());
        batch.entry(0).editedCaption = "我改过的文案";
        batch.entry(0).downloadPath = "Download/视频.mp4";
        batch.entry(0).coverDownloadPath = "Download/封面.jpg";
        batch.next(0);
        batch.entry(1).input = "尚未解析的链接";
        BatchDraftStore.save(file, batch);
        BatchState restored = BatchDraftStore.load(file);
        assertEquals(2, restored.size());
        assertEquals(batch.entry(0).input, restored.entry(0).input);
        assertEquals("原文", restored.entry(0).result.title());
        assertEquals("我改过的文案", restored.entry(0).caption());
        assertEquals("Download/视频.mp4", restored.entry(0).downloadPath);
        assertEquals("Download/封面.jpg", restored.entry(0).coverDownloadPath);
        assertEquals("尚未解析的链接", restored.entry(1).input);
    }

    @Test public void clearOnReturnDoesNotResurrectPreviousResults() throws Exception {
        File file = new File(folder.getRoot(), "draft");
        BatchState batch = new BatchState();
        batch.entry(0).input = "旧链接";
        batch.entry(0).acceptVideo(video());
        BatchDraftStore.save(file, batch);
        BatchDraftStore.clear(file);
        BatchState restored = BatchDraftStore.load(file);
        assertEquals("", restored.entry(0).input);
        assertNull(restored.entry(0).result);
    }

    @Test public void replacingDraftDoesNotAppendOrRetainDeletedEntries() throws Exception {
        File file = new File(folder.getRoot(), "draft");
        BatchState batch = new BatchState();
        batch.entry(0).input = "第一条";
        batch.next(0);
        batch.entry(1).input = "第二条";
        BatchDraftStore.save(file, batch);
        batch.clearInputs();
        BatchDraftStore.save(file, batch);
        assertEquals(1, BatchDraftStore.load(file).size());
        assertEquals("", BatchDraftStore.load(file).entry(0).input);
    }

    @Test public void brokenDraftDoesNotPreventOpeningTheBatchPage() throws Exception {
        File file = folder.newFile();
        Files.write(file.toPath(), "not a saved batch".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, BatchDraftStore.load(file).size());
    }

    @Test public void newVideoAndResultDeletionResetOnlyTheirOwnEditedCaption() {
        BatchState batch = new BatchState();
        batch.entry(0).input = "分享";
        batch.entry(0).acceptVideo(video());
        assertEquals("原文", batch.entry(0).caption());
        batch.entry(0).editedCaption = "";
        assertEquals("", batch.entry(0).caption());
        batch.entry(0).acceptVideo(video());
        assertEquals("原文", batch.entry(0).caption());
        batch.entry(0).coverDownloadPath = "old.jpg";
        batch.beginSelection(true);
        batch.toggleSelectAll();
        batch.deleteSelected(false);
        assertEquals("分享", batch.entry(0).input);
        assertEquals("", batch.entry(0).caption());
        assertEquals("", batch.entry(0).coverDownloadPath);
    }

    private static VideoResult video() {
        return new VideoResult("抖音", "原文", "作者", "https://img.example/cover.jpg",
                "https://www.douyin.com/video/1", "", 60,
                Collections.singletonList(new MediaStream("https://video.example/one.mp4", "视频", "combined",
                        "video/mp4", Collections.emptyMap())));
    }
}
