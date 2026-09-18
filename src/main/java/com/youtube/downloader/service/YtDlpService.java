package com.youtube.downloader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youtube.downloader.config.DownloaderProperties;
import com.youtube.downloader.dto.FormatInfo;
import com.youtube.downloader.dto.QualityOption;
import com.youtube.downloader.dto.VideoInfoResponse;
import com.youtube.downloader.exception.DownloaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class YtDlpService {

    private static final Logger log = LoggerFactory.getLogger(YtDlpService.class);

    private final DownloaderProperties properties;
    private final ProcessExecutor processExecutor;
    private final FormatSelectionService formatSelectionService;
    private final ObjectMapper objectMapper;

    public YtDlpService(
            DownloaderProperties properties,
            ProcessExecutor processExecutor,
            FormatSelectionService formatSelectionService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.processExecutor = processExecutor;
        this.formatSelectionService = formatSelectionService;
        this.objectMapper = objectMapper;
    }

    public void ensureToolsAvailable(boolean requireFfmpeg) {
        Path ytDlp = resolveConfiguredPath(properties.getYtDlpPath());
        if (!Files.isRegularFile(ytDlp)) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.YT_DLP_MISSING,
                    "yt-dlp not found at '" + ytDlp
                            + "'. Place yt-dlp.exe under tools/yt-dlp/ or set downloader.yt-dlp-path.");
        }

        if (requireFfmpeg) {
            Path ffmpeg = resolveConfiguredPath(properties.getFfmpegPath());
            if (!Files.isRegularFile(ffmpeg)) {
                throw new DownloaderException(
                        DownloaderException.ErrorCode.FFMPEG_MISSING,
                        "FFmpeg not found at '" + ffmpeg
                                + "'. Place ffmpeg.exe under tools/ffmpeg/bin/ or set downloader.ffmpeg-path. "
                                + "FFmpeg is required to merge video/audio streams.");
            }
        }
    }

    public VideoInfoResponse fetchVideoInfo(String validatedUrl) {
        ensureToolsAvailable(false);

        List<String> command = new ArrayList<>();
        command.add(resolveConfiguredPath(properties.getYtDlpPath()).toString());
        command.add("--dump-single-json");
        command.add("--no-download");
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add(validatedUrl);

        log.info("Extracting video metadata");
        ProcessExecutor.ProcessResult result = processExecutor.execute(
                command,
                Duration.ofSeconds(properties.getMetadataTimeoutSeconds()));

        if (!result.isSuccess()) {
            throw mapYtDlpFailure(result, "metadata extraction");
        }

        try {
            Map<String, Object> json = objectMapper.readValue(
                    result.stdout(),
                    new TypeReference<>() {
                    });
            return mapMetadata(json);
        } catch (DownloaderException e) {
            throw e;
        } catch (Exception e) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.MALFORMED_METADATA,
                    "Failed to parse yt-dlp metadata JSON",
                    e);
        }
    }

    public Path download(
            String validatedUrl,
            QualityOption quality,
            Path outputTemplate,
            String expectedFilename) {

        // FFmpeg is required for stream merging and for audio extraction (-x).
        ensureToolsAvailable(true);

        Path ytDlp = resolveConfiguredPath(properties.getYtDlpPath());
        Path ffmpeg = resolveConfiguredPath(properties.getFfmpegPath());
        String formatSelector = formatSelectionService.resolveFormatSelector(quality);

        List<String> command = new ArrayList<>();
        command.add(ytDlp.toString());
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--newline");
        command.add("-f");
        command.add(formatSelector);
        command.add("--ffmpeg-location");
        command.add(ffmpeg.getParent() != null ? ffmpeg.getParent().toString() : ffmpeg.toString());

        if (quality == QualityOption.AUDIO) {
            command.add("-x");
            command.add("--audio-format");
            command.add("m4a");
            command.add("--audio-quality");
            command.add("0");
        } else {
            command.add("--merge-output-format");
            command.add("mp4");
        }

        command.add("-o");
        command.add(outputTemplate.toString());
        command.add(validatedUrl);

        log.info("Starting yt-dlp download with quality {}", quality);
        ProcessExecutor.ProcessResult result = processExecutor.execute(
                command,
                Duration.ofSeconds(properties.getProcessTimeoutSeconds()));

        if (!result.isSuccess()) {
            throw mapYtDlpFailure(result, "download");
        }

        Path expected = outputTemplate.getParent().resolve(expectedFilename);
        if (Files.isRegularFile(expected)) {
            log.info("Download completed: {}", expectedFilename);
            return expected;
        }

        // Fallback: yt-dlp may alter extension slightly; find newest file matching stem
        String stem = stripExtension(expectedFilename);
        try {
            Path dir = outputTemplate.getParent();
            OptionalPath newest = findNewestWithStem(dir, stem);
            if (newest.path() != null) {
                log.info("Download completed (resolved): {}", newest.path().getFileName());
                return newest.path();
            }
        } catch (Exception e) {
            log.debug("Could not resolve downloaded file by stem: {}", e.getMessage());
        }

        throw new DownloaderException(
                DownloaderException.ErrorCode.DOWNLOAD_FAILURE,
                "Download appeared to succeed but output file was not found: " + expectedFilename);
    }

    private VideoInfoResponse mapMetadata(Map<String, Object> json) {
        if (json == null || json.isEmpty()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.MALFORMED_METADATA,
                    "Empty metadata from yt-dlp");
        }

        VideoInfoResponse response = new VideoInfoResponse();
        response.setId(asString(json.get("id")));
        response.setTitle(asString(json.get("title")));
        response.setUploader(firstNonBlank(asString(json.get("uploader")), asString(json.get("channel"))));
        response.setDuration(asInteger(json.get("duration")));
        response.setThumbnail(asString(json.get("thumbnail")));
        response.setWebpageUrl(asString(json.get("webpage_url")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> formats = (List<Map<String, Object>>) json.get("formats");
        List<FormatInfo> simplified = formatSelectionService.simplifyFormats(formats);
        response.setFormats(simplified);
        response.setAvailableQualities(formatSelectionService.availableQualities(simplified));

        log.info("Metadata extraction succeeded for video id={}", response.getId());
        return response;
    }

    private DownloaderException mapYtDlpFailure(ProcessExecutor.ProcessResult result, String operation) {
        String output = result.shortError().toLowerCase(Locale.ROOT);
        if (output.contains("private video")
                || output.contains("login required")
                || output.contains("sign in")
                || output.contains("unavailable")
                || output.contains("video does not exist")
                || output.contains("removed by the uploader")) {
            return new DownloaderException(
                    DownloaderException.ErrorCode.VIDEO_UNAVAILABLE,
                    "Video is unavailable, private, or restricted. This app does not bypass access controls.");
        }
        if (output.contains("requested format is not available")
                || output.contains("format is not available")) {
            return new DownloaderException(
                    DownloaderException.ErrorCode.UNSUPPORTED_FORMAT,
                    "Requested format/quality is not available for this video.");
        }
        return new DownloaderException(
                DownloaderException.ErrorCode.YT_DLP_FAILURE,
                "yt-dlp " + operation + " failed (exit " + result.exitCode() + "): " + result.shortError());
    }

    private Path resolveConfiguredPath(String configured) {
        Path path = Paths.get(configured);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private OptionalPath findNewestWithStem(Path dir, String stem) throws Exception {
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (name.startsWith(stem)) {
                    long modified = Files.getLastModifiedTime(p).toMillis();
                    if (modified > newestTime) {
                        newestTime = modified;
                        newest = p;
                    }
                }
            }
        }
        return new OptionalPath(newest);
    }

    private record OptionalPath(Path path) {
    }
}
