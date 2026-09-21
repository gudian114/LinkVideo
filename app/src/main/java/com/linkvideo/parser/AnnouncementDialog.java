package com.linkvideo.parser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;

/** Startup notice and the always-available notice in settings share the same saved choice. */
final class AnnouncementDialog {
    private static final String PREFERENCES = "linkvideo_settings";
    private static final String DISMISSED = "announcement_dismissed";
    private static final String SAVED_VISIBLE = "announcement_visible";
    private static final String SAVED_CHECKED = "announcement_checked";
    private final Activity activity;
    private AlertDialog dialog;
    private CheckBox neverShow;

    AnnouncementDialog(Activity activity) { this.activity = activity; }

    void restoreOrShow(Bundle state, boolean startup) {
        if (state != null) {
            if (state.getBoolean(SAVED_VISIBLE, false)) {
                show();
                if (neverShow != null) neverShow.setChecked(state.getBoolean(SAVED_CHECKED, false));
            }
        } else if (startup && !dismissed()) {
            show();
        }
    }

    void show() {
        if (activity.isFinishing() || activity.isDestroyed() || (dialog != null && dialog.isShowing())) return;
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_announcement, null);
        neverShow = content.findViewById(R.id.announcement_never_show);
        neverShow.setChecked(dismissed());
        dialog = new AlertDialog.Builder(activity).setTitle(R.string.announcement_title)
                .setView(content).setCancelable(false).create();
        content.findViewById(R.id.announcement_close).setOnClickListener(ignored -> {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putBoolean(DISMISSED, neverShow.isChecked()).apply();
            dismiss();
        });
        dialog.show();
    }

    void saveState(Bundle state) {
        boolean visible = dialog != null && dialog.isShowing();
        state.putBoolean(SAVED_VISIBLE, visible);
        if (visible) state.putBoolean(SAVED_CHECKED, neverShow.isChecked());
    }

    void dismiss() {
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        neverShow = null;
    }

    private boolean dismissed() {
        return activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(DISMISSED, false);
    }
}
