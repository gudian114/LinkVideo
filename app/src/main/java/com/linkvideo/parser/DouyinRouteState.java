package com.linkvideo.parser;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Process-local health state for Douyin endpoints. It never stores request data or cookies. */
final class DouyinRouteState {
    enum Route { LEGACY, DETAIL, SIGNED }

    private final LongSupplier clock;
    private final long cooldownMillis;
    private final Map<Route, Long> blockedUntil = new EnumMap<>(Route.class);

    DouyinRouteState(LongSupplier clock, long cooldownMillis) {
        if (clock == null) throw new IllegalArgumentException("clock");
        this.clock = clock;
        this.cooldownMillis = Math.max(0, cooldownMillis);
    }

    static DouyinRouteState disabled() {
        return new DouyinRouteState(System::currentTimeMillis, 0);
    }

    synchronized boolean shouldAttempt(Route route, boolean identityChanged) {
        if (identityChanged) return true;
        Long until = blockedUntil.get(route);
        if (until == null) return true;
        if (clock.getAsLong() >= until) {
            blockedUntil.remove(route);
            return true;
        }
        return false;
    }

    synchronized void record(Route route, DouyinRouteOutcome outcome) {
        boolean hardFailure = outcome == DouyinRouteOutcome.FORBIDDEN
                || route == Route.LEGACY && outcome == DouyinRouteOutcome.STATUS_11110;
        if (!hardFailure || cooldownMillis == 0) {
            if (outcome == DouyinRouteOutcome.SUCCESS) blockedUntil.remove(route);
            return;
        }
        long now = clock.getAsLong();
        long until = now > Long.MAX_VALUE - cooldownMillis
                ? Long.MAX_VALUE : now + cooldownMillis;
        blockedUntil.put(route, until);
    }

    synchronized void onBrowserIdentityChanged() {
        blockedUntil.remove(Route.DETAIL);
    }
}
