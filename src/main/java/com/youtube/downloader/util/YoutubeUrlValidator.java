package com.youtube.downloader.util;

import com.youtube.downloader.exception.DownloaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Allowlists only common public YouTube URL forms. Rejects arbitrary domains,
 * file paths, and anything that could enable SSRF or unsafe ProcessBuilder input.
 */
@Component
public class YoutubeUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(YoutubeUrlValidator.class);

    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
            "www.youtu.be"
    );

    private static final Pattern WATCH_PATTERN = Pattern.compile("^/watch$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORTS_PATTERN = Pattern.compile("^/shorts/[A-Za-z0-9_-]{6,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED_PATTERN = Pattern.compile("^/embed/[A-Za-z0-9_-]{6,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIVE_PATTERN = Pattern.compile("^/live/[A-Za-z0-9_-]{6,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTU_BE_PATTERN = Pattern.compile("^/[A-Za-z0-9_-]{6,}$");
    private static final Pattern VIDEO_ID_QUERY = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{6,})(?:&|$)");

    public String validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INVALID_URL,
                    "URL is required");
        }

        String trimmed = rawUrl.trim();
        if (trimmed.length() > 2048) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INVALID_URL,
                    "URL is too long");
        }

        if (looksLikeLocalPath(trimmed)) {
            log.warn("URL validation failed: local path rejected");
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "Local file paths are not allowed. Provide a YouTube URL.");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            log.warn("URL validation failed: malformed URI");
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INVALID_URL,
                    "Malformed URL",
                    e);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "Only http/https YouTube URLs are supported");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "URL host is missing");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(normalizedHost)) {
            log.warn("URL validation failed: unsupported host '{}'", normalizedHost);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "Only YouTube URLs are supported (youtube.com / youtu.be)");
        }

        if (uri.getUserInfo() != null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "URLs with user credentials are not allowed");
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getQuery() == null ? "" : uri.getQuery();

        boolean validPath;
        if (normalizedHost.equals("youtu.be") || normalizedHost.equals("www.youtu.be")) {
            validPath = YOUTU_BE_PATTERN.matcher(path).matches();
        } else {
            validPath = WATCH_PATTERN.matcher(path).matches() && VIDEO_ID_QUERY.matcher(query).find()
                    || SHORTS_PATTERN.matcher(path).matches()
                    || EMBED_PATTERN.matcher(path).matches()
                    || LIVE_PATTERN.matcher(path).matches();
        }

        if (!validPath) {
            log.warn("URL validation failed: unsupported path for host '{}'", normalizedHost);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_URL,
                    "Unsupported YouTube URL format. Use watch, youtu.be, or shorts links.");
        }

        log.info("URL validation succeeded for host '{}'", normalizedHost);
        return trimmed;
    }

    private boolean looksLikeLocalPath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("file:")
                || lower.matches("^[a-z]:\\\\.*")
                || lower.startsWith("\\\\")
                || lower.startsWith("/")
                || lower.contains("..\\")
                || lower.contains("../");
    }
}
