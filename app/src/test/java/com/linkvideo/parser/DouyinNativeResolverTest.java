package com.linkvideo.parser;

import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DouyinNativeResolverTest {
    @Test public void existingTtwidSkipsAnonymousRegistration() throws Exception {
        Calls calls = new Calls();
        calls.registration = "ttwid=must-not-replace-browser";
        calls.detailOutcomes = new DouyinRouteOutcome[] { DouyinRouteOutcome.FORBIDDEN };

        DouyinNativeResolver.Result result = resolver().resolve("ttwid=existing", calls);

        assertNull(result.item);
        assertEquals(1, calls.detailCalls);
        assertEquals(0, calls.registrationCalls);
        assertTrue(result.cookies.contains("ttwid=existing"));
        assertFalse(result.cookies.contains("must-not-replace-browser"));
    }

    @Test public void changedIdentityCanRetryAJustBlockedDetailRoute() throws Exception {
        Calls calls = new Calls();
        calls.registration = "ttwid=new";
        calls.detailOutcomes = new DouyinRouteOutcome[] {
                DouyinRouteOutcome.FORBIDDEN, DouyinRouteOutcome.SUCCESS };

        DouyinNativeResolver.Result result = resolver().resolve("msToken=old", calls);

        assertEquals("target", result.item.optString("aweme_id"));
        assertEquals(2, calls.detailCalls);
        assertTrue(result.cookies.contains("ttwid=new"));
    }

    @Test public void hardFailuresAreSkippedForTheNextLinkButNetworkFailuresAreNot() throws Exception {
        AtomicLong now = new AtomicLong();
        DouyinRouteState state = new DouyinRouteState(now::get, 900_000L);
        DouyinNativeResolver resolver = new DouyinNativeResolver(state,
                new DouyinTiming(System::nanoTime, 90_000L));
        Calls first = new Calls();
        first.legacyOutcome = DouyinRouteOutcome.STATUS_11110;
        first.detailOutcomes = new DouyinRouteOutcome[] { DouyinRouteOutcome.FORBIDDEN };
        first.signedOutcome = DouyinRouteOutcome.NETWORK_FAILURE;
        resolver.resolve("ttwid=existing", first);

        Calls second = new Calls();
        resolver.resolve("ttwid=existing", second);

        assertEquals(0, second.legacyCalls);
        assertEquals(0, second.detailCalls);
        assertEquals(1, second.signedCalls);
    }

    private static DouyinNativeResolver resolver() {
        return new DouyinNativeResolver(new DouyinRouteState(() -> 0L, 900_000L),
                new DouyinTiming(System::nanoTime, 90_000L));
    }

    private static final class Calls implements DouyinNativeResolver.Calls {
        int legacyCalls, detailCalls, registrationCalls, signedCalls;
        String registration = "";
        DouyinRouteOutcome legacyOutcome = DouyinRouteOutcome.EMPTY;
        DouyinRouteOutcome[] detailOutcomes = { DouyinRouteOutcome.EMPTY };
        DouyinRouteOutcome signedOutcome = DouyinRouteOutcome.EMPTY;

        @Override public DouyinNativeResolver.Attempt legacy(String cookies) {
            legacyCalls++;
            return attempt(legacyOutcome);
        }

        @Override public DouyinNativeResolver.Attempt detail(String cookies) {
            DouyinRouteOutcome value = detailOutcomes[Math.min(detailCalls, detailOutcomes.length - 1)];
            detailCalls++;
            return attempt(value);
        }

        @Override public String register() {
            registrationCalls++;
            return registration;
        }

        @Override public DouyinNativeResolver.Attempt signed(String cookies) {
            signedCalls++;
            return attempt(signedOutcome);
        }

        private static DouyinNativeResolver.Attempt attempt(DouyinRouteOutcome outcome) {
            return outcome == DouyinRouteOutcome.SUCCESS
                    ? DouyinNativeResolver.Attempt.success(new JSONObject().put("aweme_id", "target"))
                    : DouyinNativeResolver.Attempt.of(outcome);
        }
    }
}
