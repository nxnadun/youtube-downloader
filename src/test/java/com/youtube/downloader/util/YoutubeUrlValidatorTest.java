package com.youtube.downloader.util;

import com.youtube.downloader.exception.DownloaderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoutubeUrlValidatorTest {

    private YoutubeUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new YoutubeUrlValidator();
    }

    @Test
    void acceptsWatchUrl() {
        String url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
        assertEquals(url, validator.validateAndNormalize(url));
    }

    @Test
    void acceptsYoutuBeUrl() {
        String url = "https://youtu.be/dQw4w9WgXcQ";
        assertEquals(url, validator.validateAndNormalize(url));
    }

    @Test
    void acceptsShortsUrl() {
        String url = "https://www.youtube.com/shorts/dQw4w9WgXcQ";
        assertEquals(url, validator.validateAndNormalize(url));
    }

    @Test
    void rejectsUnsupportedDomain() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("https://example.com/watch?v=abc123"));
        assertEquals(DownloaderException.ErrorCode.UNSUPPORTED_URL, ex.getErrorCode());
    }

    @Test
    void rejectsLocalFilePath() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("C:\\Videos\\movie.mp4"));
        assertEquals(DownloaderException.ErrorCode.UNSUPPORTED_URL, ex.getErrorCode());
    }

    @Test
    void rejectsFileScheme() {
        assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("file:///C:/temp/video.mp4"));
    }

    @Test
    void rejectsBlank() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("  "));
        assertEquals(DownloaderException.ErrorCode.INVALID_URL, ex.getErrorCode());
    }

    @Test
    void rejectsWatchWithoutVideoId() {
        assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("https://www.youtube.com/watch"));
    }

    @Test
    void acceptsMobileHost() {
        assertDoesNotThrow(() ->
                validator.validateAndNormalize("https://m.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void errorMessageMentionsYoutubeOnly() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> validator.validateAndNormalize("https://vimeo.com/12345"));
        assertTrue(ex.getMessage().toLowerCase().contains("youtube"));
    }
}
