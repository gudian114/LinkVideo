# 拾链 v0.10 图文解析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为拾链增加抖音、快手公开图文解析、双列自适应图片列表、应用内全屏查看和逐张图片下载，并生成独立 v0.10 APK 与源码包。

**Architecture:** `VideoParser` 返回统一 `ParsedResult`，具体结果为现有 `VideoResult` 或新增 `ImagePostResult`。主页面只负责分流；图片列表、全屏查看、图片加载和下载分别由独立类负责，现有视频预览和下载路径不改。

**Tech Stack:** 原生 Android Java、API 26–35、`HttpURLConnection`、`org.json`、MediaStore、JUnit 4；不增加第三方运行时依赖。

**Spec:** `docs/superpowers/specs/2026-09-15-image-posts-design.md`

## Global Constraints

- v0.10 只增加抖音和快手图文；不解析哔哩哔哩图文。
- 视频结果继续使用现有主页面、播放器、下载和封面流程。
- 图文结果自动进入独立双列页面，图片保持平台顺序且按自身比例显示。
- 每张图片下方只显示纵向的 `下载图片` 绿色按钮和 `查看图片` 蓝色按钮。
- `查看图片` 必须在应用内全屏打开，不调用外部浏览器。
- 仅处理公开、无需登录或验证的 HTTPS 资源。
- Android 8.0 / API 26 为最低版本；不添加 AndroidX 或其他第三方 UI/图片库。
- 不上传 GitHub；每完成一个任务就在 `v0.10/progress.md` 记录检查点，最终产物放入独立 `v0.10` 文件夹。

---

## File Structure

**Create**

- `app/src/main/java/com/linkvideo/parser/ParsedResult.java`：视频与图文共同结果接口。
- `app/src/main/java/com/linkvideo/parser/ImageResource.java`：一张图片的候选 URL、请求头和宽高，可通过 Intent 序列化。
- `app/src/main/java/com/linkvideo/parser/ImagePostResult.java`：图文元数据与有序图片列表。
- `app/src/main/java/com/linkvideo/parser/ImageAspect.java`：纯 Java 的图片显示比例计算。
- `app/src/main/java/com/linkvideo/parser/ImageFormat.java`：通过响应类型、URL 和文件签名判断图片格式。
- `app/src/main/java/com/linkvideo/parser/ImageLoader.java`：HTTPS 图片请求、候选地址回退、重定向与 20 MB 上限。
- `app/src/main/java/com/linkvideo/parser/ImageDownloader.java`：将单张图片保存至 `Pictures/拾链`。
- `app/src/main/java/com/linkvideo/parser/ImageGalleryActivity.java`：双列图文页面与单卡片操作。
- `app/src/main/java/com/linkvideo/parser/ImageViewerActivity.java`：应用内全屏图片查看。
- `app/src/main/res/layout/activity_image_gallery.xml`：标题和可滚动双列容器。
- `app/src/main/res/layout/view_image_card.xml`：图片、状态和两个纵向按钮。
- `app/src/main/res/layout/activity_image_viewer.xml`：深色全屏图片、加载与错误状态。
- `app/src/test/java/com/linkvideo/parser/ImageResourceTest.java`
- `app/src/test/java/com/linkvideo/parser/ImageAspectTest.java`
- `app/src/test/java/com/linkvideo/parser/ImageFormatTest.java`
- `app/src/test/java/com/linkvideo/parser/ImageLoaderTest.java`

**Modify**

- `app/src/main/java/com/linkvideo/parser/VideoResult.java`：实现 `ParsedResult`。
- `app/src/main/java/com/linkvideo/parser/VideoParser.java`：返回统一结果并解析抖音/快手图片字段。
- `app/src/main/java/com/linkvideo/parser/MainActivity.java`：视频与图文结果分流。
- `app/src/main/AndroidManifest.xml`：注册图片列表和全屏查看 Activity。
- `app/src/main/res/values/strings.xml`：图文页面、按钮、加载和下载文本。
- `app/src/main/res/values/styles.xml`：图片按钮样式与全屏主题。
- `app/src/main/res/values/colors.xml`：图片占位与查看器背景颜色。
- `app/build.gradle`：`versionCode 10`、`versionName '0.10.0'`。
- `work/run-core-tests.ps1`：排除新增 Android Activity 和下载器，继续运行纯 JVM 测试。
- `work/build-linkvideo-debug.ps1`：输出目标改为 `v0.10/LinkVideo-v0.10-debug.apk`。
- `README.md`、`docs/verification.md`：记录新能力和验证边界。

---

### Task 1: 统一解析结果与图片资源模型

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ParsedResult.java`
- Create: `app/src/main/java/com/linkvideo/parser/ImageResource.java`
- Create: `app/src/main/java/com/linkvideo/parser/ImagePostResult.java`
- Modify: `app/src/main/java/com/linkvideo/parser/VideoResult.java`
- Create: `app/src/test/java/com/linkvideo/parser/ImageResourceTest.java`
- Modify: `app/build.gradle`
- Modify: `work/build-linkvideo-debug.ps1`
- Create: `v0.10/progress.md`

**Interfaces:**
- Produces: `ParsedResult.Kind { VIDEO, IMAGE_POST }`
- Produces: `ParsedResult.kind()`, `platform()`, `title()`, `author()`, `sourceUrl()`
- Produces: `ImageResource(List<String> urls, Map<String,String> headers, int width, int height)`
- Produces: `ImagePostResult(String platform, String title, String author, String sourceUrl, String note, List<ImageResource> images)`

- [ ] **Step 1: Write the failing model tests**

```java
@Test public void imageResourceKeepsOrderedUniqueHttpsCandidates() {
    ImageResource image = new ImageResource(Arrays.asList(
            "https://img.example/a.jpg", "", "http://img.example/no.jpg",
            "https://img.example/a.jpg", "https://img.example/b.webp"),
            Collections.singletonMap("Referer", "https://www.douyin.com/note/1"), 1080, 1440);
    assertEquals(Arrays.asList("https://img.example/a.jpg", "https://img.example/b.webp"), image.urls);
    assertEquals(1080, image.width);
    assertEquals(1440, image.height);
}

@Test public void imagePostIsImmutableAndIdentifiesItsKind() {
    List<ImageResource> source = new ArrayList<>();
    source.add(new ImageResource(Collections.singletonList("https://img.example/1.jpg"),
            Collections.emptyMap(), 0, 0));
    ImagePostResult result = new ImagePostResult("抖音", "文案", "作者",
            "https://www.douyin.com/note/1", "", source);
    source.clear();
    assertEquals(ParsedResult.Kind.IMAGE_POST, result.kind());
    assertEquals(1, result.images.size());
}
```

- [ ] **Step 2: Run the core tests and verify RED**

Run: `powershell -ExecutionPolicy Bypass -File work/run-core-tests.ps1`

Expected: compilation fails because `ImageResource`, `ImagePostResult`, and `ParsedResult` do not exist.

- [ ] **Step 3: Implement the minimal model**

```java
public interface ParsedResult {
    enum Kind { VIDEO, IMAGE_POST }
    Kind kind();
    String platform();
    String title();
    String author();
    String sourceUrl();
}
```

`ImageResource` and `ImagePostResult` implement `Serializable`, copy input collections into unmodifiable `ArrayList`/`LinkedHashMap`, reject empty result lists, and expose the exact fields listed above. `VideoResult` implements `ParsedResult`, returns `Kind.VIDEO`, and maps accessor methods to its existing public fields without changing constructors.

Change existing parser tests that assign `VideoParser.parse(...)` directly to `VideoResult` to use an explicit `(VideoResult)` cast; this is the required compile-time adaptation to the new common return type.

- [ ] **Step 4: Re-run core tests and verify GREEN**

Run: `powershell -ExecutionPolicy Bypass -File work/run-core-tests.ps1`

Expected: all existing tests plus the new model tests pass.

- [ ] **Step 5: Set v0.10 identity and save checkpoint**

Set `versionCode 10` and `versionName '0.10.0'`. Change `work/build-linkvideo-debug.ps1` so its release directory and APK filename are `v0.10` and `LinkVideo-v0.10-debug.apk`, preventing any intermediate build from overwriting v0.9. Create `v0.10/progress.md` and append `Task 1 complete: ParsedResult/ImageResource/ImagePostResult model tests pass.`

---

### Task 2: 抖音图文解析

**Files:**
- Modify: `app/src/main/java/com/linkvideo/parser/VideoParser.java`
- Modify: `app/src/test/java/com/linkvideo/parser/VideoParserTest.java`

**Interfaces:**
- Consumes: `ImageResource`, `ImagePostResult`, `ParsedResult`
- Produces: `public ParsedResult parse(String text)`
- Produces: package-private `static List<ImageResource> douyinImages(JSONObject item, Map<String,String> headers)`

- [ ] **Step 1: Add failing fixtures for both Douyin image schemas**

```java
@Test public void parsesDouyinLegacyImagePostInOriginalOrder() throws Exception {
    String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"图文\","
            + "\"author\":{\"nickname\":\"小明\"},\"images\":["
            + "{\"url_list\":[\"https://img.example/1-small.jpg\",\"https://img.example/1.jpg\"],\"width\":1080,\"height\":1440},"
            + "{\"url_list\":[\"https://img.example/2.jpg\"],\"width\":1440,\"height\":1080}]}";
    String body = "window._ROUTER_DATA=" + item;
    FakeHttp http = new FakeHttp().reply("douyin.com",
            "https://www.douyin.com/note/7312345678901234567", body);
    ParsedResult parsed = new VideoParser(http).parse("https://www.douyin.com/note/7312345678901234567");
    ImagePostResult result = (ImagePostResult) parsed;
    assertEquals(Arrays.asList("https://img.example/1.jpg", "https://img.example/2.jpg"),
            Arrays.asList(result.images.get(0).primaryUrl(), result.images.get(1).primaryUrl()));
    assertEquals(1080, result.images.get(0).width);
    assertEquals(1440, result.images.get(0).height);
}

@Test public void parsesDouyinImagePostInfoAndDeduplicatesImages() throws Exception {
    String item = "{\"aweme_id\":\"7312345678901234567\",\"desc\":\"新版图文\","
            + "\"image_post_info\":{\"images\":["
            + "{\"display_image\":{\"url_list\":[\"https://img.example/a.jpg\"],\"width\":1000,\"height\":1500}},"
            + "{\"display_image\":{\"url_list\":[\"https://img.example/a.jpg\"]}}]}}";
    FakeHttp http = new FakeHttp().reply("douyin.com",
            "https://www.douyin.com/note/7312345678901234567", "window._ROUTER_DATA=" + item);
    ImagePostResult result = (ImagePostResult) new VideoParser(http)
            .parse("https://www.douyin.com/note/7312345678901234567");
    assertEquals(1, result.images.size());
}
```

Also add a regression fixture proving a normal `video.play_addr` still returns `VideoResult`.

- [ ] **Step 2: Run only `VideoParserTest` and verify RED**

Run the same core test script, or compile then run `org.junit.runner.JUnitCore com.linkvideo.parser.VideoParserTest` using the classpath in `work/run-core-tests.ps1`.

Expected: the new fixtures fail because `findDouyinVideo` requires `video.play_addr` and the parser requires video streams.

- [ ] **Step 3: Implement Douyin result selection**

Change the recursive lookup to accept the matching `aweme_id` when it contains any of `video`, non-empty `images`, or non-empty `image_post_info.images`. Build media headers before media selection. Extract legacy candidates from `images[*].url_list`; extract new candidates from `image_post_info.images[*].display_image.url_list`; prefer the last valid HTTPS candidate while retaining all valid candidates for fallback. Deduplicate resources by primary URL.

```java
List<ImageResource> images = douyinImages(item, imageHeaders);
if (!images.isEmpty()) {
    return new ImagePostResult("抖音", item.optString("desc", "未命名图文"),
            author == null ? "" : author.optString("nickname", ""),
            resolved, "公开分享页图文解析结果；若地址过期，请重新解析。", images);
}
```

Only call `requireStreams` when no image list was found.

- [ ] **Step 4: Re-run core tests and verify GREEN**

Expected: both Douyin image schemas and all Douyin video fixtures pass.

- [ ] **Step 5: Save checkpoint**

Append the passing Douyin fixture count and schema names to `v0.10/progress.md`.

---

### Task 3: 快手图集解析

**Files:**
- Modify: `app/src/main/java/com/linkvideo/parser/VideoParser.java`
- Modify: `app/src/test/java/com/linkvideo/parser/VideoParserTest.java`

**Interfaces:**
- Consumes: `ImageResource`, `ImagePostResult`
- Produces: package-private `static List<ImageResource> kuaishouImages(JSONObject photo, JSONObject root, String photoId, Map<String,String> headers)`
- Produces: package-private `static String kuaishouImageUrl(String cdn, String path)`

- [ ] **Step 1: Add failing Kuaishou atlas fixtures**

```java
@Test public void parsesKuaishouAtlasCdnAndPathsWithSizes() throws Exception {
    String atlas = "{\"photo\":{\"id\":\"abc\",\"caption\":\"快手图集\",\"userName\":\"阿快\","
            + "\"ext_params\":{\"atlas\":{\"cdnList\":[{\"cdn\":\"https://ali.example\"}],"
            + "\"list\":[\"p/1.webp\",\"p/2.webp\"],"
            + "\"size\":[{\"w\":1080,\"h\":1440},{\"w\":1440,\"h\":1080}]}}}}";
    FakeHttp http = new FakeHttp().reply("kuaishou.com",
            "https://www.kuaishou.com/short-video/abc", "window.INIT_STATE={\"page\":" + atlas + "}");
    ImagePostResult result = (ImagePostResult) new VideoParser(http)
            .parse("https://www.kuaishou.com/short-video/abc");
    assertEquals("https://ali.example/p/1.webp", result.images.get(0).primaryUrl());
    assertEquals(2, result.images.size());
}

@Test public void keepsKuaishouFullHttpsAtlasUrlsWithoutPrefixingCdn() throws Exception {
    // atlas.list contains https://img.example/one.jpg and //img.example/two.jpg
    // Assert normalized HTTPS URLs remain in platform order.
}
```

Add failures for an atlas that belongs to a different `photo.id`, and a normal Kuaishou video fixture to prove video behavior remains unchanged.

- [ ] **Step 2: Run parser tests and verify RED**

Expected: current `parseKuaishou` calls `requireStreams` and reports that no video address exists.

- [ ] **Step 3: Implement target-bound atlas extraction**

Locate the target photo by exact `photo.id`, `photoId`, Apollo key, or `share_info` query value. Accept atlas from `photo.ext_params.atlas` or the matched parent payload's `atlas`; never use the first unrelated atlas found recursively. Read CDN from `atlas.cdn` or `atlas.cdnList[*].cdn`, image paths from `atlas.list`, and optional dimensions from a same-length `atlas.size` array.

`kuaishouImageUrl` performs these exact normalizations:

```java
if (path.startsWith("https://")) return https(path);
if (path.startsWith("//")) return https("https:" + path);
if (cdn == null || cdn.isEmpty()) return "";
String base = cdn.startsWith("https://") ? cdn : "https://" + cdn;
return https(base.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", ""));
```

Return `ImagePostResult` before collecting video streams when the matched atlas produces at least one image.

- [ ] **Step 4: Re-run parser tests and verify GREEN**

Expected: Kuaishou atlas, mismatch rejection, URL normalization, and existing video fixtures all pass.

- [ ] **Step 5: Save checkpoint**

Append the passing Kuaishou atlas cases to `v0.10/progress.md`.

---

### Task 4: 图片比例、格式与受限网络加载

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ImageAspect.java`
- Create: `app/src/main/java/com/linkvideo/parser/ImageFormat.java`
- Create: `app/src/main/java/com/linkvideo/parser/ImageLoader.java`
- Create: `app/src/test/java/com/linkvideo/parser/ImageAspectTest.java`
- Create: `app/src/test/java/com/linkvideo/parser/ImageFormatTest.java`
- Create: `app/src/test/java/com/linkvideo/parser/ImageLoaderTest.java`

**Interfaces:**
- Produces: `ImageAspect.heightForWidth(int targetWidth, int imageWidth, int imageHeight)`
- Produces: `ImageFormat.detect(String contentType, String finalUrl, byte[] bytes)` with `mimeType` and `extension`
- Produces: `ImageLoader.Payload load(ImageResource resource)` with `bytes`, `contentType`, and `finalUrl`

- [ ] **Step 1: Write failing geometry and format tests**

```java
@Test public void calculatesIndependentPortraitAndLandscapeHeights() {
    assertEquals(600, ImageAspect.heightForWidth(450, 1080, 1440));
    assertEquals(338, ImageAspect.heightForWidth(450, 1440, 1080));
}

@Test public void detectsPngBySignatureWhenCdnReturnsOctetStream() throws Exception {
    byte[] png = {(byte)0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    assertEquals(".png", ImageFormat.detect("application/octet-stream",
            "https://img.example/resource", png).extension);
}

@Test public void rejectsHtmlEvenWhenUrlEndsWithJpg() {
    assertThrows(Exception.class, () -> ImageFormat.detect("text/html",
            "https://img.example/fake.jpg", "verify".getBytes(UTF_8)));
}
```

- [ ] **Step 2: Write failing loader tests**

Use the existing `CoverLoaderTest` fake `HttpURLConnection` pattern. Assert that `ImageLoader` sends the resource's Referer/User-Agent/Cookie headers, falls back from a 403 primary URL to its second candidate, follows relative HTTPS redirects, rejects HTTP redirects, stops above 20 MB, rejects non-image bytes, and preserves thread interruption.

- [ ] **Step 3: Run the new tests and verify RED**

Expected: compilation fails because the three production classes do not exist.

- [ ] **Step 4: Implement minimal pure-Java helpers**

`ImageAspect` returns the rounded proportional height; invalid dimensions use a square placeholder and valid output is clamped between one-third and three times target width. `ImageFormat` recognizes JPEG, PNG, WebP and GIF signatures, then supported `image/*` content types; unsupported bytes throw `IOException`. `ImageLoader` limits each candidate to 15-second connect, 30-second read, five redirects, HTTPS only and 20 MB, applies all `ImageResource.headers`, and returns the first valid payload.

- [ ] **Step 5: Re-run all core tests and verify GREEN**

Expected: all loader, format, aspect, parser, video and cover tests pass without warnings.

- [ ] **Step 6: Save checkpoint**

Record helper test counts in `v0.10/progress.md`.

---

### Task 5: 单张图片保存到系统图片目录

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ImageDownloader.java`
- Modify: `work/run-core-tests.ps1`

**Interfaces:**
- Consumes: `ImageLoader`, `ImageFormat`, `ImageResource`
- Produces: `ImageDownloader.DownloadedFile download(Context context, ImagePostResult post, ImageResource image, int oneBasedIndex)`
- Produces: `DownloadedFile.uri`, `displayName`, `displayPath`

- [ ] **Step 1: Add the Android class exclusion before production code**

Change the pure JVM source filter to exclude `MainActivity.java`, `VideoDownloader.java`, `ImageDownloader.java`, `ImageGalleryActivity.java`, and `ImageViewerActivity.java`. Run core tests and confirm the filter itself does not change the passing count.

- [ ] **Step 2: Implement image download using the already-tested helpers**

Fetch bytes with `ImageLoader`, detect format with `ImageFormat`, create a sanitized name `<title>-<index><extension>`, and write only after the payload is confirmed as an image. For API 29+, insert into `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with `RELATIVE_PATH = Pictures/拾链` and `IS_PENDING = 1`, then clear `IS_PENDING`. For API 26–28, write through `MediaStore.Images` to `Environment.DIRECTORY_PICTURES/拾链`. Delete the MediaStore row if writing fails.

- [ ] **Step 3: Compile Android sources**

Run: `powershell -ExecutionPolicy Bypass -File work/build-linkvideo-debug.ps1`

Expected at this stage: resource processing, Java compilation, Lint and assemble complete with the new downloader present but not yet called by UI.

- [ ] **Step 4: Save checkpoint**

Record the compile result and intended path `Pictures/拾链` in `v0.10/progress.md`.

---

### Task 6: 双列自适应图片列表页面

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ImageGalleryActivity.java`
- Create: `app/src/main/res/layout/activity_image_gallery.xml`
- Create: `app/src/main/res/layout/view_image_card.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: serialized `ImagePostResult` extra named `image_post_result`
- Produces: `ImageGalleryActivity.open(Context, ImagePostResult)`
- Consumes later: `ImageViewerActivity.open(Context, ImageResource)`

- [ ] **Step 1: Create layouts with exact visible copy**

`activity_image_gallery.xml` contains a safe-inset root and a `ScrollView` whose horizontal child contains equal-width `left_column` and `right_column` vertical `LinearLayout`s. `view_image_card.xml` contains an `ImageView` with `adjustViewBounds=true`, a hidden status `TextView`, then exactly two full-width buttons in this order: `下载图片` using `@color/action_green`, followed by `查看图片` using `@color/accent`.

- [ ] **Step 2: Implement ordered two-column cards**

Inflate one card per resource; place zero-based even indices in the left column and odd indices in the right column. Set initial image height using `ImageAspect` and metadata when available. Use a fixed two-thread executor to load sampled thumbnails; decode bounds first, choose a power-of-two `inSampleSize` for the measured column width, then set the bitmap and recompute card height from actual dimensions on the main thread.

- [ ] **Step 3: Wire per-card download state**

On `下载图片`, request Android 8/9 storage permission when needed, disable only that card's download button, set its text to `正在下载…`, call `ImageDownloader` on the executor, then restore `下载图片` and show either `文件位置：Pictures/拾链/<name>` or the failure reason. Do not clear or disable other cards.

- [ ] **Step 4: Register Activity and compile resources**

Add `.ImageGalleryActivity` as non-exported and run `:app:processDebugResources` through the project build script.

Expected: layouts, styles, strings, IDs and manifest entries compile.

- [ ] **Step 5: Save checkpoint**

Record layout compilation and card order rules in `v0.10/progress.md`.

---

### Task 7: 应用内全屏查看与主页面结果分流

**Files:**
- Create: `app/src/main/java/com/linkvideo/parser/ImageViewerActivity.java`
- Create: `app/src/main/res/layout/activity_image_viewer.xml`
- Modify: `app/src/main/java/com/linkvideo/parser/ImageGalleryActivity.java`
- Modify: `app/src/main/java/com/linkvideo/parser/MainActivity.java`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `ImageViewerActivity.open(Context, ImageResource)` with extra `image_resource`
- Consumes: `ParsedResult` from `VideoParser.parse`

- [ ] **Step 1: Implement the full-screen viewer**

Use a dark, no-action-bar theme and edge-to-edge root. Deserialize `ImageResource`, load it with `ImageLoader` on one background thread, sample for screen dimensions, and render with `ImageView.ScaleType.FIT_CENTER`. Show a progress indicator during load and a centered `图片加载失败` message on error. The system back button calls `finish()`; never create an `ACTION_VIEW` intent.

- [ ] **Step 2: Connect each `查看图片` button**

Call `ImageViewerActivity.open(this, image)` for the card's own resource. Keep the list Activity and scroll position underneath the viewer.

- [ ] **Step 3: Change MainActivity to dispatch the unified result**

```java
ParsedResult result = new VideoParser().parse(text);
handler.post(() -> {
    if (!isCurrent(token)) return;
    finishLoading();
    if (result instanceof ImagePostResult) {
        status.setText(R.string.result_ready);
        ImageGalleryActivity.open(this, (ImagePostResult) result);
    } else {
        showResult((VideoResult) result);
    }
});
```

Do not call `resultState.succeeded` for an image result, so returning from the gallery leaves the previously displayed video intact. A later successful video parse still replaces it through the existing `showResult` method.

- [ ] **Step 4: Register viewer and compile the complete Android app**

Register `.ImageViewerActivity` as non-exported with the dark theme. Run the build script and fix only errors caused by the new feature.

- [ ] **Step 5: Save checkpoint**

Record that full-screen viewing uses no browser Intent and that prior video state survives returning from gallery.

---

### Task 8: 回归验证、v0.10 版本与本地产物

**Files:**
- Modify: `README.md`
- Modify: `docs/verification.md`
- Create: `v0.10/README.md`
- Create: `v0.10/changes.md`
- Create: `v0.10/verification.md`
- Create: `v0.10/progress.md`
- Create: `v0.10/LinkVideo-v0.10-source.zip`
- Create: `v0.10/LinkVideo-v0.10-debug.apk`

**Interfaces:**
- Produces: package `com.linkvideo.parser`, `versionCode 10`, `versionName 0.10.0`

- [ ] **Step 1: Confirm the new version and local build output**

Confirm Task 1 already set `versionCode 10`, `versionName '0.10.0'`, release folder `v0.10`, and APK filename `LinkVideo-v0.10-debug.apk`. Stop if any value still points to v0.9.

- [ ] **Step 2: Run the complete fresh core test suite**

Run: `powershell -ExecutionPolicy Bypass -File work/run-core-tests.ps1`

Expected: exit code 0 and JUnit reports zero failures. Record the count printed by this fresh run; do not reuse v0.9's 67-test number.

- [ ] **Step 3: Run Android resource, compile, Lint and APK build**

Run: `powershell -ExecutionPolicy Bypass -File work/build-linkvideo-debug.ps1`

Expected: resource processing, ECJ Java compilation, `lintDebug`, and `assembleDebug` all exit 0. Open `app/build/reports/lint-results-debug.txt` and require `No issues found.` before claiming Lint success.

- [ ] **Step 4: Inspect the APK**

Use local Build Tools to confirm package name, `versionCode 10`, `versionName 0.10.0`, minSdk 26, targetSdk 35, and v2 signing. List DEX class names and confirm `ImagePostResult`, `ImageGalleryActivity`, `ImageViewerActivity`, `ImageLoader`, and existing video classes are included.

- [ ] **Step 5: Create the independent v0.10 source snapshot**

Copy source, resources, Gradle wrapper, README, design and plan into `v0.10/LinkVideo-v0.10-source/`, excluding `.gradle`, `build`, older `v0.*` folders, local toolchains, credentials and caches. Zip that directory as `LinkVideo-v0.10-source.zip` and calculate SHA-256 for APK and ZIP.

- [ ] **Step 6: Write exact verification boundaries**

Document automated fixture coverage and build evidence separately from live-platform/device evidence. If no Android device is connected and no fresh public image-post links are available, state that installation, navigation, real image loading, full-screen display and MediaStore download remain unverified on a device.

- [ ] **Step 7: Final scope audit**

Confirm: Douyin image post fixtures pass; Kuaishou atlas fixtures pass; Bilibili image parsing is absent; video fixtures pass; gallery is two columns; card button order and colors match; viewer stays inside the app; image files target `Pictures/拾链`; only v0.10 files were created for this iteration; no GitHub push occurred.
