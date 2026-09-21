package com.linkvideo.parser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

final class CaptionEditor {
    private CaptionEditor() { }

    static void bind(Activity activity, String caption) {
        bind(activity, activity.findViewById(android.R.id.content), caption);
    }

    static void bind(Activity activity, View root, String caption) {
        EditText editor = root.findViewById(R.id.caption_input);
        editor.setText(caption == null ? "" : caption);
        root.findViewById(R.id.caption_copy_button).setOnClickListener(ignored -> {
            ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.caption_hint),
                    editor.getText().toString()));
            if (Build.VERSION.SDK_INT < 33) {
                Toast.makeText(activity, R.string.caption_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
