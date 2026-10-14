package com.youtube.downloader.service;

import com.youtube.downloader.exception.DownloaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Reusable ProcessBuilder wrapper. Never accepts shell strings — only discrete args.
 */
@Service
public class ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutor.class);
    private static final int MAX_CAPTURE_BYTES = 32 * 1024 * 1024;
    private static final long OUTPUT_JOIN_SECONDS = 60;

    public ProcessResult execute(List<String> command, Duration timeout) {
        return execute(command, timeout, null);
    }

    /**
     * @param lineListener optional callback invoked for each stdout/stderr line (streams merged when set)
     */
    public ProcessResult execute(List<String> command, Duration timeout, Consumer<String> lineListener) {
        return executeInternal(command, timeout, lineListener, null);
    }

    /**
     * Runs a process with stdout redirected to a file (avoids truncating large JSON dumps).
     * Stderr is still captured in memory (drained fully).
     */
    public ProcessResult executeWithStdoutFile(List<String> command, Duration timeout, Path stdoutFile) {
        if (stdoutFile == null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Stdout file path is required");
        }
        return executeInternal(command, timeout, null, stdoutFile);
    }

    private ProcessResult executeInternal(
            List<String> command,
            Duration timeout,
            Consumer<String> lineListener,
            Path stdoutFile) {
        if (command == null || command.isEmpty()) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Process command is empty");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        boolean streamLines = lineListener != null;
        boolean redirectStdout = stdoutFile != null;
        builder.redirectErrorStream(streamLines);

        if (redirectStdout) {
            builder.redirectOutput(stdoutFile.toFile());
        }

        Process process = null;
        try {
            log.debug("Starting process: {}", sanitizeForLog(command));
            process = builder.start();

            Process finalProcess = process;
            CompletableFuture<String> stdoutFuture;
            CompletableFuture<String> stderrFuture;

            if (streamLines) {
                stdoutFuture = CompletableFuture.supplyAsync(
                        () -> readLines(finalProcess.getInputStream(), lineListener));
                stderrFuture = CompletableFuture.completedFuture("");
            } else if (redirectStdout) {
                stdoutFuture = CompletableFuture.completedFuture("");
                stderrFuture = CompletableFuture.supplyAsync(
                        () -> readLimited(finalProcess.getErrorStream()));
            } else {
                stdoutFuture = CompletableFuture.supplyAsync(
                        () -> readLimited(finalProcess.getInputStream()));
                stderrFuture = CompletableFuture.supplyAsync(
                        () -> readLimited(finalProcess.getErrorStream()));
            }

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new DownloaderException(
                        DownloaderException.ErrorCode.TIMEOUT,
                        "External process timed out after " + timeout.toSeconds() + " seconds");
            }

            String stdout = stdoutFuture.get(OUTPUT_JOIN_SECONDS, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(OUTPUT_JOIN_SECONDS, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            if (redirectStdout) {
                stdout = Files.readString(stdoutFile, StandardCharsets.UTF_8);
            }

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

    private String readLines(InputStream inputStream, Consumer<String> lineListener) {
        StringBuilder captured = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (captured.length() < MAX_CAPTURE_BYTES) {
                    int remaining = MAX_CAPTURE_BYTES - captured.length();
                    if (line.length() + 1 <= remaining) {
                        captured.append(line).append('\n');
                    } else if (remaining > 0) {
                        captured.append(line, 0, remaining);
                    }
                }
                if (lineListener != null) {
                    try {
                        lineListener.accept(line);
                    } catch (Exception e) {
                        log.debug("Progress line listener failed: {}", e.getMessage());
                    }
                }
            }
            return captured.toString();
        } catch (IOException e) {
            return captured.toString();
        }
    }

    /**
     * Captures up to {@link #MAX_CAPTURE_BYTES}, then keeps reading until EOF so the
     * child process is not blocked / broken-piped by a full OS pipe buffer.
     */
    private String readLimited(InputStream inputStream) {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (total < MAX_CAPTURE_BYTES) {
                    int remaining = MAX_CAPTURE_BYTES - total;
                    int toWrite = Math.min(read, remaining);
                    out.write(buffer, 0, toWrite);
                    total += toWrite;
                }
                // Always continue reading to drain the pipe fully.
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
