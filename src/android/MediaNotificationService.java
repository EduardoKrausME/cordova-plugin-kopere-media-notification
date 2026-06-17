package com.eduardokraus.medianotification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaNotificationService extends Service {
    static final String ACTION_START = "com.eduardokraus.medianotification.START";
    static final String ACTION_UPDATE = "com.eduardokraus.medianotification.UPDATE";
    static final String ACTION_STOP = "com.eduardokraus.medianotification.SERVICE_STOP";
    static final String EXTRA_DATA = "data";

    private static final int NOTIFICATION_ID = 19001;
    private static final int REQUEST_CODE_CONTENT = 19010;
    private static final int REQUEST_CODE_PLAY = 19011;
    private static final int REQUEST_CODE_PAUSE = 19012;
    private static final int REQUEST_CODE_PREVIOUS = 19013;
    private static final int REQUEST_CODE_NEXT = 19014;
    private static final int REQUEST_CODE_STOP = 19015;
    private static final int REQUEST_CODE_DISMISSED = 19016;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MediaSessionCompat mediaSession;
    private MediaNotificationData currentData = new MediaNotificationData();
    private Bitmap artworkBitmap;
    private String loadedArtwork = "";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlaybackNotification();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
            applyData(intent.getStringExtra(EXTRA_DATA));
            ensureMediaSession();
            updateMediaSession();
            showNotification();
            updateArtworkIfNeeded();
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void applyData(String dataJson) {
        try {
            JSONObject json = new JSONObject(dataJson == null ? "{}" : dataJson);
            currentData.apply(json);
            if (currentData.position > currentData.duration && currentData.duration > 0L) {
                currentData.position = currentData.duration;
            }
        } catch (Exception ignored) {
            // Keep the previous notification data if invalid JSON is received.
        }
    }

    private void ensureMediaSession() {
        if (mediaSession != null) {
            return;
        }

        mediaSession = new MediaSessionCompat(this, "KopereMediaSession");
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                MediaNotificationPlugin.sendEvent("play", -1L);
            }

            @Override
            public void onPause() {
                MediaNotificationPlugin.sendEvent("pause", -1L);
            }

            @Override
            public void onSkipToPrevious() {
                MediaNotificationPlugin.sendEvent("previous", -1L);
            }

            @Override
            public void onSkipToNext() {
                MediaNotificationPlugin.sendEvent("next", -1L);
            }

            @Override
            public void onSeekTo(long pos) {
                MediaNotificationPlugin.sendEvent("seekto", Math.max(0L, pos));
            }

            @Override
            public void onStop() {
                MediaNotificationPlugin.sendEvent("stop", -1L);
            }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaSession() {
        if (mediaSession == null) {
            return;
        }

        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, safe(currentData.title))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, safe(currentData.artist))
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, safe(currentData.album))
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentData.duration);

        if (artworkBitmap != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap);
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap);
        }

        mediaSession.setMetadata(metadata.build());

        long actions = PlaybackStateCompat.ACTION_PLAY |
            PlaybackStateCompat.ACTION_PAUSE |
            PlaybackStateCompat.ACTION_PLAY_PAUSE |
            PlaybackStateCompat.ACTION_SEEK_TO |
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
            PlaybackStateCompat.ACTION_STOP;

        int state = currentData.playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        float speed = currentData.playing ? 1.0f : 0.0f;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, currentData.position, speed, SystemClock.elapsedRealtime())
            .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void showNotification() {
        ensureNotificationChannel();

        PendingIntent contentIntent = getContentPendingIntent();
        PendingIntent previousIntent = getReceiverPendingIntent(
            MediaNotificationReceiver.ACTION_PREVIOUS,
            REQUEST_CODE_PREVIOUS
        );
        PendingIntent playPauseIntent = getReceiverPendingIntent(
            currentData.playing ? MediaNotificationReceiver.ACTION_PAUSE : MediaNotificationReceiver.ACTION_PLAY,
            currentData.playing ? REQUEST_CODE_PAUSE : REQUEST_CODE_PLAY
        );
        PendingIntent nextIntent = getReceiverPendingIntent(
            MediaNotificationReceiver.ACTION_NEXT,
            REQUEST_CODE_NEXT
        );
        PendingIntent stopIntent = getReceiverPendingIntent(
            MediaNotificationReceiver.ACTION_STOP,
            REQUEST_CODE_STOP
        );
        PendingIntent dismissedIntent = getReceiverPendingIntent(
            MediaNotificationReceiver.ACTION_DISMISSED,
            REQUEST_CODE_DISMISSED
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, currentData.channelId)
            .setSmallIcon(getSmallIcon())
            .setContentTitle(emptyFallback(currentData.title, "Reproduzindo mídia"))
            .setContentText(currentData.artist)
            .setSubText(currentData.album)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissedIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(currentData.playing)
            .setLocalOnly(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", previousIntent)
            .addAction(
                currentData.playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                currentData.playing ? "Pausar" : "Reproduzir",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Próximo", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Fechar", stopIntent)
            .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession == null ? null : mediaSession.getSessionToken())
                .setShowActionsInCompactView(currentData.compactActions)
                .setCancelButtonIntent(stopIntent)
                .setShowCancelButton(true)
            );

        if (artworkBitmap != null) {
            builder.setLargeIcon(artworkBitmap);
        }

        if (currentData.duration > 0L) {
            int max = (int) Math.min(Integer.MAX_VALUE, currentData.duration / 1000L);
            int progress = (int) Math.min(Integer.MAX_VALUE, currentData.position / 1000L);
            builder.setProgress(max, progress, false);
        }

        int parsedColor = parseColor(currentData.color);
        if (parsedColor != Color.TRANSPARENT) {
            builder.setColor(parsedColor);
            builder.setColorized(false);
        }

        Notification notification = builder.build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            startForeground(NOTIFICATION_ID, notification);
        }

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // The foreground service already attempted to publish the media notification.
        }
    }

    private void updateArtworkIfNeeded() {
        final String artwork = safe(currentData.artwork);
        if (artwork.length() == 0) {
            artworkBitmap = null;
            loadedArtwork = "";
            updateMediaSession();
            return;
        }

        if (artwork.equals(loadedArtwork)) {
            return;
        }

        loadedArtwork = artwork;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = loadBitmap(artwork);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!artwork.equals(safe(currentData.artwork))) {
                            return;
                        }

                        artworkBitmap = bitmap;
                        updateMediaSession();
                        showNotification();
                    }
                });
            }
        });
    }

    private Bitmap loadBitmap(String source) {
        InputStream inputStream = null;
        HttpURLConnection connection = null;
        try {
            if (source.startsWith("content://")) {
                inputStream = getContentResolver().openInputStream(Uri.parse(source));
            } else if (source.startsWith("file://")) {
                inputStream = getContentResolver().openInputStream(Uri.parse(source));
            } else {
                URL url = new URL(source);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                inputStream = connection.getInputStream();
            }

            if (inputStream == null) {
                return null;
            }

            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            Bitmap bitmap = BitmapFactory.decodeStream(bufferedInputStream);
            if (bitmap == null) {
                return null;
            }

            return scaleBitmap(bitmap, 512);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
                // Ignore close errors.
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Bitmap scaleBitmap(Bitmap bitmap, int maxSize) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= maxSize && height <= maxSize) {
            return bitmap;
        }

        float scale = Math.min((float) maxSize / (float) width, (float) maxSize / (float) height);
        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        NotificationChannel existing = notificationManager.getNotificationChannel(currentData.channelId);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
            currentData.channelId,
            emptyFallback(currentData.channelName, "Media playback"),
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Media playback controls");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private PendingIntent getContentPendingIntent() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent == null) {
            launchIntent = new Intent();
        }
        launchIntent.setPackage(getPackageName());
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_CONTENT,
            launchIntent,
            getPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );
    }

    private PendingIntent getReceiverPendingIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MediaNotificationReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
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
        int icon = getApplicationInfo().icon;
        if (icon == 0) {
            icon = android.R.drawable.ic_media_play;
        }
        return icon;
    }

    private int parseColor(String color) {
        try {
            if (color == null || color.trim().length() == 0) {
                return Color.TRANSPARENT;
            }
            return Color.parseColor(color);
        } catch (Exception ignored) {
            return Color.TRANSPARENT;
        }
    }

    private void stopPlaybackNotification() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
            // Ignore cancel errors.
        }

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stopPlaybackNotification();
        executor.shutdownNow();
        super.onDestroy();
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private String emptyFallback(String value, String fallback) {
        String safeValue = safe(value).trim();
        return safeValue.length() == 0 ? fallback : safeValue;
    }
}
