package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewStateTest {
    @Test public void startsOnCoverWithPlayControlsVisible() {
        PreviewState state = new PreviewState();
        assertEquals(PreviewState.Mode.COVER, state.mode());
        assertTrue(state.controlsVisible());
        assertFalse(state.showsPauseIcon());
    }

    @Test public void playingControlsFadeTogetherAndSingleTapRevealsThem() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.prepared();
        assertTrue(state.showsPauseIcon());
        state.hideControls();
        assertFalse(state.controlsVisible());
        state.singleTap();
        assertTrue(state.controlsVisible());
        assertTrue(state.showsPauseIcon());
    }

    @Test public void controlsDoNotHideWhilePaused() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.prepared();
        state.pause();
        state.hideControls();
        assertTrue(state.controlsVisible());
    }

    @Test public void pauseAndResumeSwitchTheVisibleIcon() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.prepared();
        state.pause();
        assertEquals(PreviewState.Mode.PAUSED, state.mode());
        assertTrue(state.controlsVisible());
        assertFalse(state.showsPauseIcon());
        state.resume();
        assertEquals(PreviewState.Mode.PLAYING, state.mode());
        assertTrue(state.showsPauseIcon());
    }

    @Test public void completedVideoOffersReplayAndResetReturnsToCover() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.prepared();
        state.completed();
        assertEquals(PreviewState.Mode.ENDED, state.mode());
        assertTrue(state.controlsVisible());
        assertFalse(state.showsPauseIcon());
        state.reset();
        assertEquals(PreviewState.Mode.COVER, state.mode());
    }

    @Test public void failedVideoKeepsAVisibleRetryControl() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.failed();
        assertEquals(PreviewState.Mode.ERROR, state.mode());
        assertTrue(state.controlsVisible());
        assertFalse(state.showsPauseIcon());
    }

    @Test public void switchingToAnotherCardPreservesTheCompletedReplayState() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.prepared();
        state.completed();
        state.suspend();
        assertEquals(PreviewState.Mode.ENDED, state.mode());
        assertTrue(state.controlsVisible());
    }

    @Test public void switchingCardsStopsBothPlayingAndPreparingStates() {
        PreviewState state = new PreviewState();
        state.requestPlay();
        state.suspend();
        assertEquals(PreviewState.Mode.PAUSED, state.mode());
        state.requestPlay();
        state.prepared();
        state.hideControls();
        state.suspend();
        assertEquals(PreviewState.Mode.PAUSED, state.mode());
        assertTrue(state.controlsVisible());
        assertFalse(state.showsPauseIcon());
    }
}
