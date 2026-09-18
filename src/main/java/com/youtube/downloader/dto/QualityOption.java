package com.youtube.downloader.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum QualityOption {
    BEST("BEST"),
    P1080("1080P"),
    P720("720P"),
    P480("480P"),
    P360("360P"),
    AUDIO("AUDIO");

    private final String jsonValue;

    QualityOption(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static QualityOption from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(" ", "");
        return switch (normalized) {
            case "BEST" -> BEST;
            case "1080P", "1080", "P1080" -> P1080;
            case "720P", "720", "P720" -> P720;
            case "480P", "480", "P480" -> P480;
            case "360P", "360", "P360" -> P360;
            case "AUDIO", "AUDIOONLY", "AUDIO_ONLY" -> AUDIO;
            default -> throw new IllegalArgumentException("Unsupported quality: " + value);
        };
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }
}
