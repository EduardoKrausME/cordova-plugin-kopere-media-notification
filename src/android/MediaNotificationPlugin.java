package com.eduardokraus.medianotification;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MediaNotificationPlugin extends CordovaPlugin {
    private static final int REQUEST_POST_NOTIFICATIONS = 7401;
    private static final int DOWNLOAD_NOTIFICATION_BASE_ID = 20000;
    private static final int DOWNLOAD_NOTIFICATION_RANGE = 40000;
    private static final int DOWNLOAD_REQUEST_CODE_BASE = 26000;
    private static final String DOWNLOAD_DEFAULT_CHANNEL_ID = "kopere_offline_downloads";
    private static final String DOWNLOAD_DEFAULT_CHANNEL_NAME = "Downloads offline";
    private static CallbackContext eventCallback;

    private CallbackContext permissionCallback;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("start".equals(action)) {
            JSONObject data = args.optJSONObject(0);
            start(data, callbackContext);
            return true;
        }

        if ("update".equals(action)) {
            JSONObject data = args.optJSONObject(0);
            update(data, callbackContext);
            return true;
        }

        if ("stop".equals(action)) {
            stop(callbackContext);
            return true;
        }

        if ("hasPermission".equals(action)) {
            callbackContext.success(hasNotificationPermission() ? 1 : 0);
            return true;
        }

        if ("requestPermission".equals(action)) {
            requestNotificationPermission(callbackContext);
            return true;
        }

        if ("startDownload".equals(action) || "updateDownload".equals(action)) {
            showDownloadNotification(args.optJSONObject(0), "downloading", callbackContext);
            return true;
        }

        if ("completeDownload".equals(action)) {
            showDownloadNotification(args.optJSONObject(0), "complete", callbackContext);
            return true;
        }

        if ("failDownload".equals(action)) {
            showDownloadNotification(args.optJSONObject(0), "error", callbackContext);
            return true;
        }

        if ("cancelDownload".equals(action)) {
            cancelDownloadNotification(args.optJSONObject(0), callbackContext);
            return true;
        }

        return false;
    }

    private void start(JSONObject data, CallbackContext callbackContext) {
        eventCallback = callbackContext;

        Context context = getApplicationContext();
        Intent intent = new Intent(context, MediaNotificationService.class);
        intent.setAction(MediaNotificationService.ACTION_START);
        intent.putExtra(MediaNotificationService.EXTRA_DATA, data == null ? "{}" : data.toString());
        ContextCompat.startForegroundService(context, intent);

        sendEvent("ready", -1L);
    }

    private void update(JSONObject data, CallbackContext callbackContext) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, MediaNotificationService.class);
        intent.setAction(MediaNotificationService.ACTION_UPDATE);
        intent.putExtra(MediaNotificationService.EXTRA_DATA, data == null ? "{}" : data.toString());
        ContextCompat.startForegroundService(context, intent);
        callbackContext.success();
    }

    private void stop(CallbackContext callbackContext) {
        Context context = getApplicationContext();
        Intent intent = new Intent(context, MediaNotificationService.class);
        intent.setAction(MediaNotificationService.ACTION_STOP);
        context.startService(intent);
        callbackContext.success();
    }

    private void showDownloadNotification(JSONObject data, String forcedStatus, CallbackContext callbackContext) {
        data = data == null ? new JSONObject() : data;

        String channelId = emptyFallback(data.optString("channelId", ""), DOWNLOAD_DEFAULT_CHANNEL_ID);
        String channelName = emptyFallback(data.optString("channelName", ""), DOWNLOAD_DEFAULT_CHANNEL_NAME);
        String status = safe(forcedStatus).length() == 0 ? safe(data.optString("status", "downloading")) : forcedStatus;
        boolean downloading = "downloading".equals(status);
        int notificationId = getDownloadNotificationId(data);

        ensureDownloadNotificationChannel(channelId, channelName);

        String title = emptyFallback(data.optString("title", ""), "Arquivo em download");
        String text = emptyFallback(data.optString("text", ""), "Baixando...");
        if ("complete".equals(status)) {
            text = emptyFallback(data.optString("completeText", ""), "Download concluído");
        } else if ("error".equals(status)) {
            text = emptyFallback(data.optString("errorText", ""), "Erro no download");
        }

        String subText = safe(data.optString("subText", ""));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
            .setSmallIcon(getSmallIcon())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setContentIntent(getContentPendingIntent(notificationId))
            .setOnlyAlertOnce(true)
            .setOngoing(downloading)
            .setAutoCancel(!downloading)
            .setLocalOnly(false)
            .setShowWhen(!downloading)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(downloading ? NotificationCompat.CATEGORY_PROGRESS : NotificationCompat.CATEGORY_STATUS)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text));

        if (downloading) {
            int percent = Math.max(0, Math.min(100, data.optInt("percent", 0)));
            boolean indeterminate = data.optBoolean("indeterminate", false) || data.optLong("total", 0L) <= 0L;
            builder.setProgress(100, percent, indeterminate);
        } else {
            builder.setProgress(0, 0, false);
        }

        Notification notification = builder.build();
        try {
            NotificationManagerCompat.from(getApplicationContext()).notify(notificationId, notification);
            callbackContext.success(1);
        } catch (SecurityException e) {
            callbackContext.success(0);
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void cancelDownloadNotification(JSONObject data, CallbackContext callbackContext) {
        try {
            NotificationManagerCompat.from(getApplicationContext()).cancel(getDownloadNotificationId(data));
            callbackContext.success(1);
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void ensureDownloadNotificationChannel(String channelId, String channelName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) getApplicationContext()
            .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        String safeChannelId = emptyFallback(channelId, DOWNLOAD_DEFAULT_CHANNEL_ID);
        NotificationChannel existing = notificationManager.getNotificationChannel(safeChannelId);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
            safeChannelId,
            emptyFallback(channelName, DOWNLOAD_DEFAULT_CHANNEL_NAME),
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Offline download progress");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private int getDownloadNotificationId(JSONObject data) {
        String id = data == null ? "kopere-download" : data.optString("id", "kopere-download");
        int hash = safe(id).hashCode();
        if (hash == Integer.MIN_VALUE) {
            hash = 0;
        }
        return DOWNLOAD_NOTIFICATION_BASE_ID + Math.abs(hash % DOWNLOAD_NOTIFICATION_RANGE);
    }

    private PendingIntent getContentPendingIntent(int notificationId) {
        Intent launchIntent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(
            getApplicationContext().getPackageName()
        );
        if (launchIntent == null) {
            launchIntent = new Intent();
        }
        launchIntent.setPackage(getApplicationContext().getPackageName());
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        return PendingIntent.getActivity(
            getApplicationContext(),
            DOWNLOAD_REQUEST_CODE_BASE + notificationId,
            launchIntent,
            getPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    private int getPendingIntentFlags(int baseFlags) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return baseFlags | PendingIntent.FLAG_IMMUTABLE;
        }
        return baseFlags;
    }

    private int getSmallIcon() {
        int icon = cordova.getActivity().getApplicationInfo().icon;
        if (icon == 0) {
            icon = android.R.drawable.stat_sys_download;
        }
        return icon;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String emptyFallback(String value, String fallback) {
        String safeValue = safe(value).trim();
        return safeValue.length() == 0 ? fallback : safe(fallback);
    }

    private Context getApplicationContext() {
        return cordova.getActivity().getApplicationContext();
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS);
    }

    private void requestNotificationPermission(CallbackContext callbackContext) {
        if (Build.VERSION.SDK_INT < 33 || hasNotificationPermission()) {
            callbackContext.success(1);
            return;
        }

        permissionCallback = callbackContext;
        PermissionHelper.requestPermission(this, REQUEST_POST_NOTIFICATIONS, Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (permissionCallback != null) {
            permissionCallback.success(granted ? 1 : 0);
            permissionCallback = null;
        }
    }

    static synchronized void sendEvent(String action, long position) {
        if (eventCallback == null) {
            return;
        }

        try {
            JSONObject event = new JSONObject();
            event.put("action", action);
            if (position >= 0L) {
                event.put("position", position);
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, event);
            result.setKeepCallback(true);
            eventCallback.sendPluginResult(result);
        } catch (JSONException ignored) {
            // Ignore JSON errors because notification actions must never crash the app.
        }
    }

    @Override
    public void onDestroy() {
        eventCallback = null;
        super.onDestroy();
    }
}
