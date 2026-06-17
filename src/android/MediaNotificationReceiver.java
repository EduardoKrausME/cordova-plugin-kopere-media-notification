package com.eduardokraus.medianotification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MediaNotificationReceiver extends BroadcastReceiver {
    static final String ACTION_PLAY = "com.eduardokraus.medianotification.PLAY";
    static final String ACTION_PAUSE = "com.eduardokraus.medianotification.PAUSE";
    static final String ACTION_PREVIOUS = "com.eduardokraus.medianotification.PREVIOUS";
    static final String ACTION_NEXT = "com.eduardokraus.medianotification.NEXT";
    static final String ACTION_STOP = "com.eduardokraus.medianotification.STOP";
    static final String ACTION_DISMISSED = "com.eduardokraus.medianotification.DISMISSED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            MediaNotificationPlugin.sendEvent("play", -1L);
        } else if (ACTION_PAUSE.equals(action)) {
            MediaNotificationPlugin.sendEvent("pause", -1L);
        } else if (ACTION_PREVIOUS.equals(action)) {
            MediaNotificationPlugin.sendEvent("previous", -1L);
        } else if (ACTION_NEXT.equals(action)) {
            MediaNotificationPlugin.sendEvent("next", -1L);
        } else if (ACTION_STOP.equals(action)) {
            MediaNotificationPlugin.sendEvent("stop", -1L);
            stopService(context);
        } else if (ACTION_DISMISSED.equals(action)) {
            MediaNotificationPlugin.sendEvent("dismissed", -1L);
            stopService(context);
        }
    }

    private void stopService(Context context) {
        Intent serviceIntent = new Intent(context, MediaNotificationService.class);
        serviceIntent.setAction(MediaNotificationService.ACTION_STOP);
        context.startService(serviceIntent);
    }
}
