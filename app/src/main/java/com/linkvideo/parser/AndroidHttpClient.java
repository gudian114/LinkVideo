package com.linkvideo.parser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Native requests first; one bounded official-page browser session if Douyin needs it. */
final class AndroidHttpClient implements HttpClient {
    private static final DouyinRouteState DOUYIN_ROUTES = new DouyinRouteState(
            SystemClock::elapsedRealtime, 15 * 60_000L);
    private final Activity activity;
    private final HttpFetcher nativeClient = new HttpFetcher();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String browserTrace = "";

    AndroidHttpClient(Activity activity) { this.activity = activity; }

    @Override public HttpFetcher.Response get(String url, Map<String, String> headers) throws Exception {
        return nativeClient.get(url, headers);
    }
    @Override public HttpFetcher.Response get(String url, Map<String, String> headers,
            long maxDurationMillis) throws Exception {
        return nativeClient.get(url, headers, maxDurationMillis);
    }
    @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body) throws Exception {
        return nativeClient.post(url, headers, body);
    }
    @Override public HttpFetcher.Response post(String url, Map<String, String> headers, String body,
            long maxDurationMillis) throws Exception {
        return nativeClient.post(url, headers, body, maxDurationMillis);
    }
    @Override public boolean supportsBrowser() { return true; }
    @Override public String browserDiagnostics() { return browserTrace; }
    @Override public String browserCookies(String origin) {
        try {
            String value = CookieManager.getInstance().getCookie(origin);
            return value == null ? "" : value;
        } catch (RuntimeException ignored) { return ""; }
    }
    @Override public void flushBrowserCookies() {
        try { CookieManager.getInstance().flush(); }
        catch (RuntimeException ignored) { }
    }
    @Override public DouyinRouteState douyinRouteState() { return DOUYIN_ROUTES; }

    @Override public HttpFetcher.Response browserGet(String pageUrl, String requestUrl, Map<String, String> headers) throws Exception {
        return browserGet(pageUrl, requestUrl, headers, 60_000L);
    }

    @Override public HttpFetcher.Response browserGet(String pageUrl, String requestUrl,
            Map<String, String> headers, long maxDurationMillis) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("网页读取须在后台等待");
        HttpFetcher.checkInterrupted();
        Session session = new Session(new DouyinBrowserPage(pageUrl, requestUrl),
                headers.get("User-Agent"), maxDurationMillis);
        browserTrace = "  网页状态：等待主线程启动\n";
        main.post(session::start);
        try {
            if (!session.done.await(session.maxDurationMillis + 1_000L, TimeUnit.MILLISECONDS))
                throw new SocketTimeoutException("抖音网页组件未响应，请查看错误详情");
            HttpFetcher.checkInterrupted();
            if (session.error != null) throw session.error;
            return session.response;
        } finally {
            session.cancelled = true;
            main.post(session::dispose);
        }
    }

    private final class Session {
        final DouyinBrowserPage page;
        final String userAgent;
        final DouyinPageVisibility visibility;
        final long maxDurationMillis;
        final CountDownLatch done = new CountDownLatch(1);
        final Runnable poll = this::poll;
        final long started = SystemClock.elapsedRealtime();
        volatile boolean cancelled;
        WebView web;
        Dialog dialog;
        TextView statusText;
        boolean finished;
        boolean displayPage;
        boolean evaluating;
        int starts, commits, finishes, pageProgress, polls, replies, httpStatus, networkError, sslError, consoleErrors;
        int requests;
        boolean requestTimeout;
        boolean verificationUi;
        String ready = "未知", provider = "未知", stage = "启动";
        HttpFetcher.Response response;
        Exception error;

        Session(DouyinBrowserPage page, String userAgent, long maxDurationMillis) {
            this.page = page; this.userAgent = userAgent;
            this.maxDurationMillis = Math.max(1L, Math.min(60_000L, maxDurationMillis));
            visibility = new DouyinPageVisibility(SettingsActivity.shouldShowDouyinPage(activity), started);
        }

        @SuppressLint("SetJavaScriptEnabled")
        void start() {
            if (cancelled) return;
            if (activity.isDestroyed() || activity.isFinishing()) { finish(null, new CancellationException()); return; }
            try {
                web = new WebView(activity);
                PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null && info.versionName != null && info.versionName.matches("[0-9.]{1,40}")) provider = info.versionName;
                WebSettings settings = web.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(false);
                settings.setAllowContentAccess(false);
                settings.setGeolocationEnabled(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                settings.setMediaPlaybackRequiresUserGesture(true);
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
                settings.setUserAgentString(userAgent);
                web.setWebChromeClient(new WebChromeClient() {
                    @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
                    @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                        if (displayPage) return false;
                        result.confirm(); return true;
                    }
                    @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                        if (displayPage) return false;
                        result.cancel(); return true;
                    }
                    @Override public boolean onJsPrompt(WebView view, String url, String message, String initial, JsPromptResult result) {
                        if (displayPage) return false;
                        result.cancel(); return true;
                    }
                    @Override public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                        if (displayPage) return false;
                        result.confirm(); return true;
                    }
                    @Override public void onProgressChanged(WebView view, int progress) {
                        if (cancelled || finished) return;
                        pageProgress = progress;
                        visibility.observe(progress, ready, SystemClock.elapsedRealtime());
                        trace();
                    }
                    @Override public boolean onConsoleMessage(ConsoleMessage message) {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) { consoleErrors++; trace(); }
                        return true;
                    }
                });
                web.setWebViewClient(new WebViewClient() {
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        if (!request.isForMainFrame() || allowed(request.getUrl().toString())) return null;
                        main.post(() -> finish(null, new Exception("抖音网页跳转到不受支持的地址")));
                        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                    }
                    @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        if (cancelled || finished) return;
                        starts++; stage = "加载页面"; trace();
                        if (!allowed(url)) { view.stopLoading(); finish(null, new Exception("抖音网页跳转到不受支持的地址")); }
                    }
                    @Override public void onPageCommitVisible(WebView view, String url) { commits++; trace(); }
                    @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        if (!request.isForMainFrame()) return !"https".equalsIgnoreCase(request.getUrl().getScheme());
                        if (allowed(request.getUrl().toString())) return false;
                        finish(null, new Exception("抖音网页跳转到不受支持的地址"));
                        return true;
                    }
                    @Override public void onPageFinished(WebView view, String url) {
                        if (cancelled || finished) return;
                        try {
                            if (!allowed(url)) throw new Exception("抖音网页跳转到不受支持的地址");
                            finishes++; trace();
                        } catch (Exception invalid) { finish(null, invalid); }
                    }
                    @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        if (!request.isForMainFrame()) return;
                        networkError = error.getErrorCode(); stage = "页面网络失败";
                        finish(null, new Exception("抖音网页加载失败（网络错误码 " + networkError + "），请查看错误详情"));
                    }
                    @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                        if (!request.isForMainFrame()) return;
                        httpStatus = response.getStatusCode(); stage = "页面HTTP失败";
                        finish(null, new HttpFetcher.Failure(httpStatus));
                    }
                    @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        handler.cancel(); sslError = error.getPrimaryError() + 1; trace();
                        if (error.getUrl().equals(view.getUrl())) { stage = "页面证书失败"; finish(null, new Exception("抖音网页安全连接失败，请查看错误详情")); }
                    }
                    @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        finish(null, new Exception("系统网页组件已停止，请更新 Android System WebView 后重试"));
                        return true;
                    }
                });
                attachHiddenPage();
                if (visibility.shouldReveal(SystemClock.elapsedRealtime(), activity.hasWindowFocus())) showPage();
                web.loadUrl(page.pageUrl);
                main.postDelayed(poll, 250);
            } catch (Exception failure) { finish(null, failure); }
        }

        @SuppressLint("ClickableViewAccessibility") // This transparent page must not receive user interaction.
        void attachHiddenPage() {
            // Keep a measured, attached page without covering the app or taking keyboard/accessibility focus.
            web.setAlpha(0f);
            web.setFocusable(false);
            web.setFocusableInTouchMode(false);
            web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            web.setOnTouchListener((view, event) -> true);
            ViewGroup content = activity.findViewById(android.R.id.content);
            content.addView(web, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            trace();
        }

        @SuppressLint("ClickableViewAccessibility") // Removing our listener restores WebView's native touch/accessibility handling.
        void showPage() {
            if (cancelled || finished || dialog != null || web == null) return;
            if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()) return;
            // Move the same loaded page into the dialog so its cookies, challenge and JS state survive.
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.setAlpha(1f);
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            web.setOnTouchListener(null);
            LinearLayout panel = new LinearLayout(activity);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackgroundColor(activity.getColor(R.color.surface));
            int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
            TextView title = new TextView(activity);
            title.setText(R.string.douyin_browser_title); title.setTextSize(20); title.setTextColor(activity.getColor(R.color.text_primary));
            title.setPadding(pad, pad, pad, pad / 2); panel.addView(title);
            statusText = new TextView(activity);
            statusText.setTextColor(activity.getColor(R.color.text_secondary));
            statusText.setPadding(pad, 0, pad, pad / 2); panel.addView(statusText);
            ProgressBar loading = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            loading.setIndeterminate(true); panel.addView(loading);
            panel.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            Button cancel = new Button(activity); cancel.setText(R.string.douyin_browser_cancel); panel.addView(cancel);
            dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); dialog.setContentView(panel);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnCancelListener(ignored -> finish(null, new CancellationException("已取消本条解析")));
            cancel.setOnClickListener(ignored -> finish(null, new CancellationException("已取消本条解析")));
            dialog.show();
            displayPage = true;
            visibility.shown(SystemClock.elapsedRealtime());
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * .85f));
            trace();
        }

        void trace() {
            browserTrace = "  WebView版本=" + provider + "，阶段=" + stage + "，启动/可见/完成=" + starts + "/" + commits + "/" + finishes
                    + "，页面进度=" + pageProgress + "，轮询/回调=" + polls + "/" + replies + "，文档=" + ready
                    + "，详情请求=" + requests + "，详情HTTP=" + httpStatus + "，单次超时=" + requestTimeout
                    + "，网络错误=" + networkError + "，证书错误=" + sslError + "，脚本错误数=" + consoleErrors
                    + "，验证界面=" + (verificationUi ? "检测到" : "未检测")
                    + "，网页显示=" + (displayPage ? "开启" : "关闭") + "\n";
        }

        void poll() {
            if (cancelled || finished || web == null) return;
            if (activity.isDestroyed() || activity.isFinishing()) { finish(null, new CancellationException()); return; }
            long now = SystemClock.elapsedRealtime();
            long elapsed = now - started;
            if (elapsed >= maxDurationMillis) { stage = "总等待超时"; finish(null,
                    new SocketTimeoutException("抖音解析已达到总等待时间，请查看错误详情")); return; }
            if (visibility.expired(now)) { stage = "等待作品超时"; finish(null, new SocketTimeoutException(
                    "抖音网页未能及时返回作品；若页面确实需要操作，请在设置打开“显示抖音解析网页”后重试")); return; }
            if (visibility.shouldReveal(now, activity.hasWindowFocus())) {
                try { showPage(); }
                catch (RuntimeException failure) { finish(null, failure); return; }
            }
            if (statusText != null) statusText.setText(activity.getString(R.string.douyin_browser_waiting, elapsed / 1000));
            // Schedule independently of onPageFinished: a slow subresource must not prevent reading.
            main.postDelayed(poll, 750);
            // A delayed JS callback must not end the session before we can show a verification page.
            // The session deadline above still bounds the wait, including on unresponsive renderers.
            if (evaluating) return;
            if (web.getUrl() == null) return;
            if (!allowed(web.getUrl())) { stage = "地址改变"; finish(null, new Exception("抖音网页地址已改变")); return; }
            evaluating = true;
            polls++; trace();
            try {
                web.evaluateJavascript(page.script(), value -> {
                    evaluating = false;
                    if (cancelled || finished) return;
                    replies++;
                    try {
                        if (web == null || !allowed(web.getUrl())) { stage = "回调地址改变"; finish(null, new Exception("抖音网页地址已改变")); return; }
                        Object decoded = new JSONTokener(value == null ? "null" : value).nextValue();
                        if (!(decoded instanceof String)) { stage = "等待网页脚本"; trace(); return; }
                        JSONObject result = new JSONObject((String) decoded);
                        int status = result.optInt("status", 0);
                        httpStatus = status;
                        requests = result.optInt("requests", requests);
                        requestTimeout = result.optBoolean("requestTimeout", requestTimeout);
                        String document = result.optString("ready", "");
                        if (document.equals("loading") || document.equals("interactive") || document.equals("complete")) ready = document;
                        visibility.observe(pageProgress, ready, SystemClock.elapsedRealtime());
                        stage = "读取作品数据";
                        boolean scriptError = result.optBoolean("scriptError");
                        if (scriptError) stage = "网页请求启动失败";
                        verificationUi = result.optBoolean("interactionRequired");
                        long observedAt = SystemClock.elapsedRealtime();
                        visibility.observeInteraction(verificationUi, observedAt);
                        if (visibility.shouldReveal(observedAt, activity.hasWindowFocus())) showPage();
                        trace();
                        String body = result.optString("body", "");
                        HttpFetcher.Response latest = new HttpFetcher.Response(page.pageUrl, body,
                                CookieManager.getInstance().getCookie(page.pageUrl), status, "application/json", "", 0);
                        if (status == 200 && page.hasItem(body)) { stage = "取得目标作品"; finish(latest, null); }
                    } catch (Exception failure) { finish(null, failure); }
                });
            } catch (Exception failure) { evaluating = false; finish(null, failure); }
        }

        boolean allowed(String url) {
            try { return url != null && DouyinBrowserPage.allowed(new URI(url)); }
            catch (Exception invalid) { return false; }
        }

        void finish(HttpFetcher.Response value, Exception failure) {
            if (finished) return;
            response = value;
            error = failure;
            finished = true;
            trace();
            flushBrowserCookies();
            dispose();
            done.countDown();
        }

        void dispose() {
            visibility.finish();
            main.removeCallbacks(poll);
            if (dialog != null) { dialog.dismiss(); dialog = null; }
            if (web == null) return;
            WebView view = web;
            web = null;
            try {
                view.stopLoading();
                if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                view.destroy();
            } catch (RuntimeException ignored) { }
        }
    }
}
