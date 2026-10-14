package com.youtube.downloader.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessExecutorStreamingTest {

    private final ProcessExecutor executor = new ProcessExecutor();

    @Test
    void streamsLinesToListener() {
        List<String> lines = new CopyOnWriteArrayList<>();
        ProcessExecutor.ProcessResult result = executor.execute(
                List.of("cmd.exe", "/c", "echo line-one&& echo line-two"),
                Duration.ofSeconds(10),
                lines::add);

        assertEquals(0, result.exitCode());
        assertTrue(lines.stream().anyMatch(l -> l.contains("line-one")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("line-two")));
        assertTrue(result.stdout().toLowerCase().contains("line-one"));
    }

    @Test
    void worksWithoutListener() {
        ProcessExecutor.ProcessResult result = executor.execute(
                List.of("cmd.exe", "/c", "echo plain"),
                Duration.ofSeconds(10),
                null);
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().toLowerCase().contains("plain"));
    }
}
