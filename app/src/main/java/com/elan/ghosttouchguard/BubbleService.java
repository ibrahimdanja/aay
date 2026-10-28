package com.elan.ghosttouchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Floating lock bubble service and full-screen touch blocker overlay.
 */
public class BubbleService extends Service {

    public static final String ACTION_TRIGGER_LOCK = "com.elan.ghosttouchguard.TRIGGER_LOCK";
    private static final String CHANNEL_ID = "gtg_bubble_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static BubbleService instance = null;

    private WindowManager windowManager;
    private View blockerView;
    private TextView bubbleView;
    private WindowManager.LayoutParams blockerParams;
    private WindowManager.LayoutParams bubbleParams;

    private boolean isLocked = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable autoReleaseRunnable;
    private Runnable unlockRunnable;

    public static boolean isRunning() {
        return instance != null;
    }

    public static void triggerLock(Context context) {
        if (instance != null) {
            instance.mainHandler.post(() -> instance.lockScreen());
        } else {
            Intent intent = new Intent(context, BubbleService.class);
            intent.setAction(ACTION_TRIGGER_LOCK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
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
        Notification notification = buildNotification("Lock Bubble Active", "Tap LOCK to disable touches");
        startForeground(NOTIFICATION_ID, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initBlockerOverlay();
        initBubbleOverlay();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ghost Touch Guard - Lock Bubble",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows status of the floating lock bubble overlay");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String content) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }

    private int getOverlayWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void initBlockerOverlay() {
        blockerView = new View(this);
        blockerView.setBackgroundColor(Color.TRANSPARENT);

        // Blocker consumes touches when touchable
        blockerView.setOnTouchListener((v, event) -> true);

        int type = getOverlayWindowType();

        // Always attached, starts with FLAG_NOT_TOUCHABLE (transparent to touches)
        blockerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        blockerParams.gravity = Gravity.TOP | Gravity.START;

        windowManager.addView(blockerView, blockerParams);
    }

    private void initBubbleOverlay() {
        int bubbleSize = dpToPx(60);

        bubbleView = new TextView(this);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setTextColor(Color.WHITE);
        bubbleView.setTextSize(12f);
        bubbleView.setTypeface(Typeface.DEFAULT_BOLD);
        applyBubbleStyle(false);

        int type = getOverlayWindowType();

        bubbleParams = new WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 40;
        bubbleParams.y = dpToPx(160);

        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY, initialTouchX, initialTouchY;
            private long downTime = 0;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = SystemClock.uptimeMillis();
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;

                        if (isLocked) {
                            // Hold for 800ms required to unlock
                            unlockRunnable = () -> {
                                if (isLocked) {
                                    unlockScreen();
                                    Toast.makeText(BubbleService.this, "Screen Unlocked", Toast.LENGTH_SHORT).show();
                                }
                            };
                            mainHandler.postDelayed(unlockRunnable, 800);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true;
                            if (isLocked && unlockRunnable != null && (Math.abs(dx) > 30 || Math.abs(dy) > 30)) {
                                mainHandler.removeCallbacks(unlockRunnable);
                            }
                            bubbleParams.x = (int) (initialX + dx);
                            bubbleParams.y = (int) (initialY + dy);
                            windowManager.updateViewLayout(bubbleView, bubbleParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (unlockRunnable != null) {
                            mainHandler.removeCallbacks(unlockRunnable);
                            unlockRunnable = null;
                        }

                        long elapsed = SystemClock.uptimeMillis() - downTime;
                        if (!isDragging && elapsed < 400) {
                            if (!isLocked) {
                                lockScreen();
                                Toast.makeText(BubbleService.this, "Screen Locked (Hold bubble to open)", Toast.LENGTH_SHORT).show();
                            } else {
                                // Locked: quick tap does nothing
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        // Add bubble AFTER blocker so it remains on top of the touch blocker
        windowManager.addView(bubbleView, bubbleParams);
    }

    private void applyBubbleStyle(boolean locked) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        if (locked) {
            shape.setColor(Color.parseColor("#D32F2F")); // Red
            bubbleView.setText("HOLD\nto open");
        } else {
            shape.setColor(Color.parseColor("#1976D2")); // Blue
            bubbleView.setText("LOCK");
        }
        shape.setStroke(dpToPx(2), Color.WHITE);
        bubbleView.setBackground(shape);
    }

    public synchronized void lockScreen() {
        if (isLocked) return;
        isLocked = true;
        applyBubbleStyle(true);

        // Blocker consumes touches (remove FLAG_NOT_TOUCHABLE)
        blockerParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        windowManager.updateViewLayout(blockerView, blockerParams);

        // Auto-release after configured seconds
        int secs = getConfiguredSeconds();
        if (autoReleaseRunnable != null) {
            mainHandler.removeCallbacks(autoReleaseRunnable);
        }
        autoReleaseRunnable = () -> {
            if (isLocked) {
                unlockScreen();
                Toast.makeText(BubbleService.this, "Lock auto-released (" + secs + "s)", Toast.LENGTH_SHORT).show();
            }
        };
        mainHandler.postDelayed(autoReleaseRunnable, secs * 1000L);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification("Screen Locked", "Hold bubble 800ms to unlock"));
        }
    }

    public synchronized void unlockScreen() {
        if (!isLocked) return;
        isLocked = false;
        applyBubbleStyle(false);

        if (autoReleaseRunnable != null) {
            mainHandler.removeCallbacks(autoReleaseRunnable);
            autoReleaseRunnable = null;
        }

        // Blocker lets touches pass through (add FLAG_NOT_TOUCHABLE)
        blockerParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowManager.updateViewLayout(blockerView, blockerParams);

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification("Lock Bubble Active", "Tap LOCK to disable touches"));
        }
    }

    private int getConfiguredSeconds() {
        SharedPreferences sp = getSharedPreferences("cfg", MODE_PRIVATE);
        int secs = sp.getInt("secs", 60);
        return Math.max(5, Math.min(3600, secs));
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TRIGGER_LOCK.equals(intent.getAction())) {
            mainHandler.post(this::lockScreen);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (autoReleaseRunnable != null) {
            mainHandler.removeCallbacks(autoReleaseRunnable);
        }
        if (unlockRunnable != null) {
            mainHandler.removeCallbacks(unlockRunnable);
        }
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
            bubbleView = null;
        }
        if (blockerView != null && windowManager != null) {
            try {
                windowManager.removeView(blockerView);
            } catch (Exception ignored) {}
            blockerView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
