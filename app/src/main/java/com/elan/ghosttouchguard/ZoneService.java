package com.elan.ghosttouchguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Zone guard service. Clusters ghost touch coordinates and places 140x140px touch-consuming
 * overlays over chronic phantom spots.
 */
public class ZoneService extends Service {

    private static final String CHANNEL_ID = "gtg_zone_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final float MERGE_RADIUS_PX = 70.0f;
    private static final int BOX_SIZE_PX = 140;

    private static ZoneService instance = null;

    private WindowManager windowManager;
    private final List<View> zoneViews = new ArrayList<>();

    public static boolean isRunning() {
        return instance != null;
    }

    public static class Cluster {
        float cx;
        float cy;
        int count;

        Cluster(float x, float y) {
            this.cx = x;
            this.cy = y;
            this.count = 1;
        }

        double distanceTo(float x, float y) {
            float dx = this.cx - x;
            float dy = this.cy - y;
            return Math.sqrt(dx * dx + dy * dy);
        }

        void addPoint(float x, float y) {
            this.cx = (this.cx * this.count + x) / (this.count + 1);
            this.cy = (this.cy * this.count + y) / (this.count + 1);
            this.count++;
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
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ghost Touch Guard - Zone Guard",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows status of the zone touch blocker");
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
        return builder.setContentTitle("Zone Guard Active")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        refreshZones();
        return START_STICKY;
    }

    private synchronized void refreshZones() {
        // Remove existing views
        for (View v : zoneViews) {
            try {
                windowManager.removeView(v);
            } catch (Exception ignored) {}
        }
        zoneViews.clear();

        List<Cluster> activeClusters = loadAndClusterPoints();

        // Check if "show zones" test mode is enabled
        boolean showZones = isShowZonesEnabled();

        int overlayType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int halfBox = BOX_SIZE_PX / 2;

        for (Cluster c : activeClusters) {
            View view = new View(this);
            if (showZones) {
                // Translucent red box with visible border for testing
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(0x44FF0000); // 0x44FF0000 tint
                gd.setStroke(2, 0xFFFF0000);
                view.setBackground(gd);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }

            // Invisible touch-eating: consume touch events
            view.setOnTouchListener((v, event) -> true);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    BOX_SIZE_PX,
                    BOX_SIZE_PX,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = (int) (c.cx - halfBox);
            params.y = (int) (c.cy - halfBox);

            try {
                windowManager.addView(view, params);
                zoneViews.add(view);
            } catch (Exception ignored) {}
        }

        String status = "Blocking " + activeClusters.size() + " spots";
        startForeground(NOTIFICATION_ID, buildNotification(status));
    }

    private boolean isShowZonesEnabled() {
        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        SharedPreferences show = getSharedPreferences("show", MODE_PRIVATE);
        return cfg.getBoolean("show", false) || show.getBoolean("show", false);
    }

    private List<Cluster> loadAndClusterPoints() {
        List<Cluster> clusters = new ArrayList<>();

        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        File file = new File(dir, GhostMapActivity.CSV_FILENAME);

        if (!file.exists()) {
            return clusters;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine(); // skip header
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 9) {
                    try {
                        int sx = Integer.parseInt(parts[7].trim());
                        int sy = Integer.parseInt(parts[8].trim());

                        // Greedy clustering
                        Cluster closest = null;
                        double minDistance = Double.MAX_VALUE;
                        for (Cluster c : clusters) {
                            double dist = c.distanceTo(sx, sy);
                            if (dist <= MERGE_RADIUS_PX && dist < minDistance) {
                                minDistance = dist;
                                closest = c;
                            }
                        }

                        if (closest != null) {
                            closest.addPoint(sx, sy);
                        } else {
                            clusters.add(new Cluster(sx, sy));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }

        // Keep clusters with at least 3 hits
        List<Cluster> filtered = new ArrayList<>();
        for (Cluster c : clusters) {
            if (c.count >= 3) {
                filtered.add(c);
            }
        }
        return filtered;
    }

    @Override
    public void onDestroy() {
        instance = null;
        for (View v : zoneViews) {
            try {
                windowManager.removeView(v);
            } catch (Exception ignored) {}
        }
        zoneViews.clear();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
