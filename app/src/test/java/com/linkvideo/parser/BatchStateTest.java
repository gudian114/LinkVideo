package com.linkvideo.parser;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class BatchStateTest {
    @Test public void beginsWithOneEmptyInput() {
        BatchState state = new BatchState();
        assertEquals(1, state.size());
        assertEquals("", state.entry(0).input);
    }

    @Test public void backspaceAndComposingDeletionDoNotOpenNextInput() {
        assertFalse(BatchState.opensNextInput("ab"));
        assertFalse(BatchState.opensNextInput(""));
        assertFalse(BatchState.opensNextInput("分享"));
    }

    @Test public void candidateConversionAndOrdinaryReplacementDoNotAdvance() {
        assertFalse(BatchState.opensNextInput("中"));
        assertFalse(BatchState.opensNextInput("z"));
        assertFalse(BatchState.opensNextInput("abc"));
        assertFalse(BatchState.opensNextInput("ab"));
    }

    @Test public void typedLineSeparatorOpensNextInput() {
        assertTrue(BatchState.opensNextInput("\n"));
        assertTrue(BatchState.opensNextInput("\n"));
    }

    @Test public void pastedMultilineShareTextIsKeptInOneInput() {
        assertFalse(BatchState.opensNextInput("分享文案\nhttps://v.douyin.com/a/"));
    }

    @Test public void keyboardClipboardCanReplaceSelectedTextWithAShorterLink() {
        assertFalse(BatchState.opensNextInput("https://v.douyin.com/a/"));
        assertFalse(BatchState.opensNextInput("ab"));
    }

    @Test public void deletionOverASelectionDoesNotOpenNextInput() {
        assertFalse(BatchState.opensNextInput(""));
    }

    @Test public void emptyInputCannotCreateOrMoveToAnotherBox() {
        BatchState state = new BatchState();
        assertTrue(state.next(0) < 0);
        assertEquals(1, state.size());
        state.entry(0).input = " \n\t ";
        assertTrue(state.next(0) < 0);
        assertEquals(1, state.size());
        state.entry(0).input = "一条分享文案";
        assertEquals(1, state.next(0));
        state.entry(0).input = "";
        assertTrue(state.next(0) < 0);
        assertEquals(2, state.size());
    }

    @Test public void unicodeWhitespaceCannotOpenAnotherBoxOrEnterParseQueue() {
        BatchState state = new BatchState();
        state.entry(0).input = "\u3000\u2003\u00a0";
        assertTrue(state.next(0) < 0);
        assertEquals(1, state.size());
        assertTrue(state.parseTargets().isEmpty());
    }

    @Test public void duplicateShareLinksAreGroupedWithoutComparingTheirCaptions() {
        BatchState state = new BatchState();
        state.entry(0).input = "第一段文案 https://v.douyin.com/AbCd/";
        state.next(0);
        state.entry(1).input = "第二段文案 https://v.douyin.com/AbCd/";
        state.next(1);
        state.entry(2).input = "https://v.douyin.com/AbCd/";
        assertEquals(Collections.singletonList(Arrays.asList(0, 1, 2)), state.similarLinks(state.parseTargets()));
    }

    @Test public void duplicateCheckDoesNotTreatShortLinkCaseOrFailedRecognitionAsDuplicates() {
        BatchState state = new BatchState();
        state.entry(0).input = "https://v.douyin.com/AbCd/";
        state.next(0);
        state.entry(1).input = "https://v.douyin.com/abcd/";
        state.next(1);
        state.entry(2).input = "不是链接";
        state.next(2);
        state.entry(3).input = "不是链接";
        assertTrue(state.similarLinks(state.parseTargets()).isEmpty());
        assertEquals(Arrays.asList(0, 1, 2, 3), state.parseTargets());
    }

    @Test public void singleEntryDuplicateCheckIncludesMatchingOtherBoxesOnly() {
        BatchState state = new BatchState();
        state.entry(0).input = "https://v.douyin.com/first/";
        state.next(0);
        state.entry(1).input = "https://v.douyin.com/first/";
        state.next(1);
        state.entry(2).input = "https://v.kuaishou.com/other/";
        assertEquals(Collections.singletonList(Arrays.asList(0, 1)), state.similarLinks(Collections.singletonList(1)));
        assertTrue(state.similarLinks(Collections.singletonList(2)).isEmpty());
    }

    @Test public void movingFromEarlierInputReusesItsExistingNextInput() {
        BatchState state = new BatchState();
        state.entry(0).input = "https://v.douyin.com/original/";
        assertEquals(1, state.next(0));
        state.entry(1).input = "第二条链接";
        assertEquals(1, state.next(0));
        assertEquals(2, state.size());
        assertEquals("https://v.douyin.com/original/", state.entry(0).input);
        assertEquals("第二条链接", state.entry(1).input);
    }

    @Test public void cannotCreateAnEleventhInput() {
        BatchState state = new BatchState();
        for (int i = 0; i < 9; i++) {
            state.entry(i).input = "链接 " + i;
            assertEquals(i + 1, state.next(i));
        }
        state.entry(9).input = "第十条链接";
        assertEquals(-1, state.next(9));
        assertEquals(10, state.size());
    }

    @Test public void previewNavigationSkipsSuccessfulCardsButKeepsFailedInputsEditable() {
        BatchState state = new BatchState();
        state.entry(0).input = "第一条";
        state.next(0);
        state.entry(1).input = "第二条";
        state.entry(1).acceptVideo(video());
        state.next(1);
        state.entry(2).input = "第三条";
        state.entry(2).acceptVideo(video());
        state.entry(2).parseFailed = true;
        assertEquals(2, state.next(0, true));
        assertEquals(1, state.next(0, false));
        assertEquals(3, state.size());
    }

    @Test public void previewNavigationCreatesAnInputAfterTheLastSuccessfulCard() {
        BatchState state = new BatchState();
        state.entry(0).input = "第一条";
        state.next(0);
        state.entry(1).input = "第二条";
        state.entry(1).acceptVideo(video());
        assertEquals(2, state.next(0, true));
        assertEquals("", state.entry(2).input);
        assertEquals("第二条", state.entry(1).input);
        state.entry(0).input = " ";
        assertEquals(BatchState.EMPTY_INPUT, state.next(0, true));
        assertEquals(3, state.size());
    }

    @Test public void previewNavigationStillHonorsTheTenRowLimit() {
        BatchState state = new BatchState();
        for (int i = 0; i < 10; i++) {
            state.entry(i).input = "链接" + i;
            state.entry(i).acceptVideo(video());
            if (i < 9) state.next(i);
        }
        assertEquals(-1, state.next(0, true));
        assertEquals(10, state.size());
    }

    @Test public void parseQueueSkipsEmptyAndWhitespaceInputs() {
        BatchState state = new BatchState();
        state.entry(0).input = "创建第二框";
        state.next(0);
        state.entry(0).input = " \n ";
        state.entry(1).input = "https://v.douyin.com/a/";
        state.next(1);
        assertEquals(Arrays.asList(1), state.parseTargets());
    }

    @Test public void clearAllResetsToOneNewEmptyEntry() {
        BatchState state = new BatchState();
        VideoResult video = video();
        state.entry(0).input = "分享链接";
        state.entry(0).result = video;
        state.entry(0).downloadPath = "Download/one.mp4";
        state.next(0);
        state.entry(1).input = "第二条";
        state.clearInputs();
        assertEquals(1, state.size());
        assertEquals("", state.entry(0).input);
        assertNull(state.entry(0).result);
        assertEquals("", state.entry(0).downloadPath);
        assertTrue(state.videoTargets().isEmpty());
    }

    @Test public void removingAnEmptyMiddleBoxPreservesFollowingLinkAndResult() {
        BatchState state = new BatchState();
        state.entry(0).input = "第一个";
        state.next(0);
        state.entry(1).input = "第二个";
        state.next(1);
        BatchState.Entry last = state.entry(2);
        last.input = "第三个";
        last.result = video();
        last.downloadPath = "Download/last.mp4";
        state.entry(1).input = "";
        assertTrue(state.removeEmpty(1));
        assertEquals(2, state.size());
        assertSame(last, state.entry(1));
        assertEquals(Arrays.asList(0, 1), state.parseTargets());
        assertEquals(Arrays.asList(1), state.videoTargets());
    }

    @Test public void backspaceDoesNotRemoveNonEmptyOrLastRemainingBox() {
        BatchState state = new BatchState();
        assertFalse(state.removeEmpty(0));
        state.entry(0).input = "文字";
        state.next(0);
        assertFalse(state.removeEmpty(0));
        state.entry(0).input = " ";
        assertFalse(state.removeEmpty(0));
        assertTrue(state.removeEmpty(1));
        assertEquals(1, state.size());
    }

    @Test public void deletingFirstOrLastBoxKeepsNumberingAndFreesOneSlot() {
        BatchState state = new BatchState();
        for (int i = 0; i < 9; i++) {
            state.entry(i).input = "链接" + i;
            state.next(i);
        }
        state.entry(0).input = "";
        assertTrue(state.removeEmpty(0));
        assertEquals("链接1", state.entry(0).input);
        assertTrue(state.removeEmpty(8));
        state.entry(7).input = "最后一条";
        assertEquals(8, state.next(7));
        assertEquals(9, state.size());
    }

    @Test public void downloadQueueContainsVideosAndSkipsImagesAndUnparsedInputs() {
        BatchState state = new BatchState();
        state.entry(0).input = "第一条图文链接";
        state.entry(0).result = images();
        state.next(0);
        state.entry(1).input = "第二条视频链接";
        state.entry(1).result = video();
        state.next(1);
        assertEquals(Arrays.asList(1), state.videoTargets());
    }

    @Test public void serializingStatePreservesLinksAndBothMediaResultTypes() throws Exception {
        BatchState state = new BatchState();
        state.entry(0).input = "https://v.douyin.com/one/";
        state.entry(0).result = video();
        state.next(0);
        state.entry(1).input = "https://v.kuaishou.com/two";
        state.entry(1).result = images();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(state); }
        BatchState restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BatchState) input.readObject();
        }
        assertEquals(2, restored.size());
        assertEquals("https://v.douyin.com/one/", restored.entry(0).input);
        assertEquals("https://video.example/one.mp4", ((VideoResult) restored.entry(0).result).streams.get(0).url);
        assertEquals("https://img.example/one.jpg", ((ImagePostResult) restored.entry(1).result).images.get(0).primaryUrl());
    }

    private static VideoResult video() {
        return new VideoResult("抖音", "视频文案", "作者", "", "https://www.douyin.com/video/1", "", 1,
                Collections.singletonList(new MediaStream("https://video.example/one.mp4", "视频", "combined",
                        "video/mp4", Collections.emptyMap())));
    }

    private static ImagePostResult images() {
        return new ImagePostResult("快手", "图文文案", "作者", "https://www.kuaishou.com/photo/1", "",
                Collections.singletonList(new ImageResource(Collections.singletonList("https://img.example/one.jpg"),
                        Collections.emptyMap(), 1080, 1440)));
    }
}
