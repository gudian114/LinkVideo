package com.linkvideo.parser;

final class InstallOutcome {
    enum Kind { SUCCESS, CANCELED, FAILED }

    static final class Result {
        private final Kind kind;
        private final String message;

        Result(Kind kind, String message) {
            this.kind = kind;
            this.message = message;
        }

        Kind kind() { return kind; }
        String message() { return message; }
    }

    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_FAILURE_ABORTED = 3;

    private InstallOutcome() { }

    static Result fromStatus(int status, String systemMessage) {
        if (status == STATUS_SUCCESS) return new Result(Kind.SUCCESS, "更新安装成功");
        if (status == STATUS_FAILURE_ABORTED) {
            return new Result(Kind.CANCELED, "安装已取消，当前版本未改变");
        }
        String detail = systemMessage == null ? "" : systemMessage.trim();
        if (detail.length() > 160) detail = detail.substring(0, 160);
        String message = detail.isEmpty() ? "安装失败，当前版本未改变"
                : "安装失败：" + detail + "。当前版本未改变";
        return new Result(Kind.FAILED, message);
    }
}
