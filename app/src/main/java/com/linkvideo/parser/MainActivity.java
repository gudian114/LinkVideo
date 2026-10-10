package com.linkvideo.parser;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final String SAVED_INPUT = "input_text";
    private static final int REQUEST_STORAGE = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService coverExecutor = Executors.newSingleThreadExecutor();
    private Future<?> parseTask;
    private Future<?> coverTask;
    private Future<?> downloadTask;
    private long generation;
    private volatile boolean destroyed;
    private EditText input;
    private Button pasteButton;
    private Button parseButton;
    private Button reparseButton;
    private Button clearButton;
    private Button downloadButton;
    private Button coverDownloadButton;
    private ProgressBar progress;
    private View parseProgressPanel;
    private TextView parsePercent;
    private ProgressBar downloadProgress;
    private TextView status;
    private Button errorDetailsButton;
    private AlertDialog errorDetailsDialog;
    private AnnouncementDialog announcement;
    private TextView downloadDetail;
    private View resultCard;
    private ImageView cover;
    private TextView coverStatus;
    private View previewFrame;
    private VideoView previewVideo;
    private ImageButton previewCenterControl;
    private ImageButton previewBottomControl;
    private View previewBottomControls;
    private ProgressBar previewLoading;
    private SeekBar previewSeek;
    private TextView previewTime;
    private final PreviewState previewState = new PreviewState();
    private final ParseDisplayState<VideoResult> resultState = new ParseDisplayState<>();
    private boolean previewPrepared;
    private boolean previewSeeking;
    private long previewAspectRequest;
    private boolean downloadRunning;
    private boolean pendingCoverDownload;
    private final Runnable hidePreviewControls = () -> {
        previewState.hideControls();
        if (!previewState.controlsVisible()) {
            fadeOutPreviewControl(previewCenterControl);
            fadeOutPreviewControl(previewBottomControls);
        }
    };
    private final Runnable updatePreviewProgress = new Runnable() {
        @Override public void run() {
            if (!previewPrepared || destroyed) return;
            try {
                int duration = previewVideo.getDuration();
                if (duration > 0) previewSeek.setMax(duration);
                int position = previewVideo.getCurrentPosition();
                if (!previewSeeking) previewSeek.setProgress(position);
                previewTime.setText(PreviewTime.format(position, duration));
                if (previewVideo.isPlaying()) handler.postDelayed(this, 500);
            } catch (RuntimeException ignored) { }
        }
    };

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyWindowInsets();
        input = findViewById(R.id.input);
        pasteButton = findViewById(R.id.paste_button);
        parseButton = findViewById(R.id.parse_button);
        reparseButton = findViewById(R.id.reparse_button);
        clearButton = findViewById(R.id.clear_button);
        downloadButton = findViewById(R.id.download_button);
        coverDownloadButton = findViewById(R.id.cover_download_button);
        progress = findViewById(R.id.progress);
        parseProgressPanel = findViewById(R.id.parse_progress_panel);
        parsePercent = findViewById(R.id.parse_percent);
        downloadProgress = findViewById(R.id.download_progress);
        status = findViewById(R.id.status);
        errorDetailsButton = findViewById(R.id.parse_error_details);
        downloadDetail = findViewById(R.id.download_detail);
        resultCard = findViewById(R.id.result_card);
        cover = findViewById(R.id.cover);
        coverStatus = findViewById(R.id.cover_status);
        previewFrame = findViewById(R.id.preview_frame);
        previewVideo = findViewById(R.id.preview_video);
        previewCenterControl = findViewById(R.id.preview_center_control);
        previewBottomControl = findViewById(R.id.preview_bottom_control);
        previewBottomControls = findViewById(R.id.preview_bottom_controls);
        previewLoading = findViewById(R.id.preview_loading);
        previewSeek = findViewById(R.id.preview_seek);
        previewTime = findViewById(R.id.preview_time);
        previewCenterControl.setOnClickListener(view -> togglePreview());
        previewBottomControl.setOnClickListener(view -> togglePreview());
        GestureDetector previewGestures = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent event) { return true; }

                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        if (previewState.mode() == PreviewState.Mode.COVER
                                || previewState.mode() == PreviewState.Mode.ERROR) {
                            togglePreview();
                        } else {
                            revealPreviewControls();
                        }
                        return true;
                    }

                    @Override public boolean onDoubleTap(MotionEvent event) {
                        togglePreview();
                        return true;
                    }
                });
        View.OnTouchListener previewTouch = (view, event) -> previewGestures.onTouchEvent(event);
        cover.setOnTouchListener(previewTouch);
        previewVideo.setOnTouchListener(previewTouch);
        previewFrame.setOnTouchListener(previewTouch);
        previewSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) previewTime.setText(PreviewTime.format(progress, seekBar.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                previewSeeking = true;
                handler.removeCallbacks(hidePreviewControls);
                revealPreviewControls();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (previewPrepared) previewVideo.seekTo(seekBar.getProgress());
                previewSeeking = false;
                schedulePreviewControlHide();
            }
        });
        previewVideo.setOnPreparedListener(player -> {
            previewPrepared = true;
            updatePreviewAspect(player.getVideoWidth(), player.getVideoHeight());
            previewSeek.setMax(Math.max(1, player.getDuration()));
            previewTime.setText(PreviewTime.format(0, player.getDuration()));
            cover.setVisibility(View.GONE);
            previewVideo.setVisibility(View.VISIBLE);
            previewVideo.start();
            previewState.prepared();
            renderPreviewControls();
            handler.removeCallbacks(updatePreviewProgress);
            handler.post(updatePreviewProgress);
            schedulePreviewControlHide();
        });
        previewVideo.setOnCompletionListener(player -> {
            previewState.completed();
            previewSeek.setProgress(previewSeek.getMax());
            previewTime.setText(PreviewTime.format(previewSeek.getMax(), previewSeek.getMax()));
            renderPreviewControls();
        });
        previewVideo.setOnErrorListener((player, what, extra) -> {
            previewPrepared = false;
            previewState.failed();
            previewVideo.setVisibility(View.GONE);
            if (cover.getDrawable() != null) cover.setVisibility(View.VISIBLE);
            renderPreviewControls();
            toast(R.string.preview_failed);
            return true;
        });
        coverStatus.setOnClickListener(view -> {
            VideoResult result = resultState.current();
            if (result != null && !result.coverUrls.isEmpty()) {
                loadCover(result);
            }
        });
        pasteButton.setOnClickListener(view -> pasteFromClipboard());
        parseButton.setOnClickListener(view -> requestParse());
        reparseButton.setOnClickListener(view -> requestParse());
        clearButton.setOnClickListener(view -> clearLink());
        findViewById(R.id.settings_button).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.batch_button).setOnClickListener(view ->
                startActivity(new Intent(this, BatchActivity.class)));
        findViewById(R.id.original_button).setOnClickListener(view -> openOriginal());
        findViewById(R.id.copy_video_button).setOnClickListener(view -> copyVideoUrl());
        downloadButton.setOnClickListener(view -> startDownload());
        coverDownloadButton.setOnClickListener(view -> startCoverDownload());
        if (savedInstanceState != null) {
            input.setText(savedInstanceState.getString(SAVED_INPUT, ""));
        } else {
            receiveShare(getIntent());
        }
        announcement = new AnnouncementDialog(this);
        announcement.restoreOrShow(savedInstanceState, true);
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveShare(intent);
    }

    private void receiveShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())
                || !"text/plain".equals(intent.getType())) return;
        CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) input.setText(sharedText);
    }

    private void pasteFromClipboard() {
        try {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            CharSequence text = clip == null || clip.getItemCount() == 0
                    ? null : clip.getItemAt(0).getText();
            if (text == null || text.toString().trim().isEmpty()) {
                toast(R.string.clipboard_empty);
                return;
            }
            input.setText(text);
            input.setSelection(input.length());
            requestParse();
        } catch (RuntimeException exception) {
            toast(R.string.clipboard_unavailable);
        }
    }

    private void clearLink() {
        input.setText("");
        input.requestFocus();
    }

    private void cancelWork() {
        generation++;
        if (parseTask != null) parseTask.cancel(true);
        if (coverTask != null) coverTask.cancel(true);
        parseTask = null;
        coverTask = null;
        resultState.failed();
        resetPreview();
    }

    private void cancelParseRequest() {
        generation++;
        if (parseTask != null) parseTask.cancel(true);
        parseTask = null;
        resultState.failed();
    }

    private void requestParse() {
        if (downloadRunning) return;
        errorDetailsButton.setVisibility(View.GONE);
        status.setTextColor(getColor(R.color.text_secondary));
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            status.setText(R.string.input_empty);
            return;
        }
        cancelParseRequest();
        resultState.started();
        long token = generation;
        beginParse(text, token);
    }

    private void beginParse(String text, long token) {
        if (!isCurrent(token)) return;
        progress.setProgress(0);
        parsePercent.setText(getString(R.string.parse_percent_value, 0));
        parseProgressPanel.setVisibility(View.VISIBLE);
        setParsingButtonsEnabled(false);
        status.setText(R.string.loading);
        parseTask = executor.submit(() -> {
            try {
                ParsedResult result = new VideoParser(new AndroidHttpClient(this)).parse(text, percent -> handler.post(() -> {
                    if (!isCurrent(token)) return;
                    progress.setProgress(percent);
                    parsePercent.setText(getString(R.string.parse_percent_value, percent));
                }));
                handler.post(() -> {
                    if (!isCurrent(token)) return;
                    finishLoading();
                    if (result instanceof ImagePostResult) {
                        boolean keepVideo = SettingsActivity.shouldKeepVideo(this);
                        resultState.finished(keepVideo);
                        if (!keepVideo) clearVideoDisplay();
                        status.setText(R.string.result_ready);
                        status.setTextColor(getColor(R.color.accent));
                        ImageGalleryActivity.open(this, (ImagePostResult) result);
                    } else {
                        showResult((VideoResult) result);
                    }
                });
            } catch (Exception exception) {
                String message = exception.getMessage();
                handler.post(() -> {
                    if (!isCurrent(token)) return;
                    resultState.failed();
                    finishLoading();
                    status.setTextColor(getColor(R.color.error));
                    status.setText(TextUtils.isEmpty(message) ? getString(R.string.parse_failed) : message);
                    if (exception instanceof ParseFailure) {
                        String details = ((ParseFailure) exception).details;
                        errorDetailsButton.setVisibility(View.VISIBLE);
                        errorDetailsButton.setOnClickListener(ignored -> {
                            if (errorDetailsDialog != null) errorDetailsDialog.dismiss();
                            errorDetailsDialog = ParseErrorDialog.show(this, details);
                        });
                    }
                });
            }
        });
    }

    private boolean isCurrent(long token) {
        return !destroyed && !isFinishing() && token == generation;
    }

    private void finishLoading() {
        parseProgressPanel.setVisibility(View.GONE);
        setParsingButtonsEnabled(true);
    }

    private void setParsingButtonsEnabled(boolean enabled) {
        pasteButton.setEnabled(enabled);
        parseButton.setEnabled(enabled);
        reparseButton.setEnabled(enabled);
    }

    private void setInputActionsEnabled(boolean enabled) {
        setParsingButtonsEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private void clearVideoDisplay() {
        if (coverTask != null) coverTask.cancel(true);
        coverTask = null;
        resetPreview();
        previewAspectRequest++;
        cover.setImageDrawable(null);
        cover.setVisibility(View.GONE);
        coverStatus.setText("");
        coverStatus.setVisibility(View.GONE);
        textView(R.id.platform).setText("");
        textView(R.id.caption_input).setText("");
        textView(R.id.author).setText("");
        textView(R.id.duration).setText("");
        downloadProgress.setVisibility(View.GONE);
        downloadDetail.setText("");
        downloadDetail.setVisibility(View.GONE);
        downloadButton.setEnabled(false);
        coverDownloadButton.setEnabled(false);
        resultCard.setVisibility(View.GONE);
    }

    private void showResult(VideoResult result) {
        if (coverTask != null) coverTask.cancel(true);
        coverTask = null;
        resetPreview();
        cover.setImageDrawable(null);
        cover.setVisibility(View.GONE);
        resultState.succeeded(result);
        status.setText(R.string.result_ready);
        status.setTextColor(getColor(R.color.accent));
        textView(R.id.platform).setText(valueOr(result.platform, R.string.unknown));
        CaptionEditor.bind(this, result.title);
        textView(R.id.author).setText(getString(R.string.author_value,
                valueOr(result.author, R.string.unknown)));
        textView(R.id.duration).setText(getString(R.string.duration_value, formatDuration(result.durationSeconds)));
        downloadProgress.setVisibility(View.GONE);
        downloadDetail.setVisibility(View.GONE);
        try {
            DownloadPlan.create(result);
            downloadButton.setText(R.string.download_later);
            downloadButton.setEnabled(true);
        } catch (Exception ignored) {
            downloadButton.setText(R.string.download_unavailable);
            downloadButton.setEnabled(false);
        }
        coverDownloadButton.setEnabled(!TextUtils.isEmpty(result.coverUrl));
        resultCard.setVisibility(View.VISIBLE);
        updatePreviewAspect(16, 9);
        renderPreviewControls();
        coverStatus.setVisibility(View.VISIBLE);
        coverStatus.setEnabled(false);
        if (TextUtils.isEmpty(result.coverUrl)) {
            coverStatus.setText(R.string.cover_missing);
        } else {
            loadCover(result);
        }
    }

    private String formatDuration(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return getString(R.string.unknown);
        long total = Math.round(seconds);
        return total >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
                : String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
    }

    private void startDownload() {
        if (resultState.current() == null || downloadRunning) return;
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingCoverDownload = false;
            toast(R.string.storage_permission_needed);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        beginDownload();
    }

    private void beginDownload() {
        VideoResult result = resultState.current();
        if (result == null) return;
        downloadRunning = true;
        input.setEnabled(false);
        setInputActionsEnabled(false);
        downloadButton.setEnabled(false);
        coverDownloadButton.setEnabled(false);
        downloadButton.setText(R.string.download_running);
        downloadProgress.setIndeterminate(true);
        downloadProgress.setProgress(0);
        downloadProgress.setVisibility(View.VISIBLE);
        downloadDetail.setText(new DownloadProgress("", 0, 0).detail());
        downloadDetail.setVisibility(View.VISIBLE);
        status.setTextColor(getColor(R.color.text_secondary));
        downloadTask = executor.submit(() -> {
            try {
                VideoDownloader.DownloadedFile saved = VideoDownloader.download(getApplicationContext(), result,
                        state -> handler.post(() -> {
                            if (destroyed) return;
                            status.setText(state.message);
                            downloadDetail.setText(state.detail());
                            int percent = state.percent();
                            downloadProgress.setIndeterminate(percent < 0);
                            if (percent >= 0) downloadProgress.setProgress(percent);
                        }));
                handler.post(() -> {
                    if (destroyed) return;
                    finishDownload();
                    status.setTextColor(getColor(R.color.accent));
                    status.setText(R.string.download_complete);
                    downloadProgress.setIndeterminate(false);
                    downloadProgress.setProgress(100);
                    downloadProgress.setVisibility(View.VISIBLE);
                    downloadDetail.setText(getString(R.string.download_saved_path, saved.displayPath));
                    downloadDetail.setVisibility(View.VISIBLE);
                });
            } catch (Exception exception) {
                String message = exception.getMessage();
                handler.post(() -> {
                    if (destroyed) return;
                    finishDownload();
                    status.setTextColor(getColor(R.color.error));
                    status.setText(TextUtils.isEmpty(message) ? getString(R.string.download_failed) : message);
                    downloadProgress.setVisibility(View.GONE);
                    downloadDetail.setText(TextUtils.isEmpty(message) ? getString(R.string.download_failed) : message);
                    downloadDetail.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void finishDownload() {
        downloadRunning = false;
        downloadTask = null;
        input.setEnabled(true);
        parseProgressPanel.setVisibility(View.GONE);
        setInputActionsEnabled(true);
        try {
            DownloadPlan.create(resultState.current());
            downloadButton.setText(R.string.download_later);
            downloadButton.setEnabled(true);
        } catch (Exception ignored) {
            downloadButton.setText(R.string.download_unavailable);
            downloadButton.setEnabled(false);
        }
        VideoResult result = resultState.current();
        coverDownloadButton.setEnabled(result != null && !TextUtils.isEmpty(result.coverUrl));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_STORAGE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingCoverDownload) beginCoverDownload();
            else beginDownload();
        } else toast(R.string.storage_permission_denied);
        pendingCoverDownload = false;
    }

    private void openOriginal() {
        MediaStream stream;
        try {
            stream = MediaSelection.playable(resultState.current());
        } catch (Exception exception) {
            return;
        }
        Uri uri = Uri.parse(stream.url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || TextUtils.isEmpty(uri.getHost())) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            Bundle headers = new Bundle();
            for (java.util.Map.Entry<String, String> entry : stream.headers.entrySet()) {
                headers.putString(entry.getKey(), entry.getValue());
            }
            if (!headers.isEmpty()) intent.putExtra(Browser.EXTRA_HEADERS, headers);
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            toast(R.string.browser_missing);
        }
    }

    private void copyVideoUrl() {
        try {
            MediaStream stream = MediaSelection.playable(resultState.current());
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.copy_video_url), stream.url));
                if (Build.VERSION.SDK_INT < 33) toast(R.string.copied);
            }
        } catch (Exception ignored) { }
    }

    private void togglePreview() {
        if (resultState.current() == null) return;
        switch (previewState.mode()) {
            case PLAYING:
                previewVideo.pause();
                previewState.pause();
                renderPreviewControls();
                return;
            case PAUSED:
                previewVideo.start();
                previewState.resume();
                renderPreviewControls();
                handler.post(updatePreviewProgress);
                schedulePreviewControlHide();
                return;
            case ENDED:
                previewVideo.seekTo(0);
                previewVideo.start();
                previewState.resume();
                previewSeek.setProgress(0);
                renderPreviewControls();
                handler.post(updatePreviewProgress);
                schedulePreviewControlHide();
                return;
            case PREPARING:
                return;
            default:
                startPreview();
        }
    }

    private void startPreview() {
        MediaStream stream;
        try {
            stream = MediaSelection.preview(resultState.current());
        } catch (Exception exception) {
            toast(R.string.preview_failed);
            return;
        }
        handler.removeCallbacks(hidePreviewControls);
        handler.removeCallbacks(updatePreviewProgress);
        previewPrepared = false;
        previewSeek.setProgress(0);
        previewState.requestPlay();
        renderPreviewControls();
        previewVideo.setVisibility(View.VISIBLE);
        previewVideo.setVideoURI(Uri.parse(stream.url), stream.headers);
        previewVideo.requestFocus();
    }

    private void revealPreviewControls() {
        previewState.singleTap();
        renderPreviewControls();
        schedulePreviewControlHide();
    }

    private void schedulePreviewControlHide() {
        handler.removeCallbacks(hidePreviewControls);
        if (!previewSeeking && previewState.mode() == PreviewState.Mode.PLAYING) {
            handler.postDelayed(hidePreviewControls, 1000);
        }
    }

    private void fadeOutPreviewControl(View control) {
        control.animate().cancel();
        control.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            if (!previewState.controlsVisible()) control.setVisibility(View.GONE);
        }).start();
    }

    private void renderPreviewControls() {
        boolean pause = previewState.showsPauseIcon();
        int icon = pause ? R.drawable.ic_preview_pause : R.drawable.ic_preview_play;
        int description = pause ? R.string.preview_pause : R.string.preview_play;
        previewCenterControl.animate().cancel();
        previewBottomControls.animate().cancel();
        previewCenterControl.setAlpha(1f);
        previewBottomControls.setAlpha(1f);
        previewCenterControl.setImageResource(icon);
        previewCenterControl.setContentDescription(getString(description));
        int controlVisibility = previewState.controlsVisible() ? View.VISIBLE : View.GONE;
        previewCenterControl.setVisibility(controlVisibility);
        previewBottomControls.setVisibility(controlVisibility);
        previewBottomControl.setImageResource(icon);
        previewBottomControl.setContentDescription(getString(description));
        previewLoading.setVisibility(previewState.mode() == PreviewState.Mode.PREPARING
                ? View.VISIBLE : View.GONE);
    }

    private void resetPreview() {
        handler.removeCallbacks(hidePreviewControls);
        handler.removeCallbacks(updatePreviewProgress);
        previewPrepared = false;
        previewSeeking = false;
        if (previewVideo != null) {
            try { previewVideo.stopPlayback(); } catch (RuntimeException ignored) { }
            previewVideo.setVisibility(View.GONE);
        }
        if (cover != null && cover.getDrawable() != null) cover.setVisibility(View.VISIBLE);
        if (previewSeek != null) {
            previewSeek.setMax(1);
            previewSeek.setProgress(0);
        }
        if (previewTime != null) previewTime.setText(PreviewTime.format(0, 0));
        previewState.reset();
    }

    private void startCoverDownload() {
        VideoResult current = resultState.current();
        if (current == null || downloadRunning || TextUtils.isEmpty(current.coverUrl)) return;
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingCoverDownload = true;
            toast(R.string.storage_permission_needed);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        beginCoverDownload();
    }

    private void beginCoverDownload() {
        VideoResult result = resultState.current();
        if (result == null || TextUtils.isEmpty(result.coverUrl)) return;
        downloadRunning = true;
        input.setEnabled(false);
        setInputActionsEnabled(false);
        downloadButton.setEnabled(false);
        coverDownloadButton.setEnabled(false);
        downloadProgress.setVisibility(View.GONE);
        downloadDetail.setText(R.string.cover_download_running);
        downloadDetail.setVisibility(View.VISIBLE);
        status.setTextColor(getColor(R.color.text_secondary));
        status.setText(R.string.cover_download_running);
        downloadTask = executor.submit(() -> {
            try {
                VideoDownloader.DownloadedFile saved = VideoDownloader.downloadCover(getApplicationContext(), result);
                handler.post(() -> {
                    if (destroyed) return;
                    finishDownload();
                    status.setTextColor(getColor(R.color.accent));
                    status.setText(R.string.download_complete);
                    downloadDetail.setText(getString(R.string.download_saved_path, saved.displayPath));
                });
            } catch (Exception exception) {
                String message = exception.getMessage();
                handler.post(() -> {
                    if (destroyed) return;
                    finishDownload();
                    status.setTextColor(getColor(R.color.error));
                    status.setText(TextUtils.isEmpty(message) ? getString(R.string.cover_download_failed) : message);
                    downloadDetail.setText(TextUtils.isEmpty(message) ? getString(R.string.cover_download_failed) : message);
                });
            }
        });
    }

    private void loadCover(VideoResult result) {
        coverStatus.setText(R.string.cover_loading);
        coverStatus.setEnabled(false);
        coverTask = coverExecutor.submit(() -> {
            try {
                Bitmap bitmap = new CoverLoader(DouyinRuntimeHeaders::forRequest)
                        .load(result, MainActivity::decodeCover);
                handler.post(() -> {
                    if (destroyed || resultState.current() != result) {
                        bitmap.recycle();
                        return;
                    }
                    cover.setImageBitmap(bitmap);
                    updatePreviewAspect(bitmap.getWidth(), bitmap.getHeight());
                    if (previewState.mode() == PreviewState.Mode.COVER
                            || previewState.mode() == PreviewState.Mode.ERROR) {
                        cover.setVisibility(View.VISIBLE);
                    }
                    coverStatus.setVisibility(View.GONE);
                });
            } catch (Exception exception) {
                handler.post(() -> {
                    if (!destroyed && resultState.current() == result) {
                        coverStatus.setText(R.string.cover_failed);
                        coverStatus.setEnabled(true);
                    }
                });
            }
        });
    }

    private void updatePreviewAspect(int mediaWidth, int mediaHeight) {
        long request = ++previewAspectRequest;
        previewFrame.post(() -> {
            if (destroyed || request != previewAspectRequest) return;
            int height = PreviewAspect.heightForWidth(previewFrame.getWidth(), mediaWidth, mediaHeight);
            if (height <= 0 || previewFrame.getLayoutParams().height == height) return;
            ViewGroup.LayoutParams layout = previewFrame.getLayoutParams();
            layout.height = height;
            previewFrame.setLayoutParams(layout);
        });
    }

    private static Bitmap decodeCover(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("封面格式不支持");
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 1600
                || options.outHeight / options.inSampleSize > 1600) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("封面格式不支持");
        return bitmap;
    }

    private TextView textView(int id) { return findViewById(id); }

    private String valueOr(String value, int fallback) {
        return TextUtils.isEmpty(value) ? getString(fallback) : value;
    }

    private void toast(int message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(SAVED_INPUT, input.getText().toString());
        if (announcement != null) announcement.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStop() {
        if (previewPrepared && previewState.mode() == PreviewState.Mode.PLAYING) {
            previewVideo.pause();
            previewState.pause();
            renderPreviewControls();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (errorDetailsDialog != null) errorDetailsDialog.dismiss();
        if (announcement != null) announcement.dismiss();
        destroyed = true;
        cancelWork();
        if (downloadTask != null) downloadTask.cancel(true);
        resetPreview();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        coverExecutor.shutdownNow();
        super.onDestroy();
    }
}
