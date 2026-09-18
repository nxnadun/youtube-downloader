package com.youtube.downloader.util;

import com.youtube.downloader.exception.DownloaderException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Produces Windows-safe filenames and blocks path traversal / reserved names.
 */
@Component
public class FilenameSanitizer {

    private static final int MAX_LENGTH = 120;
    private static final Pattern INVALID_CHARS = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_{2,}");
    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    public String sanitize(String rawTitle, String videoId, String extension) {
        String base = rawTitle == null || rawTitle.isBlank() ? "video" : rawTitle.trim();
        base = INVALID_CHARS.matcher(base).replaceAll("_");
        base = base.replace("..", "_");
        base = base.replaceAll("[\\s.]+$", "");
        base = MULTI_UNDERSCORE.matcher(base).replaceAll("_");
        base = base.trim();

        if (base.isBlank()) {
            base = "video";
        }

        String reservedCheck = base.toUpperCase(Locale.ROOT);
        if (RESERVED.contains(reservedCheck)) {
            base = base + "_file";
        }

        String idSuffix = (videoId == null || videoId.isBlank()) ? "unknown" : sanitizeId(videoId);
        String ext = normalizeExtension(extension);

        int maxBase = MAX_LENGTH - idSuffix.length() - ext.length() - 2;
        if (maxBase < 8) {
            maxBase = 8;
        }
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }

        String filename = base + "_" + idSuffix + "." + ext;
        assertSafeFilename(filename);
        return filename;
    }

    public void assertSafeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.PATH_TRAVERSAL,
                    "Filename is empty");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")
                || filename.contains(":") || filename.startsWith(".")) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.PATH_TRAVERSAL,
                    "Invalid filename");
        }
        if (INVALID_CHARS.matcher(filename).find()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.PATH_TRAVERSAL,
                    "Filename contains illegal characters");
        }
    }

    private String sanitizeId(String videoId) {
        return videoId.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "mp4";
        }
        String ext = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isBlank()) {
            return "mp4";
        }
        return ext;
    }
}
