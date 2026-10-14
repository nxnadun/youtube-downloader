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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Orchestrates metadata lookup and async downloads with progress streaming.
 */
@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final YoutubeUrlValidator urlValidator;
    private final YtDlpService ytDlpService;
    private final FormatSelectionService formatSelectionService;
    private final FileService fileService;
    private final FilenameSanitizer filenameSanitizer;
    private final DownloadJobRegistry jobRegistry;
    private final Executor downloadTaskExecutor;

    public DownloadService(
            YoutubeUrlValidator urlValidator,
            YtDlpService ytDlpService,
            FormatSelectionService formatSelectionService,
            FileService fileService,
            FilenameSanitizer filenameSanitizer,
            DownloadJobRegistry jobRegistry,
            @Qualifier("downloadTaskExecutor") Executor downloadTaskExecutor) {
        this.urlValidator = urlValidator;
        this.ytDlpService = ytDlpService;
        this.formatSelectionService = formatSelectionService;
        this.fileService = fileService;
        this.filenameSanitizer = filenameSanitizer;
        this.jobRegistry = jobRegistry;
        this.downloadTaskExecutor = downloadTaskExecutor;
    }

    public VideoInfoResponse getVideoInfo(VideoInfoRequest request) {
        String url = urlValidator.validateAndNormalize(request.getUrl());
        log.info("Video info requested");
        return ytDlpService.fetchVideoInfo(url);
    }

    /**
     * Validates the request, starts an async download job, and returns immediately with a jobId.
     */
    public DownloadResponse download(DownloadRequest request) {
        String url = urlValidator.validateAndNormalize(request.getUrl());
        QualityOption quality = request.getQuality();
        if (quality == null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_FORMAT,
                    "Quality is required");
        }

        String jobId = UUID.randomUUID().toString();
        jobRegistry.create(jobId);
        jobRegistry.updateProgress(jobId, 0, "Queued...");

        log.info("Download job {} queued with quality={}", jobId, quality);
        downloadTaskExecutor.execute(() -> runDownloadJob(jobId, url, quality));

        return DownloadResponse.started(jobId);
    }

    public SseEmitter subscribeProgress(String jobId) {
        return jobRegistry.subscribe(jobId);
    }

    private void runDownloadJob(String jobId, String url, QualityOption quality) {
        try {
            jobRegistry.updateProgress(jobId, 0, "Fetching video information...");
            VideoInfoResponse info = ytDlpService.fetchVideoInfo(url);
            String extension = formatSelectionService.resolveOutputExtension(quality);
            String safeFilename = filenameSanitizer.sanitize(info.getTitle(), info.getId(), extension);

            Path downloadDir = fileService.getDownloadDirectory();
            Path target = fileService.resolveSafeFile(safeFilename);

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

            jobRegistry.updateProgress(jobId, 1, "Starting download...");
            Path downloaded = ytDlpService.download(
                    url,
                    quality,
                    outputTemplate,
                    safeFilename,
                    (percent, message) -> jobRegistry.updateProgress(jobId, percent, message));

            String actualFilename = downloaded.getFileName().toString();
            filenameSanitizer.assertSafeFilename(actualFilename);
            fileService.resolveSafeFile(actualFilename);

            log.info("Download job {} completed: {}", jobId, actualFilename);
            jobRegistry.complete(
                    jobId,
                    DownloadResponse.completed(
                            actualFilename,
                            "/api/downloads/" + actualFilename,
                            info.getTitle()));
        } catch (DownloaderException e) {
            log.warn("Download job {} failed: {}", jobId, e.getMessage());
            jobRegistry.fail(jobId, e.getMessage());
        } catch (Exception e) {
            log.error("Download job {} failed unexpectedly", jobId, e);
            jobRegistry.fail(jobId, e.getMessage() != null ? e.getMessage() : "Download failed");
        }
    }
}
