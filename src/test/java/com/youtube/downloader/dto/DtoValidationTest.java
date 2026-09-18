package com.youtube.downloader.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void init() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void videoInfoRequestRequiresUrl() {
        VideoInfoRequest request = new VideoInfoRequest("  ");
        Set<ConstraintViolation<VideoInfoRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void videoInfoRequestAcceptsValidUrl() {
        VideoInfoRequest request = new VideoInfoRequest("https://www.youtube.com/watch?v=abc123XYZ_");
        Set<ConstraintViolation<VideoInfoRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void downloadRequestRequiresQuality() {
        DownloadRequest request = new DownloadRequest();
        request.setUrl("https://www.youtube.com/watch?v=abc123XYZ_");
        Set<ConstraintViolation<DownloadRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void qualityOptionParsesAliases() {
        assertEquals(QualityOption.P720, QualityOption.from("720p"));
        assertEquals(QualityOption.AUDIO, QualityOption.from("audio_only"));
        assertEquals(QualityOption.BEST, QualityOption.from("BEST"));
    }

    @Test
    void qualityOptionRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> QualityOption.from("8K"));
    }
}
