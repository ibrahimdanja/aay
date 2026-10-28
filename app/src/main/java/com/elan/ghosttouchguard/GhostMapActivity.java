package com.elan.ghosttouchguard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Ghost map recording activity. Accumulates touches in ghostmap2.csv and visualizes them on HeatView.
 */
public class GhostMapActivity extends Activity {

    public static final String CSV_FILENAME = "ghostmap2.csv";
    private static final String CSV_HEADER = "epoch_ms,time,x,y,nx,ny,fingers,sx,sy\n";

    private HeatView heatView;
    private TextView summaryText;
    private Button recordBtn;
    private boolean isRecording = false;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private final int[] screenLocation = new int[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Build UI programmatically
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Top Control Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(8));
        header.setBackgroundColor(Color.parseColor("#1E1E1E"));

        // Action Buttons Row
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);

        recordBtn = new Button(this);
        recordBtn.setText("Start Record");
        recordBtn.setTextColor(Color.WHITE);
        recordBtn.setBackgroundColor(Color.parseColor("#2E7D32")); // Green
        recordBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams recParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        recParams.setMarginEnd(dpToPx(6));
        recordBtn.setLayoutParams(recParams);
        recordBtn.setOnClickListener(v -> toggleRecording());

        Button shareBtn = new Button(this);
        shareBtn.setText("Share CSV");
        shareBtn.setTextColor(Color.WHITE);
        shareBtn.setBackgroundColor(Color.parseColor("#1565C0")); // Blue
        shareBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        shareParams.setMarginEnd(dpToPx(6));
        shareBtn.setLayoutParams(shareParams);
        shareBtn.setOnClickListener(v -> shareCsv());

        Button clearBtn = new Button(this);
        clearBtn.setText("Clear (Hold)");
        clearBtn.setTextColor(Color.WHITE);
        clearBtn.setBackgroundColor(Color.parseColor("#C62828")); // Dark red
        clearBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        clearBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clearBtn.setOnClickListener(v -> Toast.makeText(GhostMapActivity.this, "Long-press Clear button to wipe data", Toast.LENGTH_SHORT).show());
        clearBtn.setOnLongClickListener(v -> {
            clearData();
            return true;
        });

        btnRow.addView(recordBtn);
        btnRow.addView(shareBtn);
        btnRow.addView(clearBtn);
        header.addView(btnRow);

        // Summary Text
        summaryText = new TextView(this);
        summaryText.setTextColor(Color.parseColor("#00E5FF")); // Cyan accent
        summaryText.setTextSize(12f);
        summaryText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        summaryText.setPadding(0, dpToPx(8), 0, 0);
        summaryText.setText("[IDLE] Touches: 0 | Max Fingers: 0 | Last: -- | Hot: None");
        header.addView(summaryText);

        root.addView(header);

        // Heatmap custom view taking rest of screen
        heatView = new HeatView(this);
        LinearLayout.LayoutParams heatParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        heatView.setLayoutParams(heatParams);
        heatView.setOnTouchListener((v, event) -> {
            if (isRecording) {
                handleTouchEvent(event);
            }
            return true; // Consume touch so it records smoothly
        });

        root.addView(heatView);
        setContentView(root);

        // Load historical touches from CSV
        loadExistingCsvData();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toggleRecording() {
        isRecording = !isRecording;
        if (isRecording) {
            recordBtn.setText("Stop Record");
            recordBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
            Toast.makeText(this, "Recording started. Leave phone flat to detect phantom touches.", Toast.LENGTH_SHORT).show();
        } else {
            recordBtn.setText("Start Record");
            recordBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
            Toast.makeText(this, "Recording stopped.", Toast.LENGTH_SHORT).show();
        }
        updateSummary();
    }

    private void updateSummary() {
        if (summaryText != null && heatView != null) {
            summaryText.setText(heatView.getSummaryText(isRecording));
        }
    }

    private void handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int actionIndex = event.getActionIndex();
            float vx = event.getX(actionIndex);
            float vy = event.getY(actionIndex);
            int fingers = event.getPointerCount();

            heatView.getLocationOnScreen(screenLocation);
            int sx = (int) (vx + screenLocation[0]);
            int sy = (int) (vy + screenLocation[1]);

            int vw = heatView.getWidth();
            int vh = heatView.getHeight();
            float nx = (vw > 0) ? (vx / vw) : 0f;
            float ny = (vh > 0) ? (vy / vh) : 0f;

            long epoch = System.currentTimeMillis();
            String timeStr = dateFormat.format(new Date(epoch));

            // Add to HeatView
            heatView.addTouch(nx, ny, fingers, timeStr);
            updateSummary();

            // Append to CSV
            String line = String.format(Locale.US, "%d,%s,%.1f,%.1f,%.3f,%.3f,%d,%d,%d\n",
                    epoch, timeStr, vx, vy, nx, ny, fingers, sx, sy);
            appendCsv(line);
        }
    }

    private File getCsvFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) dir = getFilesDir();
        return new File(dir, CSV_FILENAME);
    }

    private void appendCsv(String line) {
        File file = getCsvFile();
        FileWriter writer = null;
        try {
            boolean isNew = !file.exists() || file.length() == 0;
            writer = new FileWriter(file, true);
            if (isNew) {
                writer.write(CSV_HEADER);
            }
            writer.write(line);
        } catch (IOException e) {
            // Ignore silently or log
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private void loadExistingCsvData() {
        File file = getCsvFile();
        if (!file.exists()) {
            updateSummary();
            return;
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
                        String timeStr = parts[1];
                        float nx = Float.parseFloat(parts[4]);
                        float ny = Float.parseFloat(parts[5]);
                        int fingers = Integer.parseInt(parts[6]);
                        heatView.addTouch(nx, ny, fingers, timeStr);
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
        updateSummary();
    }

    private void shareCsv() {
        File file = getCsvFile();
        if (!file.exists() || file.length() == 0) {
            Toast.makeText(this, "No CSV data yet. Record some touches first.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder content = new StringBuilder();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 2000) {
                content.append(line).append("\n");
                count++;
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error reading CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }

        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Ghost Touch Guard CSV Export");
        sendIntent.putExtra(Intent.EXTRA_TEXT, content.toString());
        startActivity(Intent.createChooser(sendIntent, "Share Ghost Touch Data"));
    }

    private void clearData() {
        File file = getCsvFile();
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                try (FileWriter fw = new FileWriter(file, false)) {
                    fw.write(CSV_HEADER);
                } catch (IOException ignored) {}
            }
        }
        heatView.clearMap();
        updateSummary();
        Toast.makeText(this, "Ghost map and CSV cleared", Toast.LENGTH_SHORT).show();
    }
}
