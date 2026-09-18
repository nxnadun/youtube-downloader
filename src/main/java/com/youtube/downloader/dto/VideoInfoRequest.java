package com.youtube.downloader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class VideoInfoRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL is too long")
    private String url;

    public VideoInfoRequest() {
    }

    public VideoInfoRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
