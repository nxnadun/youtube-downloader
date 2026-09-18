package com.youtube.downloader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DownloadRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL is too long")
    private String url;

    @NotNull(message = "Quality is required")
    private QualityOption quality;

    public DownloadRequest() {
    }

    public DownloadRequest(String url, QualityOption quality) {
        this.url = url;
        this.quality = quality;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public QualityOption getQuality() {
        return quality;
    }

    public void setQuality(QualityOption quality) {
        this.quality = quality;
    }
}
