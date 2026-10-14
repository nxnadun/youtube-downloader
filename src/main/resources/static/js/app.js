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
    const progressWrap = document.getElementById("progressWrap");
    const progressArc = document.getElementById("progressArc");
    const progressFill = document.getElementById("progressFill");
    const progressPercent = document.getElementById("progressPercent");
    const progressBar = progressWrap.querySelector(".progress-bar");

    let activeEventSource = null;

    function setStatus(message, type) {
        statusText.textContent = message;
        statusText.classList.remove("error", "success");
        if (type) {
            statusText.classList.add(type);
        }
    }

    function setProgress(percent) {
        const value = Math.max(0, Math.min(100, Number(percent) || 0));
        const rounded = Math.round(value * 10) / 10;
        progressPercent.textContent = `${rounded}%`;
        progressFill.style.width = `${value}%`;
        progressArc.setAttribute("stroke-dasharray", `${value}, 100`);
        if (progressBar) {
            progressBar.setAttribute("aria-valuenow", String(Math.round(value)));
        }
    }

    function showProgress() {
        progressWrap.classList.remove("hidden");
        setProgress(0);
    }

    function hideProgress() {
        progressWrap.classList.add("hidden");
        setProgress(0);
    }

    function closeEventSource() {
        if (activeEventSource) {
            activeEventSource.close();
            activeEventSource = null;
        }
    }

    function setBusy(busy) {
        downloadBtn.disabled = busy;
        infoBtn.disabled = busy;
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

    function watchProgress(jobId) {
        return new Promise((resolve, reject) => {
            closeEventSource();
            const source = new EventSource(`/api/video/download/${encodeURIComponent(jobId)}/progress`);
            activeEventSource = source;

            const finish = (handler) => (event) => {
                try {
                    const data = JSON.parse(event.data);
                    handler(data);
                } catch (err) {
                    closeEventSource();
                    reject(err);
                }
            };

            source.addEventListener("progress", finish((data) => {
                setProgress(data.percent);
                if (data.message) {
                    setStatus(data.message);
                }
            }));

            source.addEventListener("completed", finish((data) => {
                setProgress(100);
                closeEventSource();
                resolve(data);
            }));

            source.addEventListener("failed", finish((data) => {
                closeEventSource();
                reject(new Error(data.message || "Download failed."));
            }));

            source.onerror = () => {
                if (source.readyState === EventSource.CLOSED) {
                    closeEventSource();
                    reject(new Error("Lost connection while watching download progress."));
                }
            };
        });
    }

    infoBtn.addEventListener("click", async () => {
        const url = urlInput.value.trim();
        if (!url) {
            setStatus("Please paste a YouTube URL.", "error");
            return;
        }

        infoBtn.disabled = true;
        fileLink.classList.add("hidden");
        hideProgress();
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

        setBusy(true);
        fileLink.classList.add("hidden");
        showProgress();
        setStatus("Starting download...");

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

            const started = await response.json();
            if (started.status !== "STARTED" || !started.jobId) {
                throw new Error(started.message || "Failed to start download.");
            }

            const data = await watchProgress(started.jobId);
            if (!data.downloadUrl) {
                throw new Error(data.message || "Download failed.");
            }

            fileLink.href = data.downloadUrl;
            fileLink.textContent = `Download File (${data.filename})`;
            fileLink.classList.remove("hidden");
            setStatus(`Completed: ${data.filename}`, "success");
        } catch (err) {
            hideProgress();
            setStatus(err.message || "Download failed.", "error");
        } finally {
            closeEventSource();
            setBusy(false);
        }
    });
})();
