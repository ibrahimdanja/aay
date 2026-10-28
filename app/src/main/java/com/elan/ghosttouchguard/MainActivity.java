package com.elan.ghosttouchguard;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main dashboard for Ghost Touch Guard.
 * Programmatic vertical UI controlling overlay permissions, master protection,
 * lock bubble, touch watcher stats, zone guard, and ghost map launcher.
 */
public class MainActivity extends Activity {

    private Button permBtn;
    private EditText secsInput;
    private TextView watcherStatsText;
    private Button showZonesBtn;

    private final BroadcastReceiver watcherReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWatcherStats();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#10141D"));
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(32));

        // Title Header
        TextView titleText = new TextView(this);
        titleText.setText("Ghost Touch Guard");
        titleText.setTextSize(24f);
        titleText.setTextColor(Color.WHITE);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(titleText);

        TextView subtitleText = new TextView(this);
        subtitleText.setText("Phantom touch workaround & screen shield");
        subtitleText.setTextSize(13f);
        subtitleText.setTextColor(Color.parseColor("#8E9AA8"));
        subtitleText.setPadding(0, dpToPx(4), 0, dpToPx(16));
        root.addView(subtitleText);

        // Section 1: Overlay Permission
        permBtn = createStyledButton("Check Overlay Permission", "#FF9800", v -> requestOverlayPermission());
        root.addView(permBtn);
        addDivider(root);

        // Section 2: Master Protection
        addSectionHeader(root, "Master Protection");

        LinearLayout masterRow = new LinearLayout(this);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);

        Button startProtectionBtn = createStyledButton("Start Protection", "#2E7D32", v -> startProtection());
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        spParams.setMarginEnd(dpToPx(6));
        startProtectionBtn.setLayoutParams(spParams);

        Button stopProtectionBtn = createStyledButton("Stop Protection", "#C62828", v -> stopProtection());
        stopProtectionBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        masterRow.addView(startProtectionBtn);
        masterRow.addView(stopProtectionBtn);
        root.addView(masterRow);

        addDivider(root);

        // Section 3: Lock Bubble Configuration & Controls
        addSectionHeader(root, "Lock Bubble Settings");

        TextView secsLabel = new TextView(this);
        secsLabel.setText("Auto-release lock after (seconds, 5-3600):");
        secsLabel.setTextColor(Color.parseColor("#D0D7DE"));
        secsLabel.setTextSize(14f);
        root.addView(secsLabel);

        secsInput = new EditText(this);
        secsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        secsInput.setTextColor(Color.WHITE);
        secsInput.setTextSize(16f);
        secsInput.setBackground(createInputBackground());
        secsInput.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputParams.setMargins(0, dpToPx(6), 0, dpToPx(12));
        secsInput.setLayoutParams(inputParams);

        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        int savedSecs = cfg.getInt("secs", 60);
        secsInput.setText(String.valueOf(savedSecs));
        root.addView(secsInput);

        LinearLayout bubbleRow = new LinearLayout(this);
        bubbleRow.setOrientation(LinearLayout.HORIZONTAL);

        Button startBubbleBtn = createStyledButton("Start Bubble", "#1976D2", v -> startBubbleService());
        LinearLayout.LayoutParams sbParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbParams.setMarginEnd(dpToPx(6));
        startBubbleBtn.setLayoutParams(sbParams);

        Button stopBubbleBtn = createStyledButton("Stop Bubble", "#455A64", v -> stopBubbleService());
        stopBubbleBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        bubbleRow.addView(startBubbleBtn);
        bubbleRow.addView(stopBubbleBtn);
        root.addView(bubbleRow);

        addDivider(root);

        // Section 4: Zone Guard
        addSectionHeader(root, "Zone Guard (Spot Blocker)");

        LinearLayout zoneRow = new LinearLayout(this);
        zoneRow.setOrientation(LinearLayout.HORIZONTAL);

        Button startZoneBtn = createStyledButton("Start Zones", "#0288D1", v -> startZoneService());
        LinearLayout.LayoutParams szParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        szParams.setMarginEnd(dpToPx(6));
        startZoneBtn.setLayoutParams(szParams);

        Button stopZoneBtn = createStyledButton("Stop Zones", "#455A64", v -> stopZoneService());
        stopZoneBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        zoneRow.addView(startZoneBtn);
        zoneRow.addView(stopZoneBtn);
        root.addView(zoneRow);

        showZonesBtn = createStyledButton("Show Zones: OFF", "#5E35B1", v -> toggleShowZones());
        showZonesBtn.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(showZonesBtn);

        addDivider(root);

        // Section 5: Touch Watcher Diagnostic
        addSectionHeader(root, "Touch Watcher (Diagnostic & Auto-Lock)");

        watcherStatsText = new TextView(this);
        watcherStatsText.setTextColor(Color.parseColor("#4DD0E1"));
        watcherStatsText.setTextSize(14f);
        watcherStatsText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        watcherStatsText.setPadding(0, 0, 0, dpToPx(8));
        root.addView(watcherStatsText);

        LinearLayout watcherRow = new LinearLayout(this);
        watcherRow.setOrientation(LinearLayout.HORIZONTAL);

        Button startWatcherBtn = createStyledButton("Start Watcher", "#00897B", v -> startWatcherService());
        LinearLayout.LayoutParams swParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        swParams.setMarginEnd(dpToPx(6));
        startWatcherBtn.setLayoutParams(swParams);

        Button stopWatcherBtn = createStyledButton("Stop Watcher", "#455A64", v -> stopWatcherService());
        stopWatcherBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        watcherRow.addView(startWatcherBtn);
        watcherRow.addView(stopWatcherBtn);
        root.addView(watcherRow);

        addDivider(root);

        // Section 6: Ghost Map Touch Recorder & Battery
        addSectionHeader(root, "Diagnostics & Power");

        Button openMapBtn = createStyledButton("Open Ghost Map Recorder", "#7B1FA2", v -> {
            Intent mapIntent = new Intent(this, GhostMapActivity.class);
            startActivity(mapIntent);
        });
        root.addView(openMapBtn);

        Button batteryBtn = createStyledButton("Battery Optimization Settings", "#37474F", v -> openBatterySettings());
        root.addView(batteryBtn);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void addSectionHeader(LinearLayout root, String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(15f);
        header.setTextColor(Color.parseColor("#90CAF9"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, dpToPx(8), 0, dpToPx(8));
        root.addView(header);
    }

    private void addDivider(LinearLayout root) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#263238"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        params.setMargins(0, dpToPx(14), 0, dpToPx(10));
        divider.setLayoutParams(params);
        root.addView(divider);
    }

    private GradientDrawable createInputBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#1C2331"));
        gd.setCornerRadius(dpToPx(6));
        gd.setStroke(dpToPx(1), Color.parseColor("#37474F"));
        return gd;
    }

    private Button createStyledButton(String text, String hexColor, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(hexColor));
        gd.setCornerRadius(dpToPx(8));
        btn.setBackground(gd);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(4), 0, dpToPx(4));
        btn.setLayoutParams(params);

        btn.setOnClickListener(listener);
        return btn;
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionButton();
        updateShowZonesButton();
        updateWatcherStats();
        registerReceiver(watcherReceiver, new IntentFilter(OverlayService.ACTION_WATCHER_UPDATE));
    }

    @Override
    protected void onPause() {
        saveConfiguredSeconds();
        try {
            unregisterReceiver(watcherReceiver);
        } catch (Exception ignored) {}
        super.onPause();
    }

    private void saveConfiguredSeconds() {
        if (secsInput == null) return;
        int secs = 60;
        try {
            secs = Integer.parseInt(secsInput.getText().toString().trim());
        } catch (NumberFormatException ignored) {}
        secs = Math.max(5, Math.min(3600, secs));
        secsInput.setText(String.valueOf(secs));

        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        cfg.edit().putInt("secs", secs).apply();
    }

    private void updatePermissionButton() {
        boolean canDraw = Settings.canDrawOverlays(this);
        if (canDraw) {
            permBtn.setText("Overlay Permission: GRANTED \u2713");
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#2E7D32"));
            gd.setCornerRadius(dpToPx(8));
            permBtn.setBackground(gd);
        } else {
            permBtn.setText("Grant Overlay Permission (Required) \u26A0");
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#D32F2F"));
            gd.setCornerRadius(dpToPx(8));
            permBtn.setBackground(gd);
        }
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startProtection() {
        if (!checkPermissionOrWarn()) return;
        saveConfiguredSeconds();

        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        SharedPreferences show = getSharedPreferences("show", MODE_PRIVATE);
        cfg.edit().putBoolean("autostart", true).putBoolean("show", false).apply();
        show.edit().putBoolean("show", false).apply();
        updateShowZonesButton();

        // Stop Zone Guard first then start so zones refresh
        stopService(new Intent(this, ZoneService.class));
        startServiceInternal(ZoneService.class);

        // Start Lock Bubble
        startServiceInternal(BubbleService.class);

        Toast.makeText(this, "Protection started (Bubble + Zones)", Toast.LENGTH_SHORT).show();
    }

    private void stopProtection() {
        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        cfg.edit().putBoolean("autostart", false).apply();

        stopService(new Intent(this, ZoneService.class));
        stopService(new Intent(this, BubbleService.class));

        Toast.makeText(this, "Protection stopped", Toast.LENGTH_SHORT).show();
    }

    private void startBubbleService() {
        if (!checkPermissionOrWarn()) return;
        saveConfiguredSeconds();
        startServiceInternal(BubbleService.class);
        Toast.makeText(this, "Lock Bubble started", Toast.LENGTH_SHORT).show();
    }

    private void stopBubbleService() {
        stopService(new Intent(this, BubbleService.class));
        Toast.makeText(this, "Lock Bubble stopped", Toast.LENGTH_SHORT).show();
    }

    private void startZoneService() {
        if (!checkPermissionOrWarn()) return;
        stopService(new Intent(this, ZoneService.class));
        startServiceInternal(ZoneService.class);
        Toast.makeText(this, "Zone Guard started (refreshed)", Toast.LENGTH_SHORT).show();
    }

    private void stopZoneService() {
        stopService(new Intent(this, ZoneService.class));
        Toast.makeText(this, "Zone Guard stopped", Toast.LENGTH_SHORT).show();
    }

    private void toggleShowZones() {
        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        SharedPreferences show = getSharedPreferences("show", MODE_PRIVATE);
        boolean current = cfg.getBoolean("show", false) || show.getBoolean("show", false);
        boolean next = !current;
        cfg.edit().putBoolean("show", next).apply();
        show.edit().putBoolean("show", next).apply();
        updateShowZonesButton();

        if (ZoneService.isRunning()) {
            stopService(new Intent(this, ZoneService.class));
            startServiceInternal(ZoneService.class);
            Toast.makeText(this, "Zone Guard refreshed (" + (next ? "Tint ON" : "Invisible") + ")", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Show zones: " + (next ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateShowZonesButton() {
        SharedPreferences cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        SharedPreferences show = getSharedPreferences("show", MODE_PRIVATE);
        boolean isShowing = cfg.getBoolean("show", false) || show.getBoolean("show", false);
        if (showZonesBtn != null) {
            showZonesBtn.setText("Show Zones (Red Tint): " + (isShowing ? "ON" : "OFF"));
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(isShowing ? Color.parseColor("#E65100") : Color.parseColor("#5E35B1"));
            gd.setCornerRadius(dpToPx(8));
            showZonesBtn.setBackground(gd);
        }
    }

    private void startWatcherService() {
        if (!checkPermissionOrWarn()) return;
        startServiceInternal(OverlayService.class);
        Toast.makeText(this, "Touch Watcher started", Toast.LENGTH_SHORT).show();
    }

    private void stopWatcherService() {
        stopService(new Intent(this, OverlayService.class));
        Toast.makeText(this, "Touch Watcher stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateWatcherStats() {
        if (watcherStatsText != null) {
            int touches = OverlayService.getTotalOutsideTouches();
            int bursts = OverlayService.getTotalBursts();
            boolean active = OverlayService.isRunning();
            watcherStatsText.setText(String.format(
                    "Watcher: %s | Touches: %d | Bursts: %d",
                    active ? "ACTIVE" : "STOPPED", touches, bursts
            ));
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Unable to open battery settings", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean checkPermissionOrWarn() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_LONG).show();
            requestOverlayPermission();
            return false;
        }
        return true;
    }

    private void startServiceInternal(Class<?> serviceClass) {
        Intent intent = new Intent(this, serviceClass);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
