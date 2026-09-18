package com.youtube.downloader.exception;

import com.youtube.downloader.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DownloaderException.class)
    public ResponseEntity<ErrorResponse> handleDownloaderException(DownloaderException ex) {
        HttpStatus status = mapStatus(ex.getErrorCode());
        log.warn("Downloader error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status)
                .body(ErrorResponse.failed(ex.getMessage(), ex.getErrorCode().name()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        String message = "Invalid request";
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().getFieldError() != null) {
            message = manv.getBindingResult().getFieldError().getDefaultMessage();
        } else if (ex instanceof ConstraintViolationException cve && !cve.getConstraintViolations().isEmpty()) {
            message = cve.getConstraintViolations().iterator().next().getMessage();
        } else if (ex instanceof IllegalArgumentException iae) {
            message = iae.getMessage();
        }
        log.warn("Validation error: {}", message);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.failed(message, DownloaderException.ErrorCode.INVALID_URL.name()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.failed(
                        "An unexpected error occurred. Please try again.",
                        DownloaderException.ErrorCode.INTERNAL_ERROR.name()));
    }

    private HttpStatus mapStatus(DownloaderException.ErrorCode code) {
        return switch (code) {
            case INVALID_URL, UNSUPPORTED_URL, UNSUPPORTED_FORMAT, MALFORMED_METADATA -> HttpStatus.BAD_REQUEST;
            case YT_DLP_MISSING, FFMPEG_MISSING -> HttpStatus.SERVICE_UNAVAILABLE;
            case VIDEO_UNAVAILABLE -> HttpStatus.NOT_FOUND;
            case PATH_TRAVERSAL -> HttpStatus.FORBIDDEN;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case YT_DLP_FAILURE, DOWNLOAD_FAILURE, DISK_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
