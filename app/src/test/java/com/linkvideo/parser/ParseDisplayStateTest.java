package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParseDisplayStateTest {
    @Test public void keepsCurrentResultWhileAnotherParseIsRunning() {
        ParseDisplayState<String> state = new ParseDisplayState<>();
        state.succeeded("旧视频");
        state.started();
        assertTrue(state.isParsing());
        assertEquals("旧视频", state.current());
    }

    @Test public void failedParseKeepsCurrentResult() {
        ParseDisplayState<String> state = new ParseDisplayState<>();
        state.succeeded("旧视频");
        state.started();
        state.failed();
        assertFalse(state.isParsing());
        assertEquals("旧视频", state.current());
    }

    @Test public void successfulParseReplacesCurrentResult() {
        ParseDisplayState<String> state = new ParseDisplayState<>();
        state.succeeded("旧视频");
        state.started();
        state.succeeded("新视频");
        assertFalse(state.isParsing());
        assertEquals("新视频", state.current());
    }

    @Test public void imageNavigationClearsPreviousVideoWhenSaveIsDisabled() {
        ParseDisplayState<String> state = new ParseDisplayState<>();
        state.succeeded("旧视频");
        state.started();

        state.finished(false);

        assertFalse(state.isParsing());
        assertNull(state.current());
    }

    @Test public void imageNavigationKeepsVideoWhenUserChoosesSave() {
        ParseDisplayState<String> state = new ParseDisplayState<>();
        state.succeeded("旧视频");
        state.started();

        state.finished(true);

        assertFalse(state.isParsing());
        assertEquals("旧视频", state.current());
    }
}
