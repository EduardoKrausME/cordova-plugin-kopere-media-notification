package com.eduardokraus.medianotification;

import org.json.JSONArray;
import org.json.JSONObject;

final class MediaNotificationData {
    String title = "";
    String artist = "";
    String album = "";
    String artwork = "";
    String channelId = "kopere_media_playback";
    String channelName = "Media playback";
    String color = "";
    long duration = 0L;
    long position = 0L;
    boolean playing = false;
    int[] compactActions = new int[]{0, 1, 2};

    static MediaNotificationData fromJson(JSONObject json) {
        MediaNotificationData data = new MediaNotificationData();
        data.apply(json);
        return data;
    }

    void apply(JSONObject json) {
        if (json == null) {
            return;
        }

        if (json.has("title")) {
            title = json.optString("title", title);
        }
        if (json.has("artist")) {
            artist = json.optString("artist", artist);
        }
        if (json.has("album")) {
            album = json.optString("album", album);
        }
        if (json.has("artwork")) {
            artwork = json.optString("artwork", artwork);
        }
        if (json.has("duration")) {
            duration = Math.max(0L, json.optLong("duration", duration));
        }
        if (json.has("position")) {
            position = Math.max(0L, json.optLong("position", position));
        }
        if (json.has("playing")) {
            playing = json.optBoolean("playing", playing);
        }
        if (json.has("channelId")) {
            String value = json.optString("channelId", channelId);
            if (value != null && value.trim().length() > 0) {
                channelId = value;
            }
        }
        if (json.has("channelName")) {
            String value = json.optString("channelName", channelName);
            if (value != null && value.trim().length() > 0) {
                channelName = value;
            }
        }
        if (json.has("color")) {
            color = json.optString("color", color);
        }
        if (json.has("compactActions")) {
            compactActions = parseCompactActions(json.optJSONArray("compactActions"));
        }
    }

    private int[] parseCompactActions(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new int[]{0, 1, 2};
        }

        int length = Math.min(array.length(), 3);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = array.optInt(i, i);
        }
        return result;
    }
}
