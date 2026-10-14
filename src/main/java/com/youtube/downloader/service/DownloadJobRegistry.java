package com.youtube.downloader.service;

import com.youtube.downloader.dto.DownloadProgressEvent;
import com.youtube.downloader.dto.DownloadResponse;
import com.youtube.downloader.exception.DownloaderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory download job state and SSE subscribers.
 */
@Service
public class DownloadJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(DownloadJobRegistry.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long JOB_TTL_MS = 60 * 60 * 1000L;

    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    public void create(String jobId) {
        cleanupExpired();
        jobs.put(jobId, new JobState());
    }

    public SseEmitter subscribe(String jobId) {
        JobState job = requireJob(jobId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        job.emitters.add(emitter);

        emitter.onCompletion(() -> job.emitters.remove(emitter));
        emitter.onTimeout(() -> {
            job.emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(ex -> job.emitters.remove(emitter));

        DownloadProgressEvent snapshot = job.latestEvent.get();
        if (snapshot != null) {
            sendQuietly(emitter, snapshot);
            if ("completed".equals(snapshot.getType()) || "failed".equals(snapshot.getType())) {
                completeQuietly(emitter);
            }
        } else {
            sendQuietly(emitter, DownloadProgressEvent.progress(jobId, 0, "Waiting to start..."));
        }

        return emitter;
    }

    public void updateProgress(String jobId, double percent, String message) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        DownloadProgressEvent event = DownloadProgressEvent.progress(jobId, percent, message);
        job.latestEvent.set(event);
        broadcast(job, event);
    }

    public void complete(String jobId, DownloadResponse response) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        DownloadProgressEvent event = DownloadProgressEvent.completed(jobId, response);
        job.latestEvent.set(event);
        broadcast(job, event);
        completeAll(job);
    }

    public void fail(String jobId, String message) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        DownloadProgressEvent event = DownloadProgressEvent.error(jobId, message);
        job.latestEvent.set(event);
        broadcast(job, event);
        completeAll(job);
    }

    private JobState requireJob(String jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new DownloaderException(
                    DownloaderException.ErrorCode.INTERNAL_ERROR,
                    "Unknown or expired download job: " + jobId);
        }
        return job;
    }

    private void broadcast(JobState job, DownloadProgressEvent event) {
        for (SseEmitter emitter : job.emitters) {
            sendQuietly(emitter, event);
        }
    }

    private void completeAll(JobState job) {
        for (SseEmitter emitter : List.copyOf(job.emitters)) {
            completeQuietly(emitter);
        }
        job.emitters.clear();
    }

    private void sendQuietly(SseEmitter emitter, DownloadProgressEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            log.debug("SSE send failed: {}", e.getMessage());
            completeQuietly(emitter);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // already completed
        }
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minusMillis(JOB_TTL_MS);
        Iterator<Map.Entry<String, JobState>> it = jobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JobState> entry = it.next();
            JobState job = entry.getValue();
            DownloadProgressEvent latest = job.latestEvent.get();
            boolean terminal = latest != null
                    && ("completed".equals(latest.getType()) || "failed".equals(latest.getType()));
            if (job.createdAt.isBefore(cutoff) || (terminal && job.createdAt.isBefore(Instant.now().minusSeconds(300)))) {
                completeAll(job);
                it.remove();
            }
        }
    }

    private static final class JobState {
        private final Instant createdAt = Instant.now();
        private final AtomicReference<DownloadProgressEvent> latestEvent = new AtomicReference<>();
        private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    }
}
