var exec = require("cordova/exec");

function noop() {}


function normalizeDownloadData(data) {
    data = data || {};

    return {
        id: String(data.id || "kopere-download"),
        title: data.title || "Arquivo em download",
        text: data.text || "Baixando...",
        subText: data.subText || "Download offline",
        loaded: Number(data.loaded || 0),
        total: Number(data.total || 0),
        percent: Number(data.percent || 0),
        indeterminate: !!data.indeterminate,
        status: data.status || "downloading",
        channelId: data.channelId || "kopere_offline_downloads",
        channelName: data.channelName || "Downloads offline",
        completeText: data.completeText || "Download concluído",
        errorText: data.errorText || "Erro no download",
        error: data.error || ""
    };
}

function normalizeData(data) {
    data = data || {};

    return {
        title: data.title || "",
        artist: data.artist || "",
        album: data.album || "",
        artwork: data.artwork || "",
        duration: Number(data.duration || 0),
        position: Number(data.position || 0),
        playing: !!data.playing,
        channelId: data.channelId || "kopere_media_playback",
        channelName: data.channelName || "Media playback",
        compactActions: data.compactActions || [0, 1, 2],
        color: data.color || ""
    };
}

module.exports = {
    start: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "start", [normalizeData(data)]);
    },

    update: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "update", [data || {}]);
    },

    stop: function(success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "stop", []);
    },

    hasPermission: function(success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "hasPermission", []);
    },

    requestPermission: function(success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "requestPermission", []);
    },

    startDownload: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "startDownload", [normalizeDownloadData(data)]);
    },

    updateDownload: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "updateDownload", [normalizeDownloadData(data)]);
    },

    completeDownload: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "completeDownload", [normalizeDownloadData(data)]);
    },

    failDownload: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "failDownload", [normalizeDownloadData(data)]);
    },

    cancelDownload: function(data, success, error) {
        exec(success || noop, error || noop, "KopereMediaNotification", "cancelDownload", [normalizeDownloadData(data)]);
    }
};
