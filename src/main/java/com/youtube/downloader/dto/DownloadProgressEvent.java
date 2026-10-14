package com.youtube.downloader.dto;

public class DownloadProgressEvent {

    private String type;
    private String jobId;
    private String status;
    private double percent;
    private String message;
    private String filename;
    private String downloadUrl;
    private String title;

    public static DownloadProgressEvent progress(String jobId, double percent, String message) {
        DownloadProgressEvent event = new DownloadProgressEvent();
        event.type = "progress";
        event.jobId = jobId;
        event.status = "RUNNING";
        event.percent = clamp(percent);
        event.message = message;
        return event;
    }

    public static DownloadProgressEvent completed(String jobId, DownloadResponse response) {
        DownloadProgressEvent event = new DownloadProgressEvent();
        event.type = "completed";
        event.jobId = jobId;
        event.status = "COMPLETED";
        event.percent = 100;
        event.message = response.getMessage();
        event.filename = response.getFilename();
        event.downloadUrl = response.getDownloadUrl();
        event.title = response.getTitle();
        return event;
    }

    public static DownloadProgressEvent error(String jobId, String message) {
        DownloadProgressEvent event = new DownloadProgressEvent();
        // Named "failed" (not "error") to avoid clashing with EventSource connection errors.
        event.type = "failed";
        event.jobId = jobId;
        event.status = "FAILED";
        event.percent = 0;
        event.message = message;
        return event;
    }

    private static double clamp(double percent) {
        if (percent < 0) {
            return 0;
        }
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = percent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
