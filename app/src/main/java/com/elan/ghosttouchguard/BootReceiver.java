package com.elan.ghosttouchguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

/**
 * Restarts BubbleService and ZoneService upon device reboot if protection autostart is enabled
 * and overlay permission is granted.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences cfg = context.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        boolean autostart = cfg.getBoolean("autostart", false);

        if (autostart && Settings.canDrawOverlays(context)) {
            // Start Zone Guard
            Intent zoneIntent = new Intent(context, ZoneService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(zoneIntent);
            } else {
                context.startService(zoneIntent);
            }

            // Start Lock Bubble
            Intent bubbleIntent = new Intent(context, BubbleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(bubbleIntent);
            } else {
                context.startService(bubbleIntent);
            }
        }
    }
}
