package com.linkvideo.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class BatchSelectionTest {
    @Test public void enteringSelectionStartsUncheckedAndCancelClearsIt() {
        BatchState batch = entries(3);
        batch.beginSelection(false);
        assertTrue(batch.isSelecting());
        assertFalse(batch.selectingVideos());
        assertTrue(batch.selectedTargets().isEmpty());
        batch.setSelected(1, true);
        assertEquals(Collections.singletonList(1), batch.selectedTargets());
        batch.cancelSelection();
        assertFalse(batch.isSelecting());
        assertTrue(batch.selectedTargets().isEmpty());
        batch.beginSelection(false);
        assertTrue(batch.selectedTargets().isEmpty());
    }

    @Test public void inputDeletionRemovesOnlyChosenEntriesAndRenumbersSurvivors() {
        BatchState batch = entries(4);
        BatchState.Entry second = batch.entry(1), last = batch.entry(3);
        batch.beginSelection(false);
        batch.setSelected(0, true);
        batch.setSelected(2, true);
        assertEquals(Arrays.asList(0, 2), batch.deleteSelected(false));
        assertEquals(2, batch.size());
        assertSame(second, batch.entry(0));
        assertSame(last, batch.entry(1));
        assertFalse(batch.isSelecting());
    }

    @Test public void deletingAllInputsLeavesOneFreshEmptyBox() {
        BatchState batch = entries(10);
        batch.beginSelection(false);
        batch.toggleSelectAll();
        assertTrue(batch.allSelected());
        assertEquals(10, batch.deleteSelected(false).size());
        assertEquals(1, batch.size());
        assertEquals("", batch.entry(0).input);
        assertNull(batch.entry(0).result);
    }

    @Test public void selectAllTogglesAndManualDeselectUpdatesItsState() {
        BatchState batch = entries(3);
        batch.beginSelection(false);
        batch.toggleSelectAll();
        assertTrue(batch.allSelected());
        batch.setSelected(1, false);
        assertFalse(batch.allSelected());
        batch.toggleSelectAll();
        assertEquals(Arrays.asList(0, 1, 2), batch.selectedTargets());
        batch.toggleSelectAll();
        assertTrue(batch.selectedTargets().isEmpty());
        assertFalse(batch.allSelected());
    }

    @Test public void videoSelectionIgnoresFailedOrUnparsedEntries() {
        BatchState batch = entries(4);
        batch.entry(0).result = video();
        batch.entry(2).result = video();
        batch.beginSelection(true);
        batch.setSelected(1, true);
        assertTrue(batch.selectedTargets().isEmpty());
        batch.toggleSelectAll();
        assertTrue(batch.allSelected());
        assertEquals(Arrays.asList(0, 2), batch.selectedTargets());
        assertEquals(Arrays.asList(0, 2), batch.downloadTargets());
    }

    @Test public void choosingVideoOneAndThreeDownloadsOnlyThoseAndEmptyNeverMeansAll() {
        BatchState batch = entries(3);
        for (int i = 0; i < 3; i++) batch.entry(i).result = video();
        assertEquals(Arrays.asList(0, 1, 2), batch.downloadTargets());
        batch.beginSelection(true);
        assertTrue(batch.downloadTargets().isEmpty());
        batch.setSelected(0, true);
        batch.setSelected(2, true);
        assertEquals(Arrays.asList(0, 2), batch.downloadTargets());
        batch.toggleSelectAll();
        batch.toggleSelectAll();
        assertTrue(batch.downloadTargets().isEmpty());
        batch.cancelSelection();
        assertEquals(Arrays.asList(0, 1, 2), batch.downloadTargets());
    }

    @Test public void deletingVideoResultsPreservesLinksAndOtherResults() {
        BatchState batch = entries(3);
        VideoResult retained = video();
        for (int i = 0; i < 3; i++) {
            batch.entry(i).result = retained;
            batch.entry(i).downloadPath = "Download/" + i + ".mp4";
        }
        batch.beginSelection(true);
        batch.setSelected(0, true);
        batch.setSelected(2, true);
        assertEquals(Arrays.asList(0, 2), batch.deleteSelected(false));
        assertEquals(3, batch.size());
        assertEquals("链接0", batch.entry(0).input);
        assertEquals("链接2", batch.entry(2).input);
        assertNull(batch.entry(0).result);
        assertNull(batch.entry(2).result);
        assertEquals("", batch.entry(0).downloadPath);
        assertSame(retained, batch.entry(1).result);
        assertEquals("Download/1.mp4", batch.entry(1).downloadPath);
        assertEquals(Collections.singletonList(1), batch.videoTargets());
    }

    @Test public void inputSelectionCannotAccidentallyDownloadVideos() {
        BatchState batch = entries(2);
        batch.entry(0).result = video();
        batch.beginSelection(false);
        batch.setSelected(0, true);
        assertTrue(batch.downloadTargets().isEmpty());
    }

    @Test public void clearingBatchResetsSelectionAsWellAsInputs() {
        BatchState batch = entries(3);
        batch.beginSelection(false);
        batch.toggleSelectAll();
        batch.clearInputs();
        assertFalse(batch.isSelecting());
        assertTrue(batch.selectedTargets().isEmpty());
        assertEquals(1, batch.size());
    }

    @Test public void rotatingRestoresTheSameSelectedVideos() throws Exception {
        BatchState batch = entries(3);
        for (int i = 0; i < 3; i++) batch.entry(i).result = video();
        batch.beginSelection(true);
        batch.setSelected(0, true);
        batch.setSelected(2, true);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(batch); }
        BatchState restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BatchState) input.readObject();
        }
        assertTrue(restored.selectingVideos());
        assertEquals(Arrays.asList(0, 2), restored.downloadTargets());
        restored.deleteSelected(false);
        assertEquals(Collections.singletonList(1), restored.videoTargets());
    }

    @Test public void combinedDeletionRemovesSelectedVideoCardsAndKeepsUnparsedOnes() {
        BatchState batch = entries(4);
        batch.entry(0).result = video();
        batch.entry(2).result = video();
        BatchState.Entry second = batch.entry(1), last = batch.entry(3);
        batch.beginSelection(true);
        batch.toggleSelectAll();
        assertEquals(Arrays.asList(0, 2), batch.deleteSelected(true));
        assertEquals(2, batch.size());
        assertSame(second, batch.entry(0));
        assertSame(last, batch.entry(1));
        assertEquals("链接1", batch.entry(0).input);
        assertTrue(batch.videoTargets().isEmpty());
        assertFalse(batch.isSelecting());
    }

    @Test public void combinedDeletionOfEveryVideoStillLeavesOneEmptyInput() {
        BatchState batch = entries(3);
        for (int i = 0; i < 3; i++) batch.entry(i).result = video();
        batch.beginSelection(true);
        batch.toggleSelectAll();
        batch.deleteSelected(true);
        assertEquals(1, batch.size());
        assertEquals("", batch.entry(0).input);
        assertNull(batch.entry(0).result);
        assertEquals("", batch.entry(0).downloadPath);
    }

    @Test public void anEmptySelectionNeverDeletesAnything() {
        BatchState batch = entries(3);
        batch.entry(0).result = video();
        batch.beginSelection(true);
        assertTrue(batch.deleteSelected(true).isEmpty());
        assertEquals(3, batch.size());
        assertNotNull(batch.entry(0).result);
        batch.beginSelection(false);
        assertTrue(batch.deleteSelected(false).isEmpty());
        assertEquals(3, batch.size());
    }

    private static BatchState entries(int count) {
        BatchState batch = new BatchState();
        for (int i = 0; i < count; i++) {
            batch.entry(i).input = "链接" + i;
            if (i + 1 < count) batch.next(i);
        }
        return batch;
    }

    private static VideoResult video() {
        return new VideoResult("抖音", "文案", "作者", "", "https://www.douyin.com/video/1", "", 1,
                Collections.singletonList(new MediaStream("https://video.example/one.mp4", "视频", "combined",
                        "video/mp4", Collections.emptyMap())));
    }
}

