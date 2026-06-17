package com.eduardokraus.medianotification;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

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
