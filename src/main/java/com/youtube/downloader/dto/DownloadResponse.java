package com.youtube.downloader.dto;

public class DownloadResponse {

    private String status;
    private String filename;
    private String downloadUrl;
    private String message;
    private String title;

    public static DownloadResponse completed(String filename, String downloadUrl, String title) {
        DownloadResponse response = new DownloadResponse();
        response.status = "COMPLETED";
        response.filename = filename;
        response.downloadUrl = downloadUrl;
        response.title = title;
        response.message = "Download completed successfully";
        return response;
    }

    public static DownloadResponse failed(String message) {
        DownloadResponse response = new DownloadResponse();
        response.status = "FAILED";
        response.message = message;
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
