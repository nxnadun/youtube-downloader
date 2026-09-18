package com.youtube.downloader.controller;

import com.youtube.downloader.dto.DownloadRequest;
import com.youtube.downloader.dto.DownloadResponse;
import com.youtube.downloader.dto.VideoInfoRequest;
import com.youtube.downloader.dto.VideoInfoResponse;
import com.youtube.downloader.service.DownloadService;
import com.youtube.downloader.service.FileService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DownloadController {

    private final DownloadService downloadService;
    private final FileService fileService;

    public DownloadController(DownloadService downloadService, FileService fileService) {
        this.downloadService = downloadService;
        this.fileService = fileService;
    }

    @PostMapping("/video/info")
    public ResponseEntity<VideoInfoResponse> videoInfo(@Valid @RequestBody VideoInfoRequest request) {
        return ResponseEntity.ok(downloadService.getVideoInfo(request));
    }

    @PostMapping("/video/download")
    public ResponseEntity<DownloadResponse> download(@Valid @RequestBody DownloadRequest request) {
        return ResponseEntity.ok(downloadService.download(request));
    }

    @GetMapping("/downloads/{filename}")
    public ResponseEntity<Resource> getDownload(@PathVariable String filename) {
        Resource resource = fileService.loadAsResource(filename);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".mp4")) {
            contentType = "video/mp4";
        } else if (lower.endsWith(".m4a") || lower.endsWith(".mp3")) {
            contentType = "audio/mp4";
        } else if (lower.endsWith(".webm")) {
            contentType = "video/webm";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
