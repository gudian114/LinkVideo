package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinPageVisibilityTest {
    @Test public void defaultModeNeverRevealsOnlyBecauseTimePassed() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 100_000);
        assertFalse(state.shouldReveal(100_000, true));
        assertFalse(state.shouldReveal(104_000, true));
        assertFalse(state.shouldReveal(124_999, true));
    }

    @Test public void transientVerificationUiDoesNotRevealThePage() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observeInteraction(true, 1_000);
        state.observeInteraction(true, 4_000);
        assertFalse(state.shouldReveal(4_000, true));
        state.observeInteraction(false, 4_001);
        state.observeInteraction(false, 6_000);
        assertFalse(state.shouldReveal(10_000, true));
    }

    @Test public void oneShortDetectionGapDoesNotRestartARealVerificationTimer() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observeInteraction(true, 1_000);
        state.observeInteraction(true, 4_000);
        state.observeInteraction(false, 4_750);
        state.observeInteraction(true, 5_500);
        state.observeInteraction(true, 6_000);
        assertTrue(state.shouldReveal(6_000, true));
    }

    @Test public void persistentVerificationUiRevealsAfterFiveSecondGrace() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observeInteraction(true, 1_000);
        state.observeInteraction(true, 5_999);
        assertFalse(state.shouldReveal(5_999, true));
        state.observeInteraction(true, 6_000);
        assertTrue(state.shouldReveal(6_000, true));
    }

    @Test public void ExplicitSettingShowsImmediatelyWhenForeground() {
        DouyinPageVisibility state = new DouyinPageVisibility(true, 123456789000L);
        assertFalse(state.shouldReveal(123456789000L, false));
        assertTrue(state.shouldReveal(123456789000L, true));
    }

    @Test public void VisiblePageGetsFullVerificationTimeAndNeverReopens() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.shown(4_000);
        assertFalse(state.shouldReveal(5_000, true));
        assertFalse(state.expired(63_999));
        assertTrue(state.expired(64_000));
        state.shown(60_000);
        assertTrue(state.expired(64_000));
    }

    @Test public void HiddenSessionStillHasABoundedLifetime() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        assertFalse(state.expired(24_999));
        assertTrue(state.expired(25_000));
        assertFalse(state.shouldReveal(25_000, true));
    }

    @Test public void verificationStartingNearHiddenDeadlineStillGetsItsGracePeriod() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observeInteraction(true, 23_000);
        state.observeInteraction(true, 24_500);
        assertFalse(state.expired(25_000));
        state.observeInteraction(true, 26_000);
        state.observeInteraction(true, 27_000);
        state.observeInteraction(true, 28_000);
        assertTrue(state.shouldReveal(28_000, true));
    }

    @Test public void FinishedOrCancelledSessionIgnoresLateEvents() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.finish();
        state.observeInteraction(true, 1);
        state.observe(90, "complete", 1);
        assertFalse(state.shouldReveal(1, true));
    }
}
