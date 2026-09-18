package com.youtube.downloader.service;

import com.youtube.downloader.exception.DownloaderException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessExecutorTest {

    private final ProcessExecutor executor = new ProcessExecutor();

    @Test
    void capturesSuccessfulOutput() {
        ProcessExecutor.ProcessResult result = executor.execute(
                List.of("cmd.exe", "/c", "echo hello-from-test"),
                Duration.ofSeconds(10));
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().toLowerCase().contains("hello-from-test"));
    }

    @Test
    void timesOutLongRunningProcess() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> executor.execute(
                        List.of("cmd.exe", "/c", "ping -n 10 127.0.0.1 >NUL"),
                        Duration.ofMillis(300)));
        assertEquals(DownloaderException.ErrorCode.TIMEOUT, ex.getErrorCode());
    }

    @Test
    void rejectsEmptyCommand() {
        DownloaderException ex = assertThrows(
                DownloaderException.class,
                () -> executor.execute(List.of(), Duration.ofSeconds(1)));
        assertEquals(DownloaderException.ErrorCode.INTERNAL_ERROR, ex.getErrorCode());
    }

    @Test
    void nonZeroExitIsReturnedNotThrown() {
        ProcessExecutor.ProcessResult result = executor.execute(
                List.of("cmd.exe", "/c", "exit /b 7"),
                Duration.ofSeconds(10));
        assertEquals(7, result.exitCode());
        assertTrue(!result.isSuccess());
    }
}
