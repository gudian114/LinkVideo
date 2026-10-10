package com.linkvideo.parser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** One batch result, using the same layout, media selection and player state as the home result. */
final class BatchVideoCard {
    final VideoResult result;
    private final Activity activity;
    private final View root, frame, bottomControls;
    private final ImageView cover;
    private final VideoView video;
    private final ImageButton center, bottom;
    private final ProgressBar loading;
    private final SeekBar seek;
    private final TextView time, coverStatus;
    private final Button download, downloadCover;
    private final EditText caption;
    private final ExecutorService covers;
    private final Runnable beforePlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PreviewState state = new PreviewState();
    private Future<?> coverTask;
    private volatile boolean disposed;
    private boolean prepared, seeking, interactive = true, downloadable, coverRetry;
    private int position, duration, mediaWidth = 16, mediaHeight = 9, coverGeneration;

    private final Runnable hide;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!prepared || disposed) return;
            position = video.getCurrentPosition();
            duration = Math.max(0, video.getDuration());
            if (!seeking) seek.setProgress(position);
            time.setText(PreviewTime.format(position, duration));
            if (video.isPlaying()) handler.postDelayed(this, 500);
        }
    };

    @SuppressLint("ClickableViewAccessibility") // Playback also has labeled buttons; gestures are optional shortcuts.
    BatchVideoCard(Activity activity, View root, BatchState.Entry entry, ExecutorService covers,
                   Runnable beforePlay, Runnable onDownload, Runnable onDownloadCover) {
        this.activity = activity; this.root = root; this.covers = covers; this.beforePlay = beforePlay;
        result = (VideoResult) entry.result;
        root.setVisibility(View.VISIBLE);
        root.setPadding(0, 0, 0, 0);
        frame = root.findViewById(R.id.preview_frame);
        cover = root.findViewById(R.id.cover);
        video = root.findViewById(R.id.preview_video);
        center = root.findViewById(R.id.preview_center_control);
        bottom = root.findViewById(R.id.preview_bottom_control);
        bottomControls = root.findViewById(R.id.preview_bottom_controls);
        hide = () -> {
            state.hideControls();
            if (!state.controlsVisible()) { fade(center); fade(bottomControls); }
        };
        loading = root.findViewById(R.id.preview_loading);
        seek = root.findViewById(R.id.preview_seek);
        time = root.findViewById(R.id.preview_time);
        coverStatus = root.findViewById(R.id.cover_status);
        download = root.findViewById(R.id.download_button);
        downloadCover = root.findViewById(R.id.cover_download_button);
        caption = root.findViewById(R.id.caption_input);
        CaptionEditor.bind(activity, root, entry.caption());
        caption.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                entry.editedCaption = text.toString();
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        ((TextView) root.findViewById(R.id.platform)).setText(result.platform);
        ((TextView) root.findViewById(R.id.author)).setText(activity.getString(R.string.author_value, result.author));
        duration = Double.isFinite(result.durationSeconds) && result.durationSeconds > 0
                ? (int) Math.min(Integer.MAX_VALUE, Math.round(result.durationSeconds * 1000)) : 0;
        ((TextView) root.findViewById(R.id.duration)).setText(activity.getString(R.string.duration_value,
                duration > 0 ? PreviewTime.format(duration, duration).split(" / ")[0] : activity.getString(R.string.unknown)));
        seek.setMax(Math.max(1, duration));
        time.setText(PreviewTime.format(0, duration));
        try { DownloadPlan.create(result); downloadable = true; }
        catch (Exception ignored) { download.setText(R.string.download_unavailable); }
        download.setOnClickListener(ignored -> onDownload.run());
        downloadCover.setOnClickListener(ignored -> onDownloadCover.run());
        root.findViewById(R.id.original_button).setOnClickListener(ignored -> openVideo());
        root.findViewById(R.id.copy_video_button).setOnClickListener(ignored -> copyVideo());
        center.setOnClickListener(ignored -> toggle());
        bottom.setOnClickListener(ignored -> toggle());
        GestureDetector gestures = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return interactive; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                if (!interactive) return false;
                if (state.mode() == PreviewState.Mode.COVER || state.mode() == PreviewState.Mode.ERROR) toggle();
                else { state.singleTap(); render(); scheduleHide(); }
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event) { toggle(); return true; }
        });
        View.OnTouchListener touch = (view, event) -> interactive && gestures.onTouchEvent(event);
        cover.setOnTouchListener(touch); video.setOnTouchListener(touch); frame.setOnTouchListener(touch);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) time.setText(PreviewTime.format(progress, bar.getMax()));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                seeking = true; handler.removeCallbacks(hide); state.singleTap(); render();
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                position = bar.getProgress();
                if (prepared) video.seekTo(position);
                seeking = false; scheduleHide();
            }
        });
        video.setOnPreparedListener(player -> {
            if (disposed || state.mode() != PreviewState.Mode.PREPARING || !interactive) { video.stopPlayback(); return; }
            prepared = true;
            duration = Math.max(0, player.getDuration());
            updateAspect(player.getVideoWidth(), player.getVideoHeight());
            seek.setMax(Math.max(1, duration));
            video.seekTo(position);
            cover.setVisibility(View.GONE);
            video.start(); state.prepared(); render();
            handler.removeCallbacks(tick); handler.post(tick); scheduleHide();
        });
        video.setOnCompletionListener(player -> {
            if (disposed || !prepared) return;
            position = duration; seek.setProgress(duration); time.setText(PreviewTime.format(duration, duration));
            state.completed(); render();
        });
        video.setOnErrorListener((player, what, extra) -> {
            if (disposed) return true;
            prepared = false; state.failed(); video.stopPlayback(); video.setVisibility(View.GONE);
            cover.setVisibility(cover.getDrawable() == null ? View.GONE : View.VISIBLE);
            render(); toast(R.string.preview_failed); return true;
        });
        frame.addOnLayoutChangeListener((view, left, top, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) updateAspect(mediaWidth, mediaHeight);
        });
        coverStatus.setOnClickListener(ignored -> { if (interactive && coverRetry) loadCover(); });
        updateAspect(16, 9);
        setInteractive(true);
        render();
        if (result.coverUrls.isEmpty()) { coverStatus.setText(R.string.cover_missing); coverStatus.setEnabled(false); }
        else loadCover();
    }

    private void toggle() {
        if (!interactive || disposed || state.mode() == PreviewState.Mode.PREPARING) return;
        if (state.mode() == PreviewState.Mode.PLAYING) {
            video.pause(); position = video.getCurrentPosition(); state.pause(); render();
            handler.removeCallbacks(tick); handler.removeCallbacks(hide); return;
        }
        beforePlay.run();
        if (state.mode() == PreviewState.Mode.ENDED) position = 0;
        if (prepared) {
            video.seekTo(position); video.start(); state.resume(); render();
            handler.removeCallbacks(tick); handler.post(tick); scheduleHide(); return;
        }
        try {
            MediaStream stream = MediaSelection.preview(result);
            state.requestPlay(); render();
            video.setVisibility(View.VISIBLE);
            video.setVideoURI(Uri.parse(stream.url), stream.headers);
        } catch (Exception failure) { state.failed(); render(); toast(R.string.preview_failed); }
    }

    void pauseAndRelease() {
        if (disposed) return;
        boolean hadPlayback = prepared || state.mode() == PreviewState.Mode.PREPARING;
        if (prepared) { position = video.getCurrentPosition(); video.pause(); }
        prepared = false;
        handler.removeCallbacks(tick); handler.removeCallbacks(hide);
        video.stopPlayback(); video.setVisibility(View.GONE);
        cover.setVisibility(cover.getDrawable() == null ? View.GONE : View.VISIBLE);
        if (hadPlayback) state.suspend();
        render();
    }

    void setInteractive(boolean enabled) {
        if (!enabled && interactive) pauseAndRelease();
        interactive = enabled;
        center.setEnabled(enabled); bottom.setEnabled(enabled); seek.setEnabled(enabled);
        caption.setEnabled(enabled);
        root.findViewById(R.id.caption_copy_button).setEnabled(enabled);
        root.findViewById(R.id.original_button).setEnabled(enabled);
        root.findViewById(R.id.copy_video_button).setEnabled(enabled);
        download.setEnabled(enabled && downloadable);
        downloadCover.setEnabled(enabled && !result.coverUrls.isEmpty());
        coverStatus.setEnabled(enabled && coverRetry);
    }

    private void render() {
        center.animate().cancel(); bottomControls.animate().cancel();
        center.setAlpha(1); bottomControls.setAlpha(1);
        int icon = state.showsPauseIcon() ? R.drawable.ic_preview_pause : R.drawable.ic_preview_play;
        String label = activity.getString(state.showsPauseIcon() ? R.string.preview_pause : R.string.preview_play);
        center.setImageResource(icon); bottom.setImageResource(icon);
        center.setContentDescription(label); bottom.setContentDescription(label);
        center.setVisibility(state.controlsVisible() ? View.VISIBLE : View.GONE);
        bottomControls.setVisibility(state.controlsVisible() ? View.VISIBLE : View.GONE);
        loading.setVisibility(state.mode() == PreviewState.Mode.PREPARING ? View.VISIBLE : View.GONE);
    }

    private void scheduleHide() {
        handler.removeCallbacks(hide);
        if (!seeking && state.mode() == PreviewState.Mode.PLAYING) handler.postDelayed(hide, 1000);
    }

    private void fade(View control) {
        control.animate().alpha(0).setDuration(250).withEndAction(() -> {
            if (!disposed && !state.controlsVisible()) control.setVisibility(View.GONE);
        }).start();
    }

    private void updateAspect(int width, int height) {
        if (width <= 0 || height <= 0) return;
        mediaWidth = width; mediaHeight = height;
        frame.post(() -> {
            if (disposed) return;
            int target = PreviewAspect.heightForWidth(frame.getWidth(), mediaWidth, mediaHeight);
            if (target <= 0 || frame.getLayoutParams().height == target) return;
            ViewGroup.LayoutParams layout = frame.getLayoutParams(); layout.height = target; frame.setLayoutParams(layout);
        });
    }

    private void loadCover() {
        int request = ++coverGeneration;
        if (coverTask != null) coverTask.cancel(true);
        coverRetry = false; coverStatus.setEnabled(false); coverStatus.setVisibility(View.VISIBLE);
        coverStatus.setText(R.string.cover_loading);
        coverTask = covers.submit(() -> {
            try {
                Bitmap bitmap = new CoverLoader(DouyinRuntimeHeaders::forRequest)
                        .load(result, BatchVideoCard::decodeCover);
                handler.post(() -> {
                    if (disposed || request != coverGeneration) { bitmap.recycle(); return; }
                    cover.setImageBitmap(bitmap);
                    if (!prepared) { updateAspect(bitmap.getWidth(), bitmap.getHeight()); cover.setVisibility(View.VISIBLE); }
                    coverStatus.setVisibility(View.GONE);
                });
            } catch (Exception failure) {
                handler.post(() -> {
                    if (disposed || request != coverGeneration) return;
                    coverRetry = true; coverStatus.setText(R.string.cover_failed); coverStatus.setEnabled(interactive);
                });
            }
        });
    }

    private static Bitmap decodeCover(byte[] bytes) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("封面格式不支持");
        options.inSampleSize = 1;
        while (options.outWidth / options.inSampleSize > 960 || options.outHeight / options.inSampleSize > 960) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (bitmap == null) throw new IOException("封面格式不支持");
        return bitmap;
    }

    private void openVideo() {
        try {
            MediaStream stream = MediaSelection.playable(result);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(stream.url));
            Bundle headers = new Bundle();
            for (java.util.Map.Entry<String, String> header : stream.headers.entrySet())
                headers.putString(header.getKey(), header.getValue());
            if (!headers.isEmpty()) intent.putExtra(Browser.EXTRA_HEADERS, headers);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException failure) { toast(R.string.browser_missing); }
        catch (Exception failure) { toast(R.string.preview_failed); }
    }

    private void copyVideo() {
        try {
            MediaStream stream = MediaSelection.playable(result);
            ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.copy_video_url), stream.url));
                if (Build.VERSION.SDK_INT < 33) toast(R.string.copied);
            }
        } catch (Exception failure) { toast(R.string.preview_failed); }
    }

    void dispose() {
        pauseAndRelease(); disposed = true;
        if (coverTask != null) coverTask.cancel(true);
        handler.removeCallbacksAndMessages(null);
        center.animate().cancel(); bottomControls.animate().cancel();
        cover.setImageDrawable(null);
    }

    private void toast(int text) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show(); }
}
