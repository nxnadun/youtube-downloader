package com.youtube.downloader.service;

import com.youtube.downloader.dto.FormatInfo;
import com.youtube.downloader.dto.QualityOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatSelectionServiceTest {

    private FormatSelectionService service;

    @BeforeEach
    void setUp() {
        service = new FormatSelectionService();
    }

    @Test
    void resolvesSafeSelectors() {
        assertTrue(service.resolveFormatSelector(QualityOption.P720).contains("height<=720"));
        assertTrue(service.resolveFormatSelector(QualityOption.AUDIO).toLowerCase().contains("bestaudio"));
        assertFalse(service.resolveFormatSelector(QualityOption.BEST).contains(";"));
    }

    @Test
    void audioUsesM4aExtension() {
        assertEquals("m4a", service.resolveOutputExtension(QualityOption.AUDIO));
        assertEquals("mp4", service.resolveOutputExtension(QualityOption.P1080));
    }

    @Test
    void simplifiesAndDeduplicatesFormats() {
        List<Map<String, Object>> raw = List.of(
                format("137", "mp4", 1080, "avc1", "none"),
                format("137", "mp4", 1080, "avc1", "none"),
                format("140", "m4a", null, "none", "mp4a"),
                Map.of("protocol", "mhtml", "vcodec", "none", "acodec", "none")
        );

        List<FormatInfo> simplified = service.simplifyFormats(raw);
        assertFalse(simplified.isEmpty());
        assertTrue(simplified.size() <= 24);
        assertTrue(simplified.stream().anyMatch(f -> Integer.valueOf(1080).equals(f.getHeight())));
    }

    @Test
    void availableQualitiesReflectMaxHeight() {
        FormatInfo f1080 = new FormatInfo();
        f1080.setHeight(1080);
        List<QualityOption> options = service.availableQualities(List.of(f1080));
        assertTrue(options.contains(QualityOption.BEST));
        assertTrue(options.contains(QualityOption.P1080));
        assertTrue(options.contains(QualityOption.P720));
        assertTrue(options.contains(QualityOption.AUDIO));
    }

    private Map<String, Object> format(String id, String ext, Integer height, String vcodec, String acodec) {
        Map<String, Object> map = new HashMap<>();
        map.put("format_id", id);
        map.put("ext", ext);
        map.put("height", height);
        map.put("vcodec", vcodec);
        map.put("acodec", acodec);
        map.put("filesize", 1000L);
        return map;
    }
}
