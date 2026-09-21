# GitHub Releases OTA Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a secure, user-controlled GitHub Releases OTA updater to LinkVideo v0.25 and produce a signed APK plus release-ready metadata and clean source archive.

**Architecture:** Pure Java classes parse and validate `latest.json`, copy and hash downloads, compare APK metadata, compare certificate identities, and map installation outcomes. A small Android integration layer owns HTTPS requests, archive inspection, unknown-source authorization, `PackageInstaller.Session`, and a dedicated update screen reached from Settings.

**Tech Stack:** Android SDK 35, Java 17 source with Android Java 8 bytecode compilation, XML layouts, `HttpURLConnection`, `PackageManager`, `PackageInstaller`, JUnit 4, PowerShell release tooling.

**Spec:** `docs/superpowers/specs/2026-09-21-github-releases-ota-design.md`

## Global Constraints

- Start from the current v0.24 root source and set `versionCode 25`, `versionName 0.25.0`; preserve every historical `v0.*` directory.
- Keep package name `com.linkvideo.parser`, `minSdk 26`, `targetSdk 35`, native Java + XML, and every existing parser/download/preview/batch behavior.
- Fetch only `https://github.com/gudian114/LinkVideo/releases/latest/download/latest.json`; accept only HTTPS redirects and APK URLs.
- Do not store a GitHub Token and do not add a third-party update SDK.
- Require SHA-256, package, version, minimum SDK, and active signing-certificate validation before installation.
- Use the same signing certificate as v0.24; never put the keystore, password, Token, `local.properties`, `.gradle`, `build`, or historical releases in the source ZIP or release upload directory.
- Use Android's system installation confirmation through `PackageInstaller.Session`; never implement silent install or claim automatic rollback.
- Do not upload or publish GitHub assets.
- The checkout has no Git repository metadata, so test checkpoints replace commit steps; do not initialize a repository solely for this feature.

## Review Focus

- A GitHub redirect changing from HTTPS to HTTP must fail before any response body is read; `UpdateManifestParserTest` and `UpdatePolicyTest` pin URL rejection, and `UpdateHttpClient` checks every redirect.
- A server that omits `Content-Length` must still download with indeterminate progress and pass final hash/metadata checks; `DownloadTransferTest` covers unknown length.
- A canceled or failed transfer must close streams and remove its partial file without removing a previously verified APK; `DownloadTransferTest` covers both paths.
- An APK signed with a subset, superset, or different certificate set must fail, including multiple-signer packages; `ApkValidationPolicyTest` covers exact set equality.
- Returning from unknown-source settings after process or Activity recreation must revalidate the saved file rather than trust stored state; `UpdateActivity` routes every resumed install through `verifyDownloadedApk()`.

---

### Task 1: Version Baseline and Strict Update Manifest

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/linkvideo/parser/UpdateManifest.java`
- Create: `app/src/main/java/com/linkvideo/parser/UpdateManifestParser.java`
- Create: `app/src/main/java/com/linkvideo/parser/UpdatePolicy.java`
- Test: `app/src/test/java/com/linkvideo/parser/UpdateManifestParserTest.java`
- Test: `app/src/test/java/com/linkvideo/parser/UpdatePolicyTest.java`

**Interfaces:**
- Produces: immutable `UpdateManifest` with `schemaVersion`, `packageName`, `versionCode`, `versionName`, `minSdk`, `apkUrl`, `sizeBytes`, `sha256`, `releaseNotes`, and `rawJson` accessors.
- Produces: `UpdateManifestParser.parse(String json): UpdateManifest`, throwing `IllegalArgumentException` with a user-safe Chinese reason.
- Produces: `UpdatePolicy.evaluate(UpdateManifest manifest, String expectedPackage, long currentCode, int deviceSdk): UpdatePolicy.Decision` where decision is `LATEST`, `UPDATE_AVAILABLE`, or `UNSUPPORTED_DEVICE`.

- [ ] **Step 1: Write strict manifest parser tests**

Add tests that parse the production-shaped document and assert every field, then independently remove `packageName`, replace `versionCode` with text, set `schemaVersion` to 2, use `http://`, use a negative size, and use a non-64-character hash. Each malformed input must throw `IllegalArgumentException`.

```java
@Test public void parsesCompleteManifest() {
    UpdateManifest value = UpdateManifestParser.parse(validJson(25));
    assertEquals("com.linkvideo.parser", value.packageName());
    assertEquals(25L, value.versionCode());
    assertEquals("0.25.0", value.versionName());
    assertTrue(value.apkUrl().startsWith("https://"));
}

@Test(expected = IllegalArgumentException.class)
public void rejectsHttpApkUrl() {
    UpdateManifestParser.parse(validJson(25).replace("https://github.com", "http://github.com"));
}
```

- [ ] **Step 2: Run the new parser tests and confirm the expected red state**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: compilation fails because `UpdateManifest` and `UpdateManifestParser` do not exist.

- [ ] **Step 3: Implement the immutable model and strict parser**

Use `org.json.JSONObject`, `has`, and `isNull` before getters. Require exact primitive types through guarded getters, trim strings, lowercase the accepted hash, and enforce:

```java
if (schemaVersion != 1) throw invalid("不支持的更新清单版本");
if (!"https".equalsIgnoreCase(URI.create(apkUrl).getScheme())) throw invalid("安装包地址必须使用 HTTPS");
if (!sha256.matches("[0-9a-fA-F]{64}")) throw invalid("SHA-256 格式错误");
if (versionCode <= 0 || minSdk <= 0 || sizeBytes < 0) throw invalid("更新清单数值无效");
```

Store the original JSON in `rawJson` so Activity state can be restored by reparsing rather than Java object serialization.

- [ ] **Step 4: Write version/package/device policy tests**

Cover remote version equal to current, lower than current, greater than current, package mismatch, and `minSdk` above the device SDK.

```java
assertEquals(UpdatePolicy.Decision.LATEST,
        UpdatePolicy.evaluate(manifest(24, 26), "com.linkvideo.parser", 24, 35));
assertEquals(UpdatePolicy.Decision.UPDATE_AVAILABLE,
        UpdatePolicy.evaluate(manifest(25, 26), "com.linkvideo.parser", 24, 35));
assertEquals(UpdatePolicy.Decision.UNSUPPORTED_DEVICE,
        UpdatePolicy.evaluate(manifest(25, 36), "com.linkvideo.parser", 24, 35));
```

- [ ] **Step 5: Implement `UpdatePolicy` and bump the root app version**

Set the root source to:

```gradle
versionCode 25
versionName '0.25.0'
```

`UpdatePolicy.evaluate` must reject a package other than `com.linkvideo.parser` with `IllegalArgumentException`, return `UNSUPPORTED_DEVICE` before download, return `LATEST` for `remote <= current`, and otherwise return `UPDATE_AVAILABLE`.

- [ ] **Step 6: Run the core suite**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: all existing tests plus the parser and policy tests pass.

### Task 2: Cancelable Transfer and SHA-256 Verification

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/CancellationToken.java`
- Create: `app/src/main/java/com/linkvideo/parser/DownloadTransfer.java`
- Create: `app/src/main/java/com/linkvideo/parser/Sha256Verifier.java`
- Test: `app/src/test/java/com/linkvideo/parser/DownloadTransferTest.java`
- Test: `app/src/test/java/com/linkvideo/parser/Sha256VerifierTest.java`

**Interfaces:**
- Produces: `CancellationToken.cancel()`, `isCanceled()`, and `throwIfCanceled()`.
- Produces: `DownloadTransfer.copy(InputStream source, File partial, long expectedBytes, long maximumBytes, CancellationToken token, ProgressListener listener): long`.
- Produces: `Sha256Verifier.digest(File file): String` and `matches(File file, String expected): boolean`.

- [ ] **Step 1: Write transfer failure, cancellation, unknown-size, and limit tests**

Use a `ByteArrayInputStream` for success, an `InputStream` that throws after several bytes for interruption, a listener that calls `token.cancel()` for cancellation, `expectedBytes == 0` for unknown length, and a maximum smaller than the payload for the size cap.

```java
@Test public void failureDeletesPartialFile() throws Exception {
    File part = temporary.newFile("update.part");
    try {
        DownloadTransfer.copy(failingStream(), part, 0, 1024, new CancellationToken(), ignored -> { });
        fail("expected IOException");
    } catch (IOException expected) {
        assertFalse(part.exists());
    }
}
```

- [ ] **Step 2: Run the new tests and confirm red state**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: compilation fails for the three missing classes.

- [ ] **Step 3: Implement streaming transfer with cleanup**

Use a 32 KiB buffer. Before each read and after each write, call `token.throwIfCanceled()`. Track total bytes, fail if it exceeds `maximumBytes`, fail if `expectedBytes > 0` and the completed count differs, flush and close the output, and delete only the provided partial path on any exception or cancellation.

The progress listener receives a small immutable `Progress` value containing `downloadedBytes`, `expectedBytes`, and `percent()`; `percent()` returns `-1` for unknown length.

- [ ] **Step 4: Write SHA-256 vector tests**

Write UTF-8 `abc` and expect `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; assert uppercase expected values match and one altered nibble fails.

- [ ] **Step 5: Implement streaming SHA-256 and constant-time comparison**

Read the file with a 32 KiB buffer, render exactly 64 lowercase hex characters, parse expected hex to bytes, and use `MessageDigest.isEqual(actualBytes, expectedBytes)`.

- [ ] **Step 6: Run the core suite**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: all tests pass and temporary `.part` files are absent after negative cases.

### Task 3: APK Metadata, Certificate, and Installer Result Policies

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ApkDescriptor.java`
- Create: `app/src/main/java/com/linkvideo/parser/ApkValidationPolicy.java`
- Create: `app/src/main/java/com/linkvideo/parser/SignerIdentity.java`
- Create: `app/src/main/java/com/linkvideo/parser/InstallOutcome.java`
- Test: `app/src/test/java/com/linkvideo/parser/ApkValidationPolicyTest.java`
- Test: `app/src/test/java/com/linkvideo/parser/SignerIdentityTest.java`
- Test: `app/src/test/java/com/linkvideo/parser/InstallOutcomeTest.java`

**Interfaces:**
- Produces: immutable `ApkDescriptor(packageName, versionCode, versionName, minSdk, signerDigests)`.
- Produces: `ApkValidationPolicy.validate(UpdateManifest manifest, ApkDescriptor apk, long currentVersionCode, long actualSize): ValidationResult`.
- Produces: `SignerIdentity.sha256(byte[] certificate): String` and `sameActiveSigners(Set<String> installed, Set<String> candidate): boolean`.
- Produces: `InstallOutcome.fromStatus(int packageInstallerStatus, String systemMessage): InstallOutcome.Result` with `SUCCESS`, `CANCELED`, `FAILED`, plus Chinese message.

- [ ] **Step 1: Write APK validation tests**

Create one valid descriptor, then vary one field per test: wrong package, equal version code, lower version code, manifest/APK version-code mismatch, version-name mismatch, min-SDK mismatch, size mismatch when manifest size is positive, and signer mismatch.

```java
assertFalse(ApkValidationPolicy.validate(manifest, apkWithVersion(24), 24, size).valid());
assertEquals("安装包版本没有提高",
        ApkValidationPolicy.validate(manifest, apkWithVersion(24), 24, size).message());
```

- [ ] **Step 2: Write certificate-set tests**

Assert exact one-signer and exact two-signer sets pass; different, subset, and superset sets fail. Verify SHA-256 output length and a known certificate-byte vector.

- [ ] **Step 3: Write install-status mapping tests**

Use the integer values mirrored from `PackageInstaller`: `0` success, `-115` aborted, and a generic negative failure. Assert messages explicitly say current version is unchanged for cancel/failure.

- [ ] **Step 4: Run the new tests and confirm red state**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: compilation fails for missing policy classes.

- [ ] **Step 5: Implement exact validation policies**

Return a `ValidationResult` instead of throwing for a downloaded APK mismatch so the UI can display the exact reason. Validation order is size, package, manifest/APK version identity, version increase, version name, minimum SDK, and signer equality.

`sameActiveSigners` must normalize digest case and require nonempty set equality:

```java
return !installed.isEmpty() && normalize(installed).equals(normalize(candidate));
```

- [ ] **Step 6: Run the core suite**

Run: `powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1`

Expected: all policy and existing tests pass.

### Task 4: HTTPS Client, Archive Inspection, and Package Installer

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/UpdateHttpClient.java`
- Create: `app/src/main/java/com/linkvideo/parser/ApkArchiveInspector.java`
- Create: `app/src/main/java/com/linkvideo/parser/UpdateInstaller.java`
- Create: `app/src/main/java/com/linkvideo/parser/UpdateInstallReceiver.java`
- Create: `app/src/main/java/com/linkvideo/parser/UpdateStateStore.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `work/run-core-tests.ps1`

**Interfaces:**
- Consumes: manifest parser, transfer, hash and APK validation classes from Tasks 1–3.
- Produces: `UpdateHttpClient.fetchManifest(CancellationToken): String` and `downloadApk(UpdateManifest, File, CancellationToken, ProgressListener): long`.
- Produces: `ApkArchiveInspector.inspect(Context, File): ApkDescriptor` and `currentVersionCode(Context): long`.
- Produces: `UpdateInstaller.commit(Context, File): int` returning the package-installer session ID.
- Produces: `UpdateStateStore.saveVerifiedManifest`, `loadVerifiedManifest`, `saveInstallOutcome`, `consumeInstallOutcome`, and `clearVerifiedDownload`.

- [ ] **Step 1: Add Android-only classes to the core runner exclusion list**

Append `UpdateHttpClient.java`, `ApkArchiveInspector.java`, `UpdateInstaller.java`, `UpdateInstallReceiver.java`, `UpdateStateStore.java`, and later `UpdateActivity.java` to the existing `Where-Object` exclusion array. Keep all pure policy classes in the core suite.

- [ ] **Step 2: Implement the HTTPS client**

Use fixed manifest constant:

```java
static final String MANIFEST_URL =
        "https://github.com/gudian114/LinkVideo/releases/latest/download/latest.json";
```

Set 15-second connection and 30-second read timeouts, `User-Agent: LinkVideo/0.25.0`, disable automatic redirects, follow at most five `Location` values, and reject any URL whose scheme is not HTTPS. Limit the manifest to 256 KiB and the APK to 1 GiB. Check cancellation before opening, while copying, and before returning.

- [ ] **Step 3: Implement archive inspection**

For API 33+ call `getPackageArchiveInfo(path, PackageInfoFlags.of(flags))`; otherwise use the legacy overload. For API 28+ use `GET_SIGNING_CERTIFICATES` and `getApkContentsSigners`; for API 26–27 use `GET_SIGNATURES`. Apply the same version/signature extraction to the current installed package and hash every certificate byte array through `SignerIdentity.sha256`.

If Android cannot parse the archive, throw `IllegalArgumentException("无法读取安装包信息")`.

- [ ] **Step 4: Implement state persistence and PackageInstaller session submission**

Store only manifest JSON, verified cache filename, digest, and last install result in private SharedPreferences. Never persist a token or credential. `UpdateInstaller.commit` creates `MODE_FULL_INSTALL`, calls `setAppPackageName("com.linkvideo.parser")`, sets user action required on API 31+, copies the APK into `session.openWrite("base.apk", 0, apk.length())`, calls `fsync`, and commits to an explicit mutable `PendingIntent` targeting `UpdateInstallReceiver`.

- [ ] **Step 5: Implement install result receiver**

On `STATUS_PENDING_USER_ACTION`, read `Intent.EXTRA_INTENT`, add `FLAG_ACTIVITY_NEW_TASK`, and open the system confirmation Activity. On terminal status, map through `InstallOutcome`, persist it, show a Toast, and for failure/cancel open `UpdateActivity` with `NEW_TASK | CLEAR_TOP` so the full message is visible.

- [ ] **Step 6: Register permission and receiver**

Add:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<receiver android:name=".UpdateInstallReceiver" android:exported="false" />
```

No storage permission or exported update component is added.

- [ ] **Step 7: Compile Android Java sources**

Run: `powershell -ExecutionPolicy Bypass -File .\work\build-linkvideo-debug.ps1`

Expected at this checkpoint: core tests, browser scripts, resource processing, Java compilation, Lint and APK assembly pass with the new integration classes even though the UI entry is not yet wired.

### Task 5: Settings Entry and Update Screen State Machine

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/UpdateActivity.java`
- Create: `app/src/main/res/layout/activity_update.xml`
- Modify: `app/src/main/java/com/linkvideo/parser/SettingsActivity.java`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/colors.xml` only if an existing semantic color cannot express error/success text.
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: every update core and integration API from Tasks 1–4.
- Produces: a Settings “检查更新” button and `UpdateActivity` with check, download, cancel, retry, verify, permission, and install states.

- [ ] **Step 1: Add update copy and screen layout**

The screen contains a 64dp header with back button/title, current and latest version labels, package-size label, scrollable release notes, status text, determinate/indeterminate horizontal ProgressBar, a primary button and a secondary cancel/retry button. Use existing `surface`, `text_primary`, `text_secondary`, `accent`, `card_background`, and button styles.

Add exact user-facing messages for invalid manifest, no network, canceled download, hash mismatch, package mismatch, version not increased, signature mismatch, unknown-source denial, install canceled, install failure, and current version unchanged.

- [ ] **Step 2: Wire Settings to the update screen**

Add a full-width “检查更新” button in the Settings card and:

```java
findViewById(R.id.check_update).setOnClickListener(ignored ->
        startActivity(new Intent(this, UpdateActivity.class)));
```

- [ ] **Step 3: Implement check and latest/update-available states**

Use one single-thread `ExecutorService`, main-thread `Handler`, a generation counter, and a `CancellationToken` per task. On entry, consume and display a pending install result; otherwise start checking. Parse remote JSON and use `UpdatePolicy`. UI callbacks must confirm the Activity generation before changing views.

- [ ] **Step 4: Implement download, progress, cancel, retry, and verification**

Download to `cacheDir/updates/LinkVideo-v0.25.apk.part`. Render `Progress.percent() == -1` as indeterminate. On success, check size and SHA-256, inspect the APK, call `ApkValidationPolicy`, atomically rename to `.apk`, persist verified metadata, and enable “安装更新”. On cancellation/failure, show retry and keep the installed app untouched.

- [ ] **Step 5: Implement unknown-source authorization and installation resume**

Before every install attempt:

```java
if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
    startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + getPackageName())), REQUEST_INSTALL_PERMISSION);
    return;
}
```

On return, show a denial message when permission remains false. When true, call `verifyDownloadedApk()` again before `UpdateInstaller.commit`. Do the same from `onResume` after process/state restoration.

- [ ] **Step 6: Save and restore safe state**

Save manifest raw JSON and nonterminal display state in `Bundle`. Cancel live network work in `onDestroy`; do not delete a verified APK. Any restored ready-to-install state must reparse the JSON, reload the cache file, recompute hash, and rerun archive/signature validation.

- [ ] **Step 7: Register the Activity and run Android checks**

Add non-exported `.UpdateActivity` to the manifest. Run:

`powershell -ExecutionPolicy Bypass -File .\work\build-linkvideo-debug.ps1`

Expected: core/browser tests, Android resources, Java compilation, Lint and APK assembly pass.

### Task 6: Full Security Regression and Final APK Evidence

**Files:**
- Modify: `work/build-linkvideo-debug.ps1`
- Create: `work/verify-linkvideo-v0.25.ps1`
- Create: `docs/feature-v0.25.md`
- Modify: `docs/verification.md`

**Interfaces:**
- Consumes: completed v0.25 source.
- Produces: signed root build at `v0.25/LinkVideo-v0.25.apk` and machine-readable verification logs.

- [ ] **Step 1: Point the build output at v0.25 without changing signing credentials**

Change only the release directory/name in `build-linkvideo-debug.ps1`; keep the existing workspace keystore path and injected alias/password behavior unchanged. Copy the built file as `v0.25/LinkVideo-v0.25.apk`.

- [ ] **Step 2: Add APK structure and metadata verification**

Adapt the v0.24 verifier to require all update classes, `activity_update.xml`, the Settings layout, Manifest permission, receiver and Activity. Run `aapt2 dump badging` and assert package `com.linkvideo.parser`, versionCode `25`, versionName `0.25.0`, and sdkVersion `26`.

- [ ] **Step 3: Add signature continuity verification**

Run `apksigner verify --verbose --print-certs` against v0.24 and v0.25. Extract the signer certificate SHA-256 digest and fail unless the two values are identical. Also require verified v1/v2 status appropriate to the existing signing output.

- [ ] **Step 4: Run all requested automated checks once from clean generated outputs**

Run in order:

```powershell
powershell -ExecutionPolicy Bypass -File .\work\run-core-tests.ps1
node .\outputs\LinkVideo\app\src\test\browser\douyin-browser.test.cjs .\work\v0.19-browser-script.js
powershell -ExecutionPolicy Bypass -File .\work\build-linkvideo-debug.ps1
powershell -ExecutionPolicy Bypass -File .\work\verify-linkvideo-v0.25.ps1
```

Record test counts, browser-script count, resource processing, Java compilation, Lint result, APK build result, package metadata, signature verification and certificate equality. Do not call these device tests.

- [ ] **Step 5: Document feature behavior and verification boundary**

`docs/feature-v0.25.md` describes Settings flow, validation order, unknown-source permission and system installer. `docs/verification.md` lists exact automatic results and a separate “未进行真机验证” section for permission, cancel/failure/success installer flows and GitHub live redirects.

### Task 7: Release-Ready Delivery Directory

**Files:**
- Create: `work/package-linkvideo-v0.25.ps1`
- Create: `work/verify-linkvideo-v0.25-archive.ps1`
- Create: `v0.25/latest.json`
- Create: `v0.25/RELEASE_NOTES.md`
- Create: `v0.25/DEVICE_TEST_CHECKLIST.md`
- Create: `v0.25/verification.md`
- Create: `v0.25/release/` contents generated by the packaging script.

**Interfaces:**
- Consumes: final APK and verified source.
- Produces: the exact uploadable artifacts requested by the user without publishing them.

- [ ] **Step 1: Generate production `latest.json` from the built APK**

Calculate actual byte length and lowercase SHA-256, then write the JSON from those values rather than hand-editing them:

```powershell
$apk = Get-Item -LiteralPath $apkPath
$apkHash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    schemaVersion = 1
    packageName = 'com.linkvideo.parser'
    versionCode = 25
    versionName = '0.25.0'
    minSdk = 26
    apkUrl = 'https://github.com/gudian114/LinkVideo/releases/download/v0.25.0/LinkVideo-v0.25.apk'
    sizeBytes = $apk.Length
    sha256 = $apkHash
    releaseNotes = '新增基于 GitHub Releases 的应用内安全更新功能。'
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath $latestJsonPath -Encoding utf8
```

The script immediately reparses the generated result through `UpdateManifestParser` or an equivalent release verifier.

- [ ] **Step 2: Build the clean source directory and ZIP**

Copy only `app/src`, Gradle project files, wrapper, README and `docs` to `v0.25/LinkVideo-v0.25-source/`, then create `LinkVideo-v0.25-source.zip`. Fail if any ZIP entry matches `keystore|local.properties|token|password|(^|/)(.gradle|.idea|build|v0.\d+)(/|$)` or contains an APK.

- [ ] **Step 3: Write release notes and device checklist**

Release notes list the Settings entry, version display, cancel/retry, SHA/package/version/signature validation, unknown-source permission and system installer. The checklist leaves unchecked boxes for Android 8+, recent Android, permission denial, cancellation, failed install, successful same-signature upgrade, process recreation and live GitHub redirect tests.

- [ ] **Step 4: Assemble the upload directory and checksums**

Place only these files in `v0.25/release/`:

```text
LinkVideo-v0.25.apk
latest.json
LinkVideo-v0.25-source.zip
SHA256SUMS.txt
RELEASE_NOTES.md
DEVICE_TEST_CHECKLIST.md
verification.md
```

`SHA256SUMS.txt` contains SHA-256 for the APK, JSON, ZIP, notes, checklist and verification record.

- [ ] **Step 5: Verify the archive and release set**

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\work\verify-linkvideo-v0.25-archive.ps1
Get-FileHash .\outputs\LinkVideo\v0.25\release\* -Algorithm SHA256
```

Expected: clean ZIP inspection passes, release allowlist is exact, every checksum matches, `latest.json` size/hash match the APK, and no key/token/password/cache file appears.

- [ ] **Step 6: Write the future release procedure**

Append to `v0.25/README.md`: increase version, build with the same signing certificate, calculate size/hash, generate `latest.json`, create a draft GitHub Release with matching tag, upload APK and `latest.json` together, verify locally, publish, verify the two public URLs, and complete the device checklist. State explicitly that this task did not upload or publish anything.

## Plan Self-Review

- Spec coverage: every design section maps to Tasks 1–7, including fixed URL, progress/cancel/retry, every validation gate, unknown-source permission, installer result mapping, signature continuity, packaging and future release steps.
- Placeholder scan: no implementation step relies on an undefined action; example size/hash values are explicitly generated and replaced by the packaging script rather than left in deliverables.
- Type consistency: `UpdateManifest`, `CancellationToken`, `DownloadTransfer.Progress`, `ApkDescriptor`, `ValidationResult`, `InstallOutcome`, `UpdateHttpClient`, `ApkArchiveInspector`, `UpdateInstaller`, and `UpdateStateStore` names and consumers are consistent across tasks.
- Review focus: HTTPS downgrade, unknown content length, partial cleanup, exact multi-signer comparison, and state restoration each have an owning implementation step and test or explicit Android verification boundary.
- Device boundary: automated tests validate policies and status mapping; actual system authorization and installation remain clearly listed for real-device validation.
