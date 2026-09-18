package com.youtube.downloader.service;

import com.youtube.downloader.dto.FormatInfo;
import com.youtube.downloader.dto.QualityOption;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps logical qualities to safe internal yt-dlp format selectors
 * and simplifies raw format lists for the UI.
 */
@Service
public class FormatSelectionService {

    public String resolveFormatSelector(QualityOption quality) {
        return switch (quality) {
            case BEST -> "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
            case P1080 -> "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]";
            case P720 -> "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]";
            case P480 -> "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]";
            case P360 -> "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360][ext=mp4]/best[height<=360]";
            case AUDIO -> "bestaudio[ext=m4a]/bestaudio/best";
        };
    }

    public String resolveOutputExtension(QualityOption quality) {
        return quality == QualityOption.AUDIO ? "m4a" : "mp4";
    }

    public List<FormatInfo> simplifyFormats(List<Map<String, Object>> rawFormats) {
        if (rawFormats == null || rawFormats.isEmpty()) {
            return List.of();
        }

        Map<String, FormatInfo> unique = new LinkedHashMap<>();
        for (Map<String, Object> raw : rawFormats) {
            FormatInfo info = toFormatInfo(raw);
            if (info == null) {
                continue;
            }
            String key = buildKey(info);
            FormatInfo existing = unique.get(key);
            if (existing == null || prefer(info, existing)) {
                unique.put(key, info);
            }
        }

        List<FormatInfo> result = new ArrayList<>(unique.values());
        result.sort(Comparator
                .comparing((FormatInfo f) -> f.getHeight() == null ? 0 : f.getHeight(), Comparator.reverseOrder())
                .thenComparing(f -> f.getFilesize() == null ? 0L : f.getFilesize(), Comparator.reverseOrder()));

        if (result.size() > 24) {
            return result.subList(0, 24);
        }
        return result;
    }

    public List<QualityOption> availableQualities(List<FormatInfo> formats) {
        Set<QualityOption> options = new TreeSet<>(Comparator.comparingInt(this::qualityOrder));
        options.add(QualityOption.BEST);
        options.add(QualityOption.AUDIO);

        int maxHeight = formats.stream()
                .map(FormatInfo::getHeight)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        if (maxHeight >= 1080) {
            options.add(QualityOption.P1080);
        }
        if (maxHeight >= 720) {
            options.add(QualityOption.P720);
        }
        if (maxHeight >= 480) {
            options.add(QualityOption.P480);
        }
        if (maxHeight >= 360 || maxHeight == 0) {
            options.add(QualityOption.P360);
        }

        return new ArrayList<>(options);
    }

    private int qualityOrder(QualityOption option) {
        return switch (option) {
            case BEST -> 0;
            case P1080 -> 1;
            case P720 -> 2;
            case P480 -> 3;
            case P360 -> 4;
            case AUDIO -> 5;
        };
    }

    private FormatInfo toFormatInfo(Map<String, Object> raw) {
        String vcodec = asString(raw.get("vcodec"));
        String acodec = asString(raw.get("acodec"));
        boolean hasVideo = vcodec != null && !"none".equalsIgnoreCase(vcodec);
        boolean hasAudio = acodec != null && !"none".equalsIgnoreCase(acodec);

        if (!hasVideo && !hasAudio) {
            return null;
        }

        // Prefer progressive or common video formats; skip obscure storyboard images
        String protocol = asString(raw.get("protocol"));
        if (protocol != null && protocol.toLowerCase(Locale.ROOT).contains("mhtml")) {
            return null;
        }

        FormatInfo info = new FormatInfo();
        info.setFormatId(asString(raw.get("format_id")));
        info.setExtension(asString(raw.get("ext")));
        info.setResolution(asString(raw.get("resolution")));
        info.setHeight(asInteger(raw.get("height")));
        info.setFps(asDouble(raw.get("fps")));
        info.setVideoCodec(hasVideo ? vcodec : null);
        info.setAudioCodec(hasAudio ? acodec : null);
        info.setFilesize(asLong(raw.get("filesize")) != null
                ? asLong(raw.get("filesize"))
                : asLong(raw.get("filesize_approx")));
        info.setNote(asString(raw.get("format_note")));

        if (info.getResolution() == null && info.getHeight() != null) {
            Integer width = asInteger(raw.get("width"));
            if (width != null) {
                info.setResolution(width + "x" + info.getHeight());
            } else {
                info.setResolution(info.getHeight() + "p");
            }
        }
        if (!hasVideo && hasAudio) {
            info.setResolution("audio only");
        }
        return info;
    }

    private String buildKey(FormatInfo info) {
        return Objects.toString(info.getHeight(), "na") + "|"
                + Objects.toString(info.getExtension(), "na") + "|"
                + (info.getVideoCodec() == null ? "a" : "v") + "|"
                + Objects.toString(info.getNote(), "");
    }

    private boolean prefer(FormatInfo candidate, FormatInfo existing) {
        long cSize = candidate.getFilesize() == null ? 0L : candidate.getFilesize();
        long eSize = existing.getFilesize() == null ? 0L : existing.getFilesize();
        return cSize > eSize;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
