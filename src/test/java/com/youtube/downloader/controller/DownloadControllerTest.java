package com.youtube.downloader.controller;

import com.youtube.downloader.dto.DownloadResponse;
import com.youtube.downloader.dto.QualityOption;
import com.youtube.downloader.dto.VideoInfoResponse;
import com.youtube.downloader.exception.DownloaderException;
import com.youtube.downloader.exception.GlobalExceptionHandler;
import com.youtube.downloader.service.DownloadService;
import com.youtube.downloader.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DownloadController.class)
@Import(GlobalExceptionHandler.class)
class DownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DownloadService downloadService;

    @MockBean
    private FileService fileService;

    @Test
    void videoInfoReturnsPayload() throws Exception {
        VideoInfoResponse info = new VideoInfoResponse();
        info.setId("abc");
        info.setTitle("Demo");
        info.setUploader("Channel");
        info.setDuration(120);
        info.setAvailableQualities(List.of(QualityOption.BEST, QualityOption.P720));
        when(downloadService.getVideoInfo(any())).thenReturn(info);

        mockMvc.perform(post("/api/video/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Demo"))
                .andExpect(jsonPath("$.id").value("abc"));
    }

    @Test
    void videoInfoRejectsBlankUrl() throws Exception {
        mockMvc.perform(post("/api/video/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadReturnsCompleted() throws Exception {
        when(downloadService.download(any())).thenReturn(
                DownloadResponse.completed("demo_abc.mp4", "/api/downloads/demo_abc.mp4", "Demo"));

        mockMvc.perform(post("/api/video/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\",\"quality\":\"720P\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.filename").value("demo_abc.mp4"));
    }

    @Test
    void downloadMapsServiceErrors() throws Exception {
        when(downloadService.download(any())).thenThrow(
                new DownloaderException(
                        DownloaderException.ErrorCode.UNSUPPORTED_URL,
                        "Only YouTube URLs are supported"));

        mockMvc.perform(post("/api/video/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/x\",\"quality\":\"BEST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILED"));
    }
}
