package com.youtube.downloader.service;

import com.youtube.downloader.exception.DownloaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reusable ProcessBuilder wrapper. Never accepts shell strings — only discrete args.
 */
@Service
public class ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutor.class);
    private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;

    public ProcessResult execute(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Process command is empty");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);

        Process process = null;
        try {
            log.debug("Starting process: {}", sanitizeForLog(command));
            process = builder.start();

            Process finalProcess = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                    () -> readLimited(finalProcess.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                    () -> readLimited(finalProcess.getErrorStream()));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new DownloaderException(
                        DownloaderException.ErrorCode.TIMEOUT,
                        "External process timed out after " + timeout.toSeconds() + " seconds");
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            return new ProcessResult(exitCode, stdout, stderr);
        } catch (DownloaderException e) {
            throw e;
        } catch (TimeoutException e) {
            destroyQuietly(process);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.TIMEOUT,
                    "Timed out waiting for process output",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyQuietly(process);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Process execution interrupted",
                    e);
        } catch (IOException e) {
            destroyQuietly(process);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.YT_DLP_FAILURE,
                    "Failed to start external process: " + e.getMessage(),
                    e);
        } catch (Exception e) {
            destroyQuietly(process);
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Process execution failed: " + e.getMessage(),
                    e);
        } finally {
            destroyQuietly(process);
        }
    }

    private String readLimited(InputStream inputStream) {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                int remaining = MAX_CAPTURE_BYTES - total;
                if (remaining <= 0) {
                    break;
                }
                int toWrite = Math.min(read, remaining);
                out.write(buffer, 0, toWrite);
                total += toWrite;
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void destroyQuietly(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private String sanitizeForLog(List<String> command) {
        if (command.isEmpty()) {
            return "[]";
        }
        // Log executable name only + arg count (avoid huge URLs/noise in normal logs)
        return command.getFirst() + " (+" + (command.size() - 1) + " args)";
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String combinedOutput() {
            StringBuilder sb = new StringBuilder();
            if (stdout != null && !stdout.isBlank()) {
                sb.append(stdout.trim());
            }
            if (stderr != null && !stderr.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(stderr.trim());
            }
            return sb.toString();
        }

        public String shortError() {
            String combined = combinedOutput();
            if (combined.length() > 500) {
                return combined.substring(0, 500) + "...";
            }
            return combined;
        }
    }
}
