var exec = require("cordova/exec");

function noop() {}

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
    }
};
