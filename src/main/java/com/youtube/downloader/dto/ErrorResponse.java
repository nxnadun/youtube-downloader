package com.youtube.downloader.dto;

public class ErrorResponse {

    private String status;
    private String message;
    private String code;

    public static ErrorResponse failed(String message, String code) {
        ErrorResponse response = new ErrorResponse();
        response.status = "FAILED";
        response.message = message;
        response.code = code;
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
