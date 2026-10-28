package com.elan.ghosttouchguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Custom 12-column x 20-row grid heatmap view for recording and visualizing ghost touches.
 */
public class HeatView extends View {

    public static final int COLS = 12;
    public static final int ROWS = 20;

    private final int[][] counts = new int[COLS][ROWS];
    private int totalTouches = 0;
    private int maxCount = 0;
    private int maxFingers = 0;
    private String lastTimeStr = "--";

    private final Paint bgPaint = new Paint();
    private final Paint gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HeatView(Context context) {
        super(context);
        init();
    }

    public HeatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setColor(Color.parseColor("#121212"));

        gridLinePaint.setColor(Color.parseColor("#2C2C2C"));
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(1.5f);

        cellFillPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(spToPx(10));
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    public synchronized void addTouch(float nx, float ny, int fingers, String timeStr) {
        float clampedX = Math.max(0f, Math.min(0.999f, nx));
        float clampedY = Math.max(0f, Math.min(0.999f, ny));

        int col = (int) (clampedX * COLS);
        int row = (int) (clampedY * ROWS);

        counts[col][row]++;
        totalTouches++;

        if (counts[col][row] > maxCount) {
            maxCount = counts[col][row];
        }
        if (fingers > maxFingers) {
            maxFingers = fingers;
        }
        lastTimeStr = timeStr;

        postInvalidate();
    }

    public synchronized void clearMap() {
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                counts[c][r] = 0;
            }
        }
        totalTouches = 0;
        maxCount = 0;
        maxFingers = 0;
        lastTimeStr = "--";
        postInvalidate();
    }

    public synchronized int getTotalTouches() {
        return totalTouches;
    }

    public synchronized int getMaxFingers() {
        return maxFingers;
    }

    public synchronized String getLastTimeStr() {
        return lastTimeStr;
    }

    private static class RegionScore {
        String name;
        int count;

        RegionScore(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    public synchronized String getHottestAreasSummary() {
        if (totalTouches == 0) {
            return "None";
        }

        // 3x3 named macro-regions
        String[] vNames = {"top", "mid", "bottom"};
        String[] hNames = {"left", "center", "right"};
        int[][] regionCounts = new int[3][3];

        for (int c = 0; c < COLS; c++) {
            int hi = (c < 4) ? 0 : (c < 8 ? 1 : 2);
            for (int r = 0; r < ROWS; r++) {
                int vi = (r < 7) ? 0 : (r < 14 ? 1 : 2);
                regionCounts[vi][hi] += counts[c][r];
            }
        }

        List<RegionScore> list = new ArrayList<>();
        for (int vi = 0; vi < 3; vi++) {
            for (int hi = 0; hi < 3; hi++) {
                int c = regionCounts[vi][hi];
                if (c > 0) {
                    String name = (vi == 1 && hi == 1) ? "center" : (vNames[vi] + " " + hNames[hi]);
                    list.add(new RegionScore(name, c));
                }
            }
        }

        Collections.sort(list, new Comparator<RegionScore>() {
            @Override
            public int compare(RegionScore a, RegionScore b) {
                return Integer.compare(b.count, a.count);
            }
        });

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(3, list.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).name).append(" (").append(list.get(i).count).append(")");
        }
        return sb.length() > 0 ? sb.toString() : "None";
    }

    public synchronized String getSummaryText(boolean isRecording) {
        String state = isRecording ? "[RECORDING] " : "[IDLE] ";
        return state + "Touches: " + totalTouches
                + " | Max Fingers: " + maxFingers
                + " | Last: " + lastTimeStr
                + " | Hot: " + getHottestAreasSummary();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        // Background
        canvas.drawRect(0, 0, width, height, bgPaint);

        if (width <= 0 || height <= 0) return;

        float cellW = width / COLS;
        float cellH = height / ROWS;

        // Draw heat cells
        for (int c = 0; c < COLS; c++) {
            float left = c * cellW;
            float right = (c + 1) * cellW;

            for (int r = 0; r < ROWS; r++) {
                float top = r * cellH;
                float bottom = (r + 1) * cellH;

                int cnt = counts[c][r];
                if (cnt > 0) {
                    float ratio = (maxCount > 0) ? ((float) cnt / maxCount) : 0f;
                    int alpha = (int) (50 + 205 * ratio);
                    cellFillPaint.setColor(Color.argb(alpha, 255, 30, 30));
                    canvas.drawRect(left, top, right, bottom, cellFillPaint);

                    String label = String.valueOf(cnt);
                    float textY = top + cellH / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f);
                    canvas.drawText(label, left + cellW / 2f, textY, textPaint);
                }
            }
        }

        // Draw grid lines
        for (int c = 0; c <= COLS; c++) {
            float x = c * cellW;
            canvas.drawLine(x, 0, x, height, gridLinePaint);
        }
        for (int r = 0; r <= ROWS; r++) {
            float y = r * cellH;
            canvas.drawLine(0, y, width, y, gridLinePaint);
        }
    }
}
