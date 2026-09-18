(() => {
    const urlInput = document.getElementById("urlInput");
    const infoBtn = document.getElementById("infoBtn");
    const downloadBtn = document.getElementById("downloadBtn");
    const infoSection = document.getElementById("infoSection");
    const statusText = document.getElementById("statusText");
    const fileLink = document.getElementById("fileLink");
    const thumbnail = document.getElementById("thumbnail");
    const titleEl = document.getElementById("title");
    const uploaderEl = document.getElementById("uploader");
    const durationEl = document.getElementById("duration");
    const formatsBlock = document.getElementById("formatsBlock");
    const formatsList = document.getElementById("formatsList");

    function setStatus(message, type) {
        statusText.textContent = message;
        statusText.classList.remove("error", "success");
        if (type) {
            statusText.classList.add(type);
        }
    }

    function formatDuration(seconds) {
        if (seconds == null || Number.isNaN(Number(seconds))) {
            return "—";
        }
        const total = Math.max(0, Math.floor(Number(seconds)));
        const h = Math.floor(total / 3600);
        const m = Math.floor((total % 3600) / 60);
        const s = total % 60;
        if (h > 0) {
            return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
        }
        return `${m}:${String(s).padStart(2, "0")}`;
    }

    function selectedQuality() {
        const checked = document.querySelector('input[name="quality"]:checked');
        return checked ? checked.value : "BEST";
    }

    function renderFormats(formats) {
        formatsList.innerHTML = "";
        if (!formats || formats.length === 0) {
            formatsBlock.classList.add("hidden");
            return;
        }
        formats.slice(0, 12).forEach((fmt) => {
            const li = document.createElement("li");
            const parts = [
                fmt.resolution || "n/a",
                fmt.extension || "",
                fmt.fps ? `${fmt.fps} fps` : "",
                fmt.note || ""
            ].filter(Boolean);
            li.textContent = parts.join(" · ");
            formatsList.appendChild(li);
        });
        formatsBlock.classList.remove("hidden");
    }

    async function parseError(response) {
        try {
            const data = await response.json();
            return data.message || `Request failed (${response.status})`;
        } catch {
            return `Request failed (${response.status})`;
        }
    }

    infoBtn.addEventListener("click", async () => {
        const url = urlInput.value.trim();
        if (!url) {
            setStatus("Please paste a YouTube URL.", "error");
            return;
        }

        infoBtn.disabled = true;
        fileLink.classList.add("hidden");
        setStatus("Fetching video information...");

        try {
            const response = await fetch("/api/video/info", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ url })
            });

            if (!response.ok) {
                throw new Error(await parseError(response));
            }

            const data = await response.json();
            titleEl.textContent = data.title || "—";
            uploaderEl.textContent = data.uploader || "—";
            durationEl.textContent = formatDuration(data.duration);

            if (data.thumbnail) {
                thumbnail.src = data.thumbnail;
                thumbnail.hidden = false;
            } else {
                thumbnail.removeAttribute("src");
                thumbnail.hidden = true;
            }

            renderFormats(data.formats);
            infoSection.classList.remove("hidden");
            setStatus("Video information loaded. Choose a quality and click Download.", "success");
        } catch (err) {
            setStatus(err.message || "Failed to get video information.", "error");
        } finally {
            infoBtn.disabled = false;
        }
    });

    downloadBtn.addEventListener("click", async () => {
        const url = urlInput.value.trim();
        if (!url) {
            setStatus("Please paste a YouTube URL.", "error");
            return;
        }

        downloadBtn.disabled = true;
        infoBtn.disabled = true;
        fileLink.classList.add("hidden");
        setStatus("Preparing... Downloading may take a while for longer videos.");

        try {
            const response = await fetch("/api/video/download", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    url,
                    quality: selectedQuality()
                })
            });

            if (!response.ok) {
                throw new Error(await parseError(response));
            }

            const data = await response.json();
            if (data.status !== "COMPLETED" || !data.downloadUrl) {
                throw new Error(data.message || "Download failed.");
            }

            fileLink.href = data.downloadUrl;
            fileLink.textContent = `Download File (${data.filename})`;
            fileLink.classList.remove("hidden");
            setStatus(`Completed: ${data.filename}`, "success");
        } catch (err) {
            setStatus(err.message || "Download failed.", "error");
        } finally {
            downloadBtn.disabled = false;
            infoBtn.disabled = false;
        }
    });
})();
