package com.linkvideo.parser;

final class PreviewState {
    enum Mode { COVER, PREPARING, PLAYING, PAUSED, ENDED, ERROR }

    private Mode mode = Mode.COVER;
    private boolean controlsVisible = true;

    Mode mode() { return mode; }
    boolean controlsVisible() { return controlsVisible; }
    boolean showsPauseIcon() { return mode == Mode.PLAYING; }

    void requestPlay() {
        mode = Mode.PREPARING;
        controlsVisible = false;
    }

    void prepared() {
        mode = Mode.PLAYING;
        controlsVisible = true;
    }

    void pause() {
        mode = Mode.PAUSED;
        controlsVisible = true;
    }

    void suspend() {
        if (mode == Mode.PLAYING || mode == Mode.PREPARING) pause();
    }

    void resume() {
        mode = Mode.PLAYING;
        controlsVisible = true;
    }

    void hideControls() {
        if (mode == Mode.PLAYING) controlsVisible = false;
    }

    void singleTap() { controlsVisible = true; }

    void completed() {
        mode = Mode.ENDED;
        controlsVisible = true;
    }

    void failed() {
        mode = Mode.ERROR;
        controlsVisible = true;
    }

    void reset() {
        mode = Mode.COVER;
        controlsVisible = true;
    }
}
