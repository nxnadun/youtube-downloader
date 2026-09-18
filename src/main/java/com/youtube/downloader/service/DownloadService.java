package com.youtube.downloader.service;

import com.youtube.downloader.dto.DownloadRequest;
import com.youtube.downloader.dto.DownloadResponse;
import com.youtube.downloader.dto.QualityOption;
import com.youtube.downloader.dto.VideoInfoRequest;
import com.youtube.downloader.dto.VideoInfoResponse;
import com.youtube.downloader.exception.DownloaderException;
import com.youtube.downloader.util.FilenameSanitizer;
import com.youtube.downloader.util.YoutubeUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates metadata lookup and downloads.
 * Structured so async job support can be added later without changing controllers much.
 */
@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final YoutubeUrlValidator urlValidator;
    private final YtDlpService ytDlpService;
    private final FormatSelectionService formatSelectionService;
    private final FileService fileService;
    private final FilenameSanitizer filenameSanitizer;

    public DownloadService(
            YoutubeUrlValidator urlValidator,
            YtDlpService ytDlpService,
            FormatSelectionService formatSelectionService,
            FileService fileService,
            FilenameSanitizer filenameSanitizer) {
        this.urlValidator = urlValidator;
        this.ytDlpService = ytDlpService;
        this.formatSelectionService = formatSelectionService;
        this.fileService = fileService;
        this.filenameSanitizer = filenameSanitizer;
    }

    public VideoInfoResponse getVideoInfo(VideoInfoRequest request) {
        String url = urlValidator.validateAndNormalize(request.getUrl());
        log.info("Video info requested");
        return ytDlpService.fetchVideoInfo(url);
    }

    public DownloadResponse download(DownloadRequest request) {
        String url = urlValidator.validateAndNormalize(request.getUrl());
        QualityOption quality = request.getQuality();
        if (quality == null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_FORMAT,
                    "Quality is required");
        }

        log.info("Download requested with quality={}", quality);

        VideoInfoResponse info = ytDlpService.fetchVideoInfo(url);
        String extension = formatSelectionService.resolveOutputExtension(quality);
        String safeFilename = filenameSanitizer.sanitize(info.getTitle(), info.getId(), extension);

        Path downloadDir = fileService.getDownloadDirectory();
        Path target = fileService.resolveSafeFile(safeFilename);

        // yt-dlp output template uses the sanitized name without extension; extension is appended by yt-dlp
        String stem = safeFilename.substring(0, safeFilename.lastIndexOf('.'));
        Path outputTemplate = downloadDir.resolve(stem + ".%(ext)s");

        try {
            if (Files.exists(target)) {
                Files.delete(target);
            }
        } catch (Exception e) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.DISK_ERROR,
                    "Cannot prepare output file: " + e.getMessage(),
                    e);
        }

        Path downloaded = ytDlpService.download(url, quality, outputTemplate, safeFilename);
        String actualFilename = downloaded.getFileName().toString();
        filenameSanitizer.assertSafeFilename(actualFilename);

        // Ensure final file stays inside downloads dir
        fileService.resolveSafeFile(actualFilename);

        log.info("Download completed successfully: {}", actualFilename);
        return DownloadResponse.completed(
                actualFilename,
                "/api/downloads/" + actualFilename,
                info.getTitle());
    }
}
