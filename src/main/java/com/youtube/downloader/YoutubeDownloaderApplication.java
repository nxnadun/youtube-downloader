package com.youtube.downloader;

import com.youtube.downloader.config.DownloaderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DownloaderProperties.class)
public class YoutubeDownloaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(YoutubeDownloaderApplication.class, args);
    }
}
