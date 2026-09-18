# YouTube Downloader (Local Windows MVP)

A free **local** Spring Boot web application that uses **yt-dlp** and **FFmpeg** to download YouTube videos you are authorized to download.

> **Legal notice:** Only download videos that you own or are authorized to download. Respect YouTube's Terms of Service and copyright law. This app does **not** bypass DRM, paywalls, private videos, authentication, or other access controls.

## Requirements

- **JDK 21**
- **Apache Maven 3.9+**
- **yt-dlp** Windows executable
- **FFmpeg** Windows build (includes `ffmpeg.exe`)

## 1. Check Java

```powershell
java -version
```

Expected: a Java 21 runtime (for example `openjdk version "21..."`).

If Java is missing, install a JDK 21 distribution (Eclipse Temurin, Microsoft Build of OpenJDK, Oracle JDK, etc.) and ensure `JAVA_HOME` / `PATH` are set.

## 2. Check Maven

```powershell
mvn -version
```

Expected: Maven 3.9+ using Java 21.

## 3. Obtain yt-dlp

Download the official Windows binary from the yt-dlp project releases:

- Project: https://github.com/yt-dlp/yt-dlp
- Releases: https://github.com/yt-dlp/yt-dlp/releases

Place the executable here:

```text
youtube-downloader\tools\yt-dlp\yt-dlp.exe
```

Quick check:

```powershell
.\tools\yt-dlp\yt-dlp.exe --version
```

## 4. Obtain FFmpeg

Download a legitimate Windows build from an official/project distribution such as:

- https://ffmpeg.org/download.html (links to trusted Windows builds)
- or the gyan.dev / BtbN community builds commonly linked from ffmpeg.org

Extract so that `ffmpeg.exe` is available at:

```text
youtube-downloader\tools\ffmpeg\bin\ffmpeg.exe
```

Quick check:

```powershell
.\tools\ffmpeg\bin\ffmpeg.exe -version
```

## 5. Expected directory structure

```text
youtube-downloader/
  pom.xml
  README.md
  downloads/
  tools/
    yt-dlp/
      yt-dlp.exe
    ffmpeg/
      bin/
        ffmpeg.exe
  src/
    main/
      java/com/youtube/downloader/...
      resources/
        application.yml
        templates/index.html
        static/css/style.css
        static/js/app.js
    test/java/com/youtube/downloader/...
```

## 6. Configuration (`application.yml`)

Default values:

```yaml
downloader:
  yt-dlp-path: tools/yt-dlp/yt-dlp.exe
  ffmpeg-path: tools/ffmpeg/bin/ffmpeg.exe
  download-directory: downloads
  process-timeout-seconds: 600
  metadata-timeout-seconds: 60
```

Paths are relative to the process working directory (usually the project root when using `mvn spring-boot:run`).

### Environment variable overrides

Spring Boot relaxed binding supports:

```powershell
$env:DOWNLOADER_YT_DLP_PATH="C:\Tools\yt-dlp\yt-dlp.exe"
$env:DOWNLOADER_FFMPEG_PATH="C:\Tools\ffmpeg\bin\ffmpeg.exe"
$env:DOWNLOADER_DOWNLOAD_DIRECTORY="D:\Media\downloads"
```

Never expose executable paths through the HTTP API — only configuration / environment.

## 7. Start the application

From the project root:

```powershell
cd C:\Projects\seylan\youtube-downloader
mvn spring-boot:run
```

## 8. Open in browser

```text
http://localhost:8080
```

## 9. Package

```powershell
mvn clean package
```

## 10. Run the JAR

```powershell
java -jar target\youtube-downloader-1.0.0-SNAPSHOT.jar
```

If you run the JAR from another directory, either keep relative `tools/` and `downloads/` beside the working directory, or set absolute paths via environment variables.

## API examples

### Get video information

```powershell
curl -X POST http://localhost:8080/api/video/info `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"https://www.youtube.com/watch?v=VIDEO_ID\"}"
```

### Download

```powershell
curl -X POST http://localhost:8080/api/video/download `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"https://www.youtube.com/watch?v=VIDEO_ID\",\"quality\":\"720P\"}"
```

Supported qualities: `BEST`, `1080P`, `720P`, `480P`, `360P`, `AUDIO`.

### Fetch completed file

```text
GET http://localhost:8080/api/downloads/<filename>
```

## Browser workflow

1. Open `http://localhost:8080`
2. Paste a supported YouTube URL
3. Click **Get Video Information**
4. Review title / channel / duration / thumbnail
5. Choose a quality
6. Click **Download**
7. When status shows **Completed**, click **Download File**

## Troubleshooting

| Problem | Fix |
|--------|-----|
| Java not found | Install JDK 21 and add it to `PATH` |
| Maven not found | Install Maven and add `mvn` to `PATH` |
| yt-dlp not found | Place `yt-dlp.exe` under `tools/yt-dlp/` or set `DOWNLOADER_YT_DLP_PATH` |
| FFmpeg not found | Place `ffmpeg.exe` under `tools/ffmpeg/bin/` or set `DOWNLOADER_FFMPEG_PATH` |
| Port 8080 in use | Change `server.port` in `application.yml` or stop the other process |
| Invalid / unsupported URL | Use `youtube.com/watch`, `youtu.be`, or `youtube.com/shorts` links only |
| Download failure | Check yt-dlp/FFmpeg versions, disk space, and that the video is publicly available and authorized |
| Unsupported format | Try another quality (for example `BEST` or `720P`) |
| Private / restricted video | Not supported — this app does not bypass access controls |

## Security notes (v1)

- YouTube URL allowlisting only
- No arbitrary command execution endpoints
- No user-supplied executable paths or yt-dlp format expressions
- Filename sanitization + downloads-directory path traversal protection
- ProcessBuilder with discrete arguments (no `cmd /c` shell wrapping for yt-dlp)

## Tests

```powershell
mvn clean test
```

Tests do **not** download real YouTube videos.

<img width="896" height="903" alt="image" src="https://github.com/user-attachments/assets/75ed6263-fa13-48ca-a7a6-e925128ed7b9" />

