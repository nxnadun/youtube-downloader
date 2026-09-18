package com.youtube.downloader.service;

import com.youtube.downloader.config.DownloaderProperties;
import com.youtube.downloader.exception.DownloaderException;
import com.youtube.downloader.util.FilenameSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileServicePathTraversalTest {

    @TempDir
    Path tempDir;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        DownloaderProperties properties = new DownloaderProperties();
        properties.setDownloadDirectory(tempDir.toString());
        fileService = new FileService(properties, new FilenameSanitizer());
    }

    @Test
    void blocksParentDirectoryEscape() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> fileService.resolveSafeFile("../secret.txt"));
        assertEquals(DownloaderException.ErrorCode.PATH_TRAVERSAL, ex.getErrorCode());
    }

    @Test
    void allowsSafeFilenameInsideDownloads() throws Exception {
        Path file = tempDir.resolve("video_abc.mp4");
        Files.writeString(file, "demo");
        Path resolved = fileService.resolveSafeFile("video_abc.mp4");
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
        assertTrue(fileService.loadAsResource("video_abc.mp4").exists());
    }
}
