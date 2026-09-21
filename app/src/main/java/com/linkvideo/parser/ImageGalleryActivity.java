package com.linkvideo.parser;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageGalleryActivity extends Activity {
    private static final String EXTRA_RESULT = "image_post_result";
    private static final int REQUEST_STORAGE = 201;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImagePostResult result;
    private ImageResource pendingImage;
    private int pendingIndex;
    private Button pendingButton;
    private boolean destroyed;

    public static void open(Context context, ImagePostResult result) {
        Intent intent = new Intent(context, ImageGalleryActivity.class);
        intent.putExtra(EXTRA_RESULT, result);
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_image_gallery);
        findViewById(R.id.gallery_back).setOnClickListener(ignored -> finish());
        applyWindowInsets();
        result = serializableExtra(getIntent(), EXTRA_RESULT, ImagePostResult.class);
        if (result == null || result.images.isEmpty()) {
            finish();
            return;
        }
        CaptionEditor.bind(this, result.title);
        LinearLayout left = findViewById(R.id.left_column);
        LinearLayout right = findViewById(R.id.right_column);
        for (int i = 0; i < result.images.size(); i++) {
            View card = LayoutInflater.from(this).inflate(R.layout.view_image_card,
                    i % 2 == 0 ? left : right, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(8);
            card.setLayoutParams(params);
            if (i % 2 == 0) left.addView(card); else right.addView(card);
            bindCard(card, result.images.get(i), i + 1);
        }
    }

    private void bindCard(View card, ImageResource image, int oneBasedIndex) {
        FrameLayout frame = card.findViewById(R.id.image_frame);
        ImageView imageView = card.findViewById(R.id.gallery_image);
        ProgressBar loading = card.findViewById(R.id.image_loading);
        TextView imageStatus = card.findViewById(R.id.image_status);
        Button download = card.findViewById(R.id.image_download_button);
        Button view = card.findViewById(R.id.image_view_button);
        imageView.setContentDescription(getString(R.string.image_description) + " " + oneBasedIndex);
        view.setOnClickListener(ignored -> ImageViewerActivity.open(this, image));
        download.setOnClickListener(ignored -> startDownload(image, oneBasedIndex, download));
        frame.post(() -> {
            if (destroyed) return;
            int width = frame.getWidth();
            setFrameHeight(frame, ImageAspect.heightForWidth(width, image.width, image.height));
            loadThumbnail(image, frame, imageView, loading, imageStatus, Math.max(1, width));
        });
    }

    private void loadThumbnail(ImageResource image, FrameLayout frame, ImageView imageView,
                               ProgressBar loading, TextView imageStatus, int targetWidth) {
        executor.submit(() -> {
            try {
                ImageLoader.Payload payload = new ImageLoader().load(image);
                Bitmap bitmap = decodeSampled(payload.bytes, targetWidth);
                if (bitmap == null) throw new IOException("图片格式不受支持");
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(Color.TRANSPARENT);
                    setFrameHeight(frame, ImageAspect.heightForWidth(frame.getWidth(),
                            bitmap.getWidth(), bitmap.getHeight()));
                    loading.setVisibility(View.GONE);
                    imageStatus.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    imageStatus.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void startDownload(ImageResource image, int oneBasedIndex, Button button) {
        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingImage = image;
            pendingIndex = oneBasedIndex;
            pendingButton = button;
            Toast.makeText(this, R.string.image_storage_permission_needed, Toast.LENGTH_SHORT).show();
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
            return;
        }
        beginDownload(image, oneBasedIndex, button);
    }

    private void beginDownload(ImageResource image, int oneBasedIndex, Button button) {
        button.setEnabled(false);
        button.setText(R.string.image_download_running);
        executor.submit(() -> {
            try {
                ImageDownloader.DownloadedFile saved = ImageDownloader.download(
                        getApplicationContext(), result, image, oneBasedIndex);
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    restoreDownloadButton(button);
                    Toast.makeText(this, getString(R.string.download_saved_path, saved.displayPath),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                String message = error.getMessage();
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    restoreDownloadButton(button);
                    Toast.makeText(this, message == null || message.isEmpty()
                            ? getString(R.string.image_download_failed) : message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void restoreDownloadButton(Button button) {
        button.setText(R.string.download_image);
        button.setEnabled(true);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_STORAGE) return;
        ImageResource image = pendingImage;
        int index = pendingIndex;
        Button button = pendingButton;
        pendingImage = null;
        pendingButton = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                && image != null && button != null) {
            beginDownload(image, index, button);
        } else {
            Toast.makeText(this, R.string.image_storage_permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyWindowInsets() {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        View root = findViewById(R.id.gallery_root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }

    private static Bitmap decodeSampled(byte[] bytes, int targetWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / sample > targetWidth * 2 && sample <= 64) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static void setFrameHeight(View frame, int height) {
        if (height <= 0 || frame.getLayoutParams().height == height) return;
        ViewGroup.LayoutParams params = frame.getLayoutParams();
        params.height = height;
        frame.setLayoutParams(params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static <T extends Serializable> T serializableExtra(Intent intent, String key, Class<T> type) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) return intent.getSerializableExtra(key, type);
        Serializable value = intent.getSerializableExtra(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override protected void onDestroy() {
        destroyed = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
