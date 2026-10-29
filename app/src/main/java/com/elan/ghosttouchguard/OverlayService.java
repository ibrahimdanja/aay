package com.elan.ghosttouchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Touch watcher overlay service. Detects outside touches across apps using a 1x1 overlay,
 * counting total touches, detecting bursts (3+ touches in 500ms), and auto-locking on severe bursts (4+ in 300ms).
 */
public class OverlayService extends Service {

    public static final String ACTION_WATCHER_UPDATE = "com.elan.ghosttouchguard.WATCHER_UPDATE";
    public static final String EXTRA_TOUCHES = "extra_touches";
    public static final String EXTRA_BURSTS = "extra_bursts";

    private static final String CHANNEL_ID = "gtg_watcher_channel";
    private static final int NOTIFICATION_ID = 1003;

    private static OverlayService instance = null;

    private WindowManager windowManager;
    private View watcher1x1View;

    private static int totalOutsideTouches = 0;
    private static int totalBursts = 0;

    private final Deque<Long> touchTimestamps = new ArrayDeque<>();
    private long lastBurstTimestamp = 0;

    public static boolean isRunning() {
        return instance != null;
    }

    public static int getTotalOutsideTouches() {
        return totalOutsideTouches;
    }

    public static int getTotalBursts() {
        return totalBursts;
    }

    public static void resetStats() {
        totalOutsideTouches = 0;
        totalBursts = 0;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Watched: 0 touches, 0 bursts"));

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initWatcherOverlay();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ghost Touch Guard - Touch Watcher",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors phantom touch burst activity across apps");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String content) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("Touch Watcher Diagnostic")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
    }

    private void initWatcherOverlay() {
        watcher1x1View = new View(this);
        watcher1x1View.setBackgroundColor(Color.TRANSPARENT);

        watcher1x1View.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                handleOutsideTouch();
            }
            return false;
        });

        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                1,
                1,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        windowManager.addView(watcher1x1View, params);
    }

    private synchronized void handleOutsideTouch() {
        long now = SystemClock.uptimeMillis();
        totalOutsideTouches++;

        // Purge timestamps older than 500 ms
        while (!touchTimestamps.isEmpty() && (now - touchTimestamps.peekFirst()) > 500) {
            touchTimestamps.pollFirst();
        }
        touchTimestamps.addLast(now);

        // Check for burst (3 or more touches within 500 ms)
        if (touchTimestamps.size() >= 3 && (now - lastBurstTimestamp) > 500) {
            totalBursts++;
            lastBurstTimestamp = now;
        }

        // Auto-lock on severe burst (4 or more touches within 300 ms)
        int rapidCount = 0;
        for (Long t : touchTimestamps) {
            if ((now - t) <= 300) {
                rapidCount++;
            }
        }
        if (rapidCount >= 4) {
            BubbleService.triggerLock(this);
        }

        // Broadcast stats
        Intent updateIntent = new Intent(ACTION_WATCHER_UPDATE);
        updateIntent.setPackage(getPackageName());
        updateIntent.putExtra(EXTRA_TOUCHES, totalOutsideTouches);
        updateIntent.putExtra(EXTRA_BURSTS, totalBursts);
        sendBroadcast(updateIntent);

        // Update notification
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification("Watched: " + totalOutsideTouches + " touches, " + totalBursts + " bursts"));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (watcher1x1View != null && windowManager != null) {
            try {
                windowManager.removeView(watcher1x1View);
            } catch (Exception ignored) {}
            watcher1x1View = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
