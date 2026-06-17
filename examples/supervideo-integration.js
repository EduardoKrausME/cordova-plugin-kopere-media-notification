(function() {
    var notificationStarted = false;
    var updateTimer = null;

    function hasPlugin() {
        return window.KopereMediaNotification && window.cordova && cordova.platformId === "android";
    }

    function toMillis(value) {
        value = Number(value || 0);
        if (!isFinite(value) || value < 0) {
            return 0;
        }
        return Math.round(value * 1000);
    }

    function findVideo() {
        return document.querySelector("video");
    }

    function getTitle() {
        var title = document.querySelector("h1, .page-title, .activity-title, [data-supervideo-title]");
        if (title && title.textContent) {
            return title.textContent.trim();
        }
        return document.title || "Vídeo";
    }

    function getCourseName() {
        var course = document.querySelector(".breadcrumb li:last-child, [data-course-name]");
        if (course && course.textContent) {
            return course.textContent.trim();
        }
        return "Moodle";
    }

    function getArtwork() {
        var image = document.querySelector("[data-supervideo-thumb], .supervideo-thumb img, video[poster]");
        if (!image) {
            return "";
        }

        if (image.getAttribute("poster")) {
            return image.getAttribute("poster");
        }
        return image.getAttribute("src") || "";
    }

    function buildData(video) {
        return {
            title: getTitle(),
            artist: getCourseName(),
            album: "Kopere APP Mobile",
            artwork: getArtwork(),
            duration: toMillis(video.duration),
            position: toMillis(video.currentTime),
            playing: !video.paused,
            channelName: "Reprodução de vídeo"
        };
    }

    function handleAction(video, event) {
        if (!event || !event.action) {
            return;
        }

        if (event.action === "play") {
            video.play();
        } else if (event.action === "pause") {
            video.pause();
        } else if (event.action === "previous") {
            video.currentTime = Math.max(0, video.currentTime - 15);
        } else if (event.action === "next") {
            video.currentTime = Math.min(video.duration || 0, video.currentTime + 15);
        } else if (event.action === "seekto" && typeof event.position !== "undefined") {
            video.currentTime = event.position / 1000;
        } else if (event.action === "stop" || event.action === "dismissed") {
            video.pause();
            stopMediaNotification();
        }
    }

    function updateMediaNotification(video) {
        if (!hasPlugin() || !notificationStarted || !video) {
            return;
        }

        KopereMediaNotification.update({
            duration: toMillis(video.duration),
            position: toMillis(video.currentTime),
            playing: !video.paused
        });
    }

    function startMediaNotification(video) {
        if (!hasPlugin() || notificationStarted || !video) {
            return;
        }

        notificationStarted = true;

        KopereMediaNotification.start(buildData(video), function(event) {
            handleAction(video, event);
        }, function(error) {
            console.error("KopereMediaNotification error", error);
        });

        if (updateTimer) {
            clearInterval(updateTimer);
        }

        updateTimer = setInterval(function() {
            updateMediaNotification(video);
        }, 1000);
    }

    function stopMediaNotification() {
        if (!hasPlugin() || !notificationStarted) {
            return;
        }

        notificationStarted = false;
        if (updateTimer) {
            clearInterval(updateTimer);
            updateTimer = null;
        }

        KopereMediaNotification.stop();
    }

    document.addEventListener("deviceready", function() {
        var video = findVideo();
        if (!video) {
            return;
        }

        KopereMediaNotification.requestPermission(function() {
            video.addEventListener("play", function() {
                startMediaNotification(video);
                updateMediaNotification(video);
            });

            video.addEventListener("pause", function() {
                updateMediaNotification(video);
            });

            video.addEventListener("ended", function() {
                stopMediaNotification();
            });

            video.addEventListener("loadedmetadata", function() {
                updateMediaNotification(video);
            });
        });
    });
})();
