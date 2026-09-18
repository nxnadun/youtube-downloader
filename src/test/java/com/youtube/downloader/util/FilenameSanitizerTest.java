package com.youtube.downloader.util;

import com.youtube.downloader.exception.DownloaderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilenameSanitizerTest {

    private FilenameSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new FilenameSanitizer();
    }

    @Test
    void removesWindowsInvalidCharacters() {
        String result = sanitizer.sanitize("My:Video<>Name|Test?.mp4", "abc123", "mp4");
        assertFalse(result.contains(":"));
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains("|"));
        assertFalse(result.contains("?"));
        assertTrue(result.endsWith("_abc123.mp4"));
    }

    @Test
    void blocksPathTraversalSequences() {
        String result = sanitizer.sanitize("../secret", "id1", "mp4");
        assertFalse(result.contains(".."));
        assertTrue(result.contains("id1"));
    }

    @Test
    void handlesReservedWindowsNames() {
        String result = sanitizer.sanitize("CON", "vid", "mp4");
        assertTrue(result.toLowerCase().contains("con_file") || result.startsWith("CON_file"));
    }

    @Test
    void truncatesLongTitles() {
        String longTitle = "a".repeat(300);
        String result = sanitizer.sanitize(longTitle, "xyz", "mp4");
        assertTrue(result.length() <= 130);
    }

    @Test
    void assertSafeFilenameRejectsTraversal() {
        assertThrows(DownloaderException.class, () -> sanitizer.assertSafeFilename("../evil.mp4"));
        assertThrows(DownloaderException.class, () -> sanitizer.assertSafeFilename("folder/file.mp4"));
        assertThrows(DownloaderException.class, () -> sanitizer.assertSafeFilename("folder\\file.mp4"));
    }

    @Test
    void usesFallbackWhenTitleBlank() {
        String result = sanitizer.sanitize("   ", "vid99", "mp4");
        assertEquals("video_vid99.mp4", result);
    }
}
