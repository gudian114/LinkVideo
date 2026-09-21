package com.linkvideo.parser;

final class ParseDisplayState<T> {
    private T current;
    private boolean parsing;

    T current() { return current; }
    boolean isParsing() { return parsing; }

    void started() { parsing = true; }

    void succeeded(T result) {
        current = result;
        parsing = false;
    }

    void failed() { parsing = false; }

    void finished(boolean keepCurrent) {
        if (!keepCurrent) current = null;
        parsing = false;
    }
}
