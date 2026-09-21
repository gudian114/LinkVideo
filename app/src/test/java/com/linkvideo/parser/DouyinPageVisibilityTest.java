package com.linkvideo.parser;

import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinPageVisibilityTest {
    @Test public void hiddenPageGetsEightSecondsBeforeReveal() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 100000);
        assertFalse(state.shouldReveal(107999, true));
        assertTrue(state.shouldReveal(108000, true));
    }

    @Test public void ActualProgressRestartsIdleWaitAtAnyPercentage() {
        for (int progress : new int[] {10, 73, 88, 100}) {
            DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
            state.observe(progress, "loading", 4000);
            assertFalse(state.shouldReveal(8000, true));
            assertFalse(state.shouldReveal(11999, true));
            assertTrue(state.shouldReveal(12000, true));
        }
    }

    @Test public void RepeatedPollsAndProgressRegressionsDoNotPostponeReveal() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observe(50, "interactive", 1000);
        state.observe(50, "interactive", 5000);
        state.observe(10, "loading", 8000);
        assertTrue(state.shouldReveal(9000, true));
    }

    @Test public void DocumentReadinessIsMeaningfulProgress() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observe(100, "loading", 1000);
        state.observe(100, "complete", 6000);
        assertFalse(state.shouldReveal(9000, true));
        assertTrue(state.shouldReveal(14000, true));
    }

    @Test public void SlowContinuousProgressHasABoundedHiddenWait() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.observe(10, "loading", 7000);
        state.observe(30, "interactive", 14000);
        assertFalse(state.shouldReveal(15999, true));
        assertTrue(state.shouldReveal(16000, true));
    }

    @Test public void NoPopupInBackgroundOrOverAnotherWindow() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        assertFalse(state.shouldReveal(16000, false));
        assertTrue(state.shouldReveal(18000, true));
    }

    @Test public void VisiblePageGetsFullVerificationTimeAndNeverReopens() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.shown(16000);
        assertFalse(state.shouldReveal(17000, true));
        assertFalse(state.expired(75999));
        assertTrue(state.expired(76000));
        state.shown(75000); // An accidental repeated notification must not extend the deadline.
        assertTrue(state.expired(76000));
    }

    @Test public void BackgroundTimeoutCannotCauseLatePopup() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        assertFalse(state.expired(24999));
        assertTrue(state.expired(25000));
        assertFalse(state.shouldReveal(25000, true));
    }

    @Test public void FinishedOrCancelledSessionIgnoresLateEvents() {
        DouyinPageVisibility state = new DouyinPageVisibility(false, 0);
        state.finish();
        state.observe(90, "complete", 9000);
        assertFalse(state.shouldReveal(16000, true));
    }

    @Test public void ExplicitSettingShowsImmediatelyWhenForeground() {
        DouyinPageVisibility state = new DouyinPageVisibility(true, 123456789000L);
        assertFalse(state.shouldReveal(123456789000L, false));
        assertTrue(state.shouldReveal(123456789000L, true));
        state.shown(123456789000L);
        assertFalse(state.expired(123456848999L));
        assertTrue(state.expired(123456849000L));
    }
}
