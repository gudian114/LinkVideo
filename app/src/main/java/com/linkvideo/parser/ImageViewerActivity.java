package com.linkvideo.parser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageViewerActivity extends Activity {
    private static final String EXTRA_IMAGE = "image_resource";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean destroyed;

    public static void open(Context context, ImageResource image) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putExtra(EXTRA_IMAGE, image);
        context.startActivity(intent);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_image_viewer);
        ImageResource image = serializableExtra(getIntent(), EXTRA_IMAGE, ImageResource.class);
        if (image == null) {
            finish();
            return;
        }
        ImageView imageView = findViewById(R.id.viewer_image);
        ProgressBar loading = findViewById(R.id.viewer_loading);
        TextView status = findViewById(R.id.viewer_status);
        executor.submit(() -> {
            try {
                ImageLoader.Payload payload = new ImageLoader(DouyinRuntimeHeaders::forRequest).load(image);
                Bitmap bitmap = decodeForScreen(payload.bytes);
                if (bitmap == null) throw new IOException("图片格式不受支持");
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    imageView.setImageBitmap(bitmap);
                    loading.setVisibility(View.GONE);
                    status.setVisibility(View.GONE);
                });
            } catch (Exception error) {
                handler.post(() -> {
                    if (destroyed || isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    status.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private Bitmap decodeForScreen(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        DisplayMetrics display = getResources().getDisplayMetrics();
        int sample = 1;
        while ((bounds.outWidth / sample > display.widthPixels * 2
                || bounds.outHeight / sample > display.heightPixels * 2) && sample <= 64) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
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
