# LinkVideo v0.27 Douyin Parser Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Douyin parsing prefer a reusable verified identity and avoid repeated known-failing native routes, while showing verification promptly when a browser is truly required.

**Architecture:** A pure-Java native route resolver owns typed outcomes, endpoint cooldowns and total-budget decisions. `HttpClient` exposes browser identity without persisting it in parsed results; Android integration reads and flushes `CookieManager`, while WebView visibility becomes identity- and failure-aware.

**Tech Stack:** Android SDK 35, native Java + XML, WebView/CookieManager, HttpURLConnection, JUnit 4, Node browser-script tests, PowerShell delivery tooling.

**Spec:** `docs/superpowers/specs/2026-09-26-douyin-parser-performance-design.md`

## Global Constraints

- Preserve every `v0.26` and earlier directory; create independent `v0.27` delivery artifacts.
- Keep package `com.linkvideo.parser`, minSdk 26, targetSdk 35, Java/XML stack and OTA behavior.
- Do not add concurrent Douyin requests, background clipboard-link access, tokens, unofficial remote services, silent installation or plaintext cookie persistence.
- Only load a concrete Douyin work page after the user starts parsing and native/page-data routes cannot return the requested work.
- The checkout has no Git metadata, so RED/GREEN logs and versioned artifacts replace commit checkpoints.

## Review Focus

- A stale browser cookie must not permanently suppress native routes or hide a required verification page.
- A network timeout must not be classified as a hard endpoint denial.
- A new verified browser identity reopens ordinary detail once while preserving known legacy/signed failures.
- Redirects to non-Douyin hosts must never receive Douyin Cookie headers.
- Cancellation and Activity destruction must still dispose the single WebView session.

---

### Task 1: Typed native outcomes and endpoint cooldown

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/DouyinRouteOutcome.java`
- Create: `app/src/main/java/com/linkvideo/parser/DouyinRouteState.java`
- Create: `app/src/main/java/com/linkvideo/parser/DouyinTiming.java`
- Test: `app/src/test/java/com/linkvideo/parser/DouyinRouteStateTest.java`
- Test: `app/src/test/java/com/linkvideo/parser/DouyinTimingTest.java`

**Interfaces:**
- `DouyinRouteOutcome` values: `SUCCESS`, `FORBIDDEN`, `STATUS_11110`, `EMPTY`, `NETWORK_FAILURE`, `SKIPPED`.
- `DouyinRouteState.shouldAttempt(Route, boolean identityChanged)`, `record(Route, Outcome)`, and `onBrowserIdentityChanged()`.
- `DouyinTiming.check(String stage)` and `remainingMillis()` use a supplied monotonic clock and a 90-second default budget.

- [ ] Write tests proving route-specific 15-minute cooldown, forced retry after identity change, expiry, reset, and no cooldown for network/empty outcomes.
- [ ] Run `work/run-core-tests.ps1` and confirm compilation fails because the new types do not exist.
- [ ] Implement the smallest thread-safe state and deadline classes.
- [ ] Re-run the core suite and confirm all tests pass.

### Task 2: Browser identity bridge without serializing secrets

**Files:**
- Modify: `app/src/main/java/com/linkvideo/parser/HttpClient.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ParseDiagnostics.java`
- Create: `app/src/main/java/com/linkvideo/parser/SerializableHeaders.java`
- Modify: `app/src/main/java/com/linkvideo/parser/MediaStream.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageResource.java`
- Test: `app/src/test/java/com/linkvideo/parser/SerializableHeadersTest.java`
- Modify test: `app/src/test/java/com/linkvideo/parser/ParseDiagnosticsTest.java`
- Modify test: `app/src/test/java/com/linkvideo/parser/BatchDraftStoreTest.java`

**Interfaces:**
- `HttpClient.browserCookies(String origin)`, `flushBrowserCookies()`, and `douyinRouteState()` default to no identity/no-op/no cooldown.
- `ParseDiagnostics` delegates identity methods without appending values.
- Serializable result headers remove cookie and authorization values case-insensitively.

- [ ] Write tests with a unique cookie canary proving stream/image construction and serialized batch drafts contain no secret value.
- [ ] Write a diagnostics wrapper test proving browser cookies are delegated but never included in the report.
- [ ] Run the targeted tests and observe failure against current constructors/wrapper.
- [ ] Implement header sanitization and identity delegation, then run the complete core suite.

### Task 3: Native Douyin route coordinator

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/DouyinNativeResolver.java`
- Modify: `app/src/main/java/com/linkvideo/parser/VideoParser.java`
- Test: `app/src/test/java/com/linkvideo/parser/DouyinNativeResolverTest.java`
- Modify test: `app/src/test/java/com/linkvideo/parser/DouyinSessionTest.java`
- Modify test: `app/src/test/java/com/linkvideo/parser/DouyinBrowserFallbackTest.java`

**Interfaces:**
- Resolver receives current cookies and serial `Calls` for legacy detail, ordinary detail, anonymous registration and signed detail.
- Resolver returns item/cookie state and records typed outcomes through the injected `DouyinRouteState`.
- `VideoParser` merges `browserCookies("https://www.douyin.com/")` before native API attempts, skips an exact duplicate share page, and falls back to WebView only after resolver failure.

- [ ] Add tests that a valid browser cookie makes ordinary detail succeed with zero browser calls, an exact canonical landing page is fetched once, an unchanged registration does not repeat ordinary detail, and cooldown skips hard-failed routes on the next link.
- [ ] Run the new tests and confirm expected failures/call-count mismatches.
- [ ] Implement coordinator integration, preserving cancellation and current item-ID checks.
- [ ] Remove Cookie from Douyin media/image result headers and update the browser fallback expectation.
- [ ] Run all core tests; confirm Bilibili and Kuaishou tests remain green.

### Task 4: Prompt and event-driven WebView fallback

**Files:**
- Modify: `app/src/main/java/com/linkvideo/parser/DouyinPageVisibility.java`
- Modify: `app/src/main/java/com/linkvideo/parser/AndroidHttpClient.java`
- Modify test: `app/src/test/java/com/linkvideo/parser/DouyinPageVisibilityTest.java`
- Modify: `app/src/test/browser/douyin-browser.test.cjs` only if the production script contract changes.

**Interfaces:**
- `DouyinPageVisibility(showInitially, hasIdentity, now)` reveals immediately for cold sessions, at four seconds for warm sessions, and immediately after `requireInteraction()`.
- `AndroidHttpClient` reads stable CookieManager identity, calls `flush()` when a browser session finishes, exposes shared production cooldown state, and caps WebView waiting to the parse budget that remains.

- [ ] Replace the old 8/16-second tests with cold-immediate, warm-four-second, explicit-failure and existing lifecycle/deadline tests; observe RED.
- [ ] Implement visibility behavior and semantic reveal on browser 403/script error/request timeout.
- [ ] Add identity bridge and flush/reset behavior without logging cookie values.
- [ ] Run core and Node browser-script tests.

### Task 5: Exact-origin runtime Cookie headers

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/DouyinHeaderPolicy.java`
- Create: `app/src/main/java/com/linkvideo/parser/DouyinRuntimeHeaders.java`
- Modify: `app/src/main/java/com/linkvideo/parser/VideoDownloader.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageLoader.java`
- Modify: `app/src/main/java/com/linkvideo/parser/CoverLoader.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageDownloader.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageGalleryActivity.java`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageViewerActivity.java`
- Modify: `app/src/main/java/com/linkvideo/parser/MainActivity.java`
- Modify: `app/src/main/java/com/linkvideo/parser/BatchVideoCard.java`
- Test: `app/src/test/java/com/linkvideo/parser/DouyinHeaderPolicyTest.java`

**Interfaces:**
- Pure policy accepts only HTTPS `douyin.com` and subdomains and returns a copied header map with a runtime cookie; all other origins receive the sanitized base map.
- Android wrapper supplies `CookieManager.getCookie(actualRequestUrl)` only to the policy.

- [ ] Write tests for exact host, subdomain, lookalike host, HTTP URL, user-info URL, and redirect-host recomputation; observe RED.
- [ ] Implement policy and wrapper.
- [ ] Apply runtime headers only to redirect-aware in-app download/image connections. External browser and system-player boundaries use sanitized stored headers.
- [ ] Run core tests and Android Java compilation.

### Task 6: Version, diagnostics, regression and build

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/test/java/com/linkvideo/parser/DownloadDestinationContractTest.java`
- Create or modify: `work/build-linkvideo-v0.27.ps1`
- Create or modify: `work/verify-linkvideo-v0.27.ps1`

**Interfaces:**
- Root source uses versionCode 27/versionName 0.27.0.
- Build script signs with the existing debug/release-continuity key and writes only to `v0.27`.

- [ ] Update the version contract test first and observe RED.
- [ ] Bump version and add v0.27-only build/verification scripts without changing the v0.26 scripts or files.
- [ ] Run 100% of core JUnit tests, Node browser tests, Android resource processing, production Java compilation, lint and signed APK assembly.
- [ ] Verify package/version/minSdk/targetSdk, v2 signature continuity with v0.26, required classes/resources, and absence of cookie canaries.

### Task 7: Independent v0.27 delivery

**Files:**
- Create: `v0.27/release/LinkVideo-v0.27.apk`
- Create: `v0.27/release/latest.json`
- Create: `v0.27/release/LinkVideo-v0.27-source.zip`
- Create: `v0.27/release/SHA256SUMS.txt`
- Create: `v0.27/release/README.md`
- Create: `v0.27/release/RELEASE_NOTES.md`
- Create: `v0.27/release/DEVICE_TEST_CHECKLIST.md`
- Create: `v0.27/release/verification.md`

- [ ] Package a clean source archive excluding `.gradle`, build caches, APKs, historical releases, local properties, credentials and keystores.
- [ ] Generate SHA-256 and `latest.json` for `v0.27.0` using the fixed GitHub repository URL.
- [ ] Verify the source ZIP reopens, excludes sensitive/build files, and contains the exact tested source.
- [ ] Record automated evidence separately from the uncompleted real-device scenarios.
- [ ] Do not upload or publish any artifact.
