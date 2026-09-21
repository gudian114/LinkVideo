package com.linkvideo.parser;

final class ParseFailure extends Exception {
    private static final long serialVersionUID = 1L;
    final String details;

    ParseFailure(Exception cause, String details) {
        super(cause.getMessage(), cause);
        this.details = details;
    }
}
