package com.linkvideo.parser;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinRouteStateTest {
    @Test public void forbiddenRouteCoolsDownButIdentityChangeCanProbeImmediately() {
        AtomicLong now = new AtomicLong(1_000);
        DouyinRouteState state = new DouyinRouteState(now::get, 15 * 60_000L);

        assertTrue(state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
        state.record(DouyinRouteState.Route.DETAIL, DouyinRouteOutcome.FORBIDDEN);

        assertFalse(state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
        assertTrue(state.shouldAttempt(DouyinRouteState.Route.DETAIL, true));
        now.addAndGet(15 * 60_000L);
        assertTrue(state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
    }

    @Test public void status11110OnlyDisablesLegacyRoute() {
        DouyinRouteState state = new DouyinRouteState(() -> 0L, 900_000L);
        state.record(DouyinRouteState.Route.LEGACY, DouyinRouteOutcome.STATUS_11110);
        state.record(DouyinRouteState.Route.DETAIL, DouyinRouteOutcome.STATUS_11110);

        assertFalse(state.shouldAttempt(DouyinRouteState.Route.LEGACY, false));
        assertTrue(state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
        assertTrue(state.shouldAttempt(DouyinRouteState.Route.SIGNED, false));
    }

    @Test public void transientAndEmptyResponsesNeverEnterCooldown() {
        DouyinRouteState state = new DouyinRouteState(() -> 0L, 900_000L);
        for (DouyinRouteOutcome outcome : new DouyinRouteOutcome[] {
                DouyinRouteOutcome.NETWORK_FAILURE, DouyinRouteOutcome.EMPTY,
                DouyinRouteOutcome.SUCCESS, DouyinRouteOutcome.SKIPPED }) {
            state.record(DouyinRouteState.Route.DETAIL, outcome);
            assertTrue(outcome.name(), state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
        }
    }

    @Test public void changedBrowserIdentityReopensOnlyOrdinaryDetail() {
        DouyinRouteState state = new DouyinRouteState(() -> 0L, 900_000L);
        for (DouyinRouteState.Route route : DouyinRouteState.Route.values()) {
            state.record(route, DouyinRouteOutcome.FORBIDDEN);
            assertFalse(state.shouldAttempt(route, false));
        }

        state.onBrowserIdentityChanged();

        assertFalse(state.shouldAttempt(DouyinRouteState.Route.LEGACY, false));
        assertTrue(state.shouldAttempt(DouyinRouteState.Route.DETAIL, false));
        assertFalse(state.shouldAttempt(DouyinRouteState.Route.SIGNED, false));
    }
}
