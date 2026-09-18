package com.youtube.downloader.exception;

public class DownloaderException extends RuntimeException {

    private final ErrorCode errorCode;

    public DownloaderException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DownloaderException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public enum ErrorCode {
        INVALID_URL,
        UNSUPPORTED_URL,
        YT_DLP_MISSING,
        FFMPEG_MISSING,
        YT_DLP_FAILURE,
        DOWNLOAD_FAILURE,
        TIMEOUT,
        DISK_ERROR,
        MALFORMED_METADATA,
        VIDEO_UNAVAILABLE,
        UNSUPPORTED_FORMAT,
        PATH_TRAVERSAL,
        INTERNAL_ERROR
    }
}
