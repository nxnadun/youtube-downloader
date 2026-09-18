package com.youtube.downloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "downloader")
public class DownloaderProperties {

    /**
     * Relative or absolute path to the yt-dlp executable.
     * Overridable via DOWNLOADER_YT_DLP_PATH.
     */
    private String ytDlpPath = "tools/yt-dlp/yt-dlp.exe";

    /**
     * Relative or absolute path to the FFmpeg executable.
     * Overridable via DOWNLOADER_FFMPEG_PATH.
     */
    private String ffmpegPath = "tools/ffmpeg/bin/ffmpeg.exe";

    /**
     * Directory where completed downloads are stored.
     * Overridable via DOWNLOADER_DOWNLOAD_DIRECTORY.
     */
    private String downloadDirectory = "downloads";

    private long processTimeoutSeconds = 600;

    private long metadataTimeoutSeconds = 60;

    public String getYtDlpPath() {
        return ytDlpPath;
    }

    public void setYtDlpPath(String ytDlpPath) {
        this.ytDlpPath = ytDlpPath;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getDownloadDirectory() {
        return downloadDirectory;
    }

    public void setDownloadDirectory(String downloadDirectory) {
        this.downloadDirectory = downloadDirectory;
    }

    public long getProcessTimeoutSeconds() {
        return processTimeoutSeconds;
    }

    public void setProcessTimeoutSeconds(long processTimeoutSeconds) {
        this.processTimeoutSeconds = processTimeoutSeconds;
    }

    public long getMetadataTimeoutSeconds() {
        return metadataTimeoutSeconds;
    }

    public void setMetadataTimeoutSeconds(long metadataTimeoutSeconds) {
        this.metadataTimeoutSeconds = metadataTimeoutSeconds;
    }
}
