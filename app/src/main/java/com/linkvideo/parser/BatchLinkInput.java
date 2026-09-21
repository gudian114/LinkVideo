package com.linkvideo.parser;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

public final class BatchLinkInput extends EditText {
    private Runnable onNextInput;
    private Runnable onEmptyBackspace;
    private boolean deletingEmpty;
    private boolean changing;

    public BatchLinkInput(Context context, AttributeSet attrs) {
        super(context, attrs);
        addTextChangedListener(new TextWatcher() {
            private String previous;
            private String restore;
            private int selection;

            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
                if (changing) return;
                previous = text.toString();
                restore = null;
                selection = Math.max(0, getSelectionEnd());
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (changing || onNextInput == null) return;
                String inserted = text.subSequence(start, start + count).toString();
                if (BatchState.opensNextInput(inserted)) restore = previous;
            }

            @Override public void afterTextChanged(Editable text) {
                if (changing || restore == null) return;
                String value = restore;
                restore = null;
                changing = true;
                try {
                    text.replace(0, text.length(), value);
                    setSelection(Math.min(selection, text.length()));
                } finally { changing = false; }
                moveNext();
            }
        });
    }

    void setOnNextInput(Runnable next) { onNextInput = next; }
    void setOnEmptyBackspace(Runnable remove) { onEmptyBackspace = remove; }

    private boolean removeEmpty() {
        if (!isEnabled() || length() != 0 || onEmptyBackspace == null) return false;
        if (!deletingEmpty) {
            deletingEmpty = true;
            post(() -> {
                deletingEmpty = false;
                if (isEnabled() && length() == 0) onEmptyBackspace.run();
            });
        }
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL && event.getRepeatCount() == 0 && removeEmpty()) return true;
        return super.onKeyDown(keyCode, event);
    }

    void clearLink() {
        changing = true;
        try { setText(""); } finally { changing = false; }
    }

    @Override public boolean onTextContextMenuItem(int id) {
        changing = true;
        try { return super.onTextContextMenuItem(id); }
        finally { changing = false; }
    }

    private boolean moveNext() {
        if (onNextInput == null) return false;
        post(onNextInput);
        return true;
    }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (beforeLength > 0 && afterLength == 0 && removeEmpty()) return true;
                return super.deleteSurroundingText(beforeLength, afterLength);
            }

            @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (beforeLength > 0 && afterLength == 0 && removeEmpty()) return true;
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
            }

            @Override public boolean sendKeyEvent(KeyEvent event) {
                int key = event.getKeyCode();
                if (key == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getRepeatCount() == 0 && removeEmpty()) return true;
                if (key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) moveNext();
                    return true;
                }
                return super.sendKeyEvent(event);
            }
        };
    }
}
