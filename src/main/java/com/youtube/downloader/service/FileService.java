package com.youtube.downloader.service;

import com.youtube.downloader.config.DownloaderProperties;
import com.youtube.downloader.exception.DownloaderException;
import com.youtube.downloader.util.FilenameSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final DownloaderProperties properties;
    private final FilenameSanitizer filenameSanitizer;

    public FileService(DownloaderProperties properties, FilenameSanitizer filenameSanitizer) {
        this.properties = properties;
        this.filenameSanitizer = filenameSanitizer;
    }

    public Path getDownloadDirectory() {
        Path dir = Paths.get(properties.getDownloadDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.DISK_ERROR,
                    "Cannot create downloads directory: " + dir,
                    e);
        }
        return dir;
    }

    public Path resolveSafeFile(String filename) {
        filenameSanitizer.assertSafeFilename(filename);
        Path base = getDownloadDirectory();
        Path resolved = base.resolve(filename).normalize();
        if (!resolved.startsWith(base)) {
            log.warn("Path traversal attempt blocked for filename '{}'", filename);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.PATH_TRAVERSAL,
                    "Access outside downloads directory is not allowed");
        }
        return resolved;
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = resolveSafeFile(filename);
            if (!Files.exists(file) || !Files.isRegularFile(file)) {
                throw new DownloaderException(
                        DownloaderException.ErrorCode.VIDEO_UNAVAILABLE,
                        "File not found: " + filename);
            }
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new DownloaderException(
                        DownloaderException.ErrorCode.DISK_ERROR,
                        "File is not readable: " + filename);
            }
            return resource;
        } catch (DownloaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.DISK_ERROR,
                    "Failed to load file: " + e.getMessage(),
                    e);
        }
    }

    public Optional<Path> findNewestMatching(String prefix) {
        Path dir = getDownloadDirectory();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()));
        } catch (IOException e) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.DISK_ERROR,
                    "Failed to list downloads directory",
                    e);
        }
    }

    public void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("Could not delete temp file {}: {}", path, e.getMessage());
        }
    }
}
