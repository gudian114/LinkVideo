package com.linkvideo.parser;

import org.json.JSONObject;

/** Serial native-route coordinator. It owns ordering, not HTTP or JSON extraction. */
final class DouyinNativeResolver {
    interface Calls {
        Attempt legacy(String cookies) throws Exception;
        Attempt detail(String cookies) throws Exception;
        String register() throws Exception;
        Attempt signed(String cookies) throws Exception;
    }

    static final class Attempt {
        final DouyinRouteOutcome outcome;
        final JSONObject item;

        private Attempt(DouyinRouteOutcome outcome, JSONObject item) {
            this.outcome = outcome;
            this.item = item;
        }

        static Attempt success(JSONObject item) {
            if (item == null) throw new IllegalArgumentException("item");
            return new Attempt(DouyinRouteOutcome.SUCCESS, item);
        }

        static Attempt of(DouyinRouteOutcome outcome) {
            if (outcome == null || outcome == DouyinRouteOutcome.SUCCESS)
                throw new IllegalArgumentException("outcome");
            return new Attempt(outcome, null);
        }
    }

    static final class Result {
        final JSONObject item;
        final String cookies;

        Result(JSONObject item, String cookies) {
            this.item = item;
            this.cookies = cookies == null ? "" : cookies;
        }
    }

    private interface Request { Attempt run() throws Exception; }

    private final DouyinRouteState state;
    private final DouyinTiming timing;

    DouyinNativeResolver(DouyinRouteState state, DouyinTiming timing) {
        this.state = state == null ? DouyinRouteState.disabled() : state;
        this.timing = timing == null ? DouyinTiming.start() : timing;
    }

    Result resolve(String initialCookies, Calls calls) throws Exception {
        String cookies = HttpFetcher.mergeCookies("", initialCookies);
        if (!cookies.isEmpty()) {
            String current = cookies;
            Attempt legacy = request(DouyinRouteState.Route.LEGACY, false, "旧版详情",
                    () -> calls.legacy(current));
            if (legacy.item != null) return new Result(legacy.item, cookies);
            Attempt detail = request(DouyinRouteState.Route.DETAIL, false, "普通详情",
                    () -> calls.detail(current));
            if (detail.item != null) return new Result(detail.item, cookies);
        }

        if (!DouyinCookieIdentity.hasStableIdentity(cookies)) {
            timing.check("匿名身份");
            String registered = calls.register();
            String merged = HttpFetcher.mergeCookies(cookies, registered);
            boolean identityChanged = DouyinCookieIdentity.changed(cookies, merged);
            cookies = merged;
            if (identityChanged) {
                String current = cookies;
                Attempt detail = request(DouyinRouteState.Route.DETAIL, true, "身份后详情",
                        () -> calls.detail(current));
                if (detail.item != null) return new Result(detail.item, cookies);
            }
        }

        String current = cookies;
        Attempt signed = request(DouyinRouteState.Route.SIGNED, false, "签名详情",
                () -> calls.signed(current));
        return new Result(signed.item, cookies);
    }

    private Attempt request(DouyinRouteState.Route route, boolean identityChanged,
                            String stage, Request request) throws Exception {
        timing.check(stage);
        if (!state.shouldAttempt(route, identityChanged)) return Attempt.of(DouyinRouteOutcome.SKIPPED);
        Attempt attempt = request.run();
        timing.check(stage + "完成");
        if (attempt == null) attempt = Attempt.of(DouyinRouteOutcome.EMPTY);
        state.record(route, attempt.outcome);
        return attempt;
    }
}
