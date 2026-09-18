package com.youtube.downloader.dto;

import java.util.ArrayList;
import java.util.List;

public class VideoInfoResponse {

    private String id;
    private String title;
    private String uploader;
    private Integer duration;
    private String thumbnail;
    private String webpageUrl;
    private List<FormatInfo> formats = new ArrayList<>();
    private List<QualityOption> availableQualities = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getWebpageUrl() {
        return webpageUrl;
    }

    public void setWebpageUrl(String webpageUrl) {
        this.webpageUrl = webpageUrl;
    }

    public List<FormatInfo> getFormats() {
        return formats;
    }

    public void setFormats(List<FormatInfo> formats) {
        this.formats = formats;
    }

    public List<QualityOption> getAvailableQualities() {
        return availableQualities;
    }

    public void setAvailableQualities(List<QualityOption> availableQualities) {
        this.availableQualities = availableQualities;
    }
}
