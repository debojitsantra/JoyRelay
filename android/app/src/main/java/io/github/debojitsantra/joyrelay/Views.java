package io.github.debojitsantra.joyrelay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

class LatencyGraph extends View {
    static final int MAX_POINTS = 90;

    private List<Float> data = java.util.Collections.emptyList();
    private final Paint line = new Paint(3);
    private final Paint grid = new Paint(3);
    private final Paint label = new Paint(3);

    LatencyGraph(Context context) {
        super(context);
        init(context);
    }

    LatencyGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        line.setColor(C.ACCENT_HI);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dpf(context, 2));

        grid.setColor(C.BORDER);
        grid.setStrokeWidth(Ui.dpf(context, 1));

        label.setColor(C.MUTED);
        label.setTextSize(Ui.dpf(context, 10));
    }

    void setData(List<Float> values) {
        data = values;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float paddingBottom = Ui.dpf(getContext(), 14);
        float plotHeight = height - paddingBottom;

        canvas.drawLine(0, plotHeight, width, plotHeight, grid);
        canvas.drawLine(0, plotHeight / 2, width, plotHeight / 2, grid);
        if (data.size() < 2) {
            canvas.drawText("waiting for data...", 0, height - 1, label);
            return;
        }

        float top = 10;
        for (Float value : data) {
            top = Math.max(top, value * 1.25f);
        }

        Path path = new Path();
        float step = width / (MAX_POINTS - 1);
        float startX = width - step * (data.size() - 1);
        for (int i = 0; i < data.size(); i++) {
            float x = startX + i * step;
            float y = plotHeight - Math.min(top, data.get(i)) / top * (plotHeight - 4) - 2;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }

        canvas.drawPath(path, line);
        canvas.drawText("0", 0, height - 1, label);
        canvas.drawText(
                String.format("%.0f ms", top),
                width - Ui.dpf(getContext(), 44),
                height - 1,
                label
        );
    }
}

class ControllerSimulationView extends View {
    private int buttons;
    private int lt;
    private int rt;
    private float lx;
    private float ly;
    private float rx;
    private float ry;
    private final Paint paint = new Paint(3);

    ControllerSimulationView(Context context) {
        super(context);
    }

    ControllerSimulationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setState(int buttons, float lx, float ly, float rx, float ry, int lt, int rt) {
        this.buttons = buttons;
        this.lx = lx;
        this.ly = ly;
        this.rx = rx;
        this.ry = ry;
        this.lt = lt;
        this.rt = rt;
        invalidate();
    }

    private void circle(Canvas canvas, float x, float y, float radius, int color, boolean stroke) {
        paint.setColor(color);
        paint.setStyle(stroke ? Paint.Style.STROKE : Paint.Style.FILL);
        paint.setStrokeWidth(2);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void trigger(Canvas canvas, boolean left, int amount) {
        Path shape = new Path();
        if (left) {
            shape.moveTo(152.5f, 37f);
            shape.cubicTo(152.5f, 41.142f, 149.142f, 44.5f, 145f, 44.5f);
            shape.lineTo(132f, 44.5f);
            shape.cubicTo(127.858f, 44.5f, 124.5f, 41.142f, 124.5f, 37f);
            shape.lineTo(124.5f, 16.5f);
            shape.cubicTo(124.5f, 8.768f, 130.768f, 2.5f, 138.5f, 2.5f);
            shape.cubicTo(146.232f, 2.5f, 152.5f, 8.768f, 152.5f, 16.5f);
            shape.close();
        } else {
            shape.moveTo(317.5f, 37f);
            shape.cubicTo(317.5f, 41.142f, 314.142f, 44.5f, 310f, 44.5f);
            shape.lineTo(297f, 44.5f);
            shape.cubicTo(292.858f, 44.5f, 289.5f, 41.142f, 289.5f, 37f);
            shape.lineTo(289.5f, 16.5f);
            shape.cubicTo(289.5f, 8.768f, 295.768f, 2.5f, 303.5f, 2.5f);
            shape.cubicTo(311.232f, 2.5f, 317.5f, 8.768f, 317.5f, 16.5f);
            shape.close();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C.CARD_HI);
        canvas.drawPath(shape, paint);

        if (amount > 0) {
            canvas.save();
            canvas.clipPath(shape);
            paint.setColor(C.ACCENT);
            float leftEdge = left ? 124.5f : 289.5f;
            float rightEdge = left ? 152.5f : 317.5f;
            canvas.drawRect(
                    leftEdge,
                    44.5f - (42f * amount / 255f),
                    rightEdge,
                    45f,
                    paint
            );
            canvas.restore();
        }

        paint.setColor(C.TEXT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(shape, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / 440f, getHeight() / 400f);
        if (scale <= 0) {
            return;
        }

        canvas.save();
        canvas.translate((getWidth() - 440 * scale) / 2, (getHeight() - 400 * scale) / 2);
        canvas.scale(scale, scale);

        Path shell = new Path();
        shell.moveTo(220.5f, 92f);
        shell.cubicTo(200.5f, 92f, 154f, 92f, 128f, 92f);
        shell.cubicTo(95.5f, 92f, 66.5f, 109.5f, 55f, 137.5f);
        shell.cubicTo(43.5f, 165.5f, 4f, 271.1f, 4f, 317.5f);
        shell.cubicTo(4f, 363.9f, 17.5f, 378.5f, 49.5f, 378.5f);
        shell.cubicTo(81.5f, 378.5f, 105f, 294.5f, 150f, 294.5f);
        shell.cubicTo(195f, 294.5f, 220.5f, 294.5f, 220.5f, 294.5f);
        shell.cubicTo(220.5f, 294.5f, 245.5f, 294.5f, 290.5f, 294.5f);
        shell.cubicTo(335.5f, 294.5f, 359f, 378.5f, 391f, 378.5f);
        shell.cubicTo(423f, 378.5f, 436.5f, 363.9f, 436.5f, 317.5f);
        shell.cubicTo(436.5f, 271.1f, 397f, 165.5f, 385.5f, 137.5f);
        shell.cubicTo(374f, 109.5f, 345f, 92f, 312.5f, 92f);
        shell.cubicTo(286.5f, 92f, 240f, 92f, 220.5f, 92f);
        shell.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C.CARD);
        canvas.drawPath(shell, paint);
        paint.setColor(C.TEXT);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(shell, paint);
        paint.setStyle(Paint.Style.FILL);

        int active = C.ACCENT;
        int idle = C.CARD_HI;
        int edge = C.MUTED;

        trigger(canvas, true, lt);
        trigger(canvas, false, rt);
        drawRect(canvas, 111.5f, 61.5f, 152.5f, 74.5f,
                (buttons & 0x0100) != 0 ? active : idle, edge);
        drawRect(canvas, 289.5f, 61.5f, 330.5f, 74.5f,
                (buttons & 0x0200) != 0 ? active : idle, edge);

        circle(canvas, 113, 160, 37.5f, edge, true);
        circle(canvas, 278, 238, 37.5f, edge, true);
        circle(canvas, 113, 160, 28, idle, false);
        circle(canvas, 278, 238, 28, idle, false);
        circle(canvas, 113 + lx * 17, 160 - ly * 17, 20,
                (buttons & 0x40) != 0 || Math.abs(lx) > 0.08f || Math.abs(ly) > 0.08f
                        ? active : idle,
                false);
        circle(canvas, 278 + rx * 17, 238 - ry * 17, 20,
                (buttons & 0x80) != 0 || Math.abs(rx) > 0.08f || Math.abs(ry) > 0.08f
                        ? active : idle,
                false);

        circle(canvas, 166, 238, 37.5f, edge, true);
        drawDpad(canvas, 166, 220, true, (buttons & 1) != 0, active, idle, edge);
        drawDpad(canvas, 184, 238, false, (buttons & 8) != 0, active, idle, edge);
        drawDpad(canvas, 166, 256, true, (buttons & 2) != 0, active, idle, edge);
        drawDpad(canvas, 148, 238, false, (buttons & 4) != 0, active, idle, edge);

        circle(canvas, 329, 160, 37.5f, edge, true);
        circle(canvas, 329, 141, 10.5f, (buttons & 0x8000) != 0 ? active : idle, false);
        circle(canvas, 348, 160, 10.5f, (buttons & 0x2000) != 0 ? active : idle, false);
        circle(canvas, 329, 179, 10.5f, (buttons & 0x1000) != 0 ? active : idle, false);
        circle(canvas, 310, 160, 10.5f, (buttons & 0x4000) != 0 ? active : idle, false);

        metaButton(canvas, 185, 162, "−", (buttons & 0x0020) != 0, active, idle, edge);
        metaButton(canvas, 259, 162, "+", (buttons & 0x0010) != 0, active, idle, edge);
        canvas.restore();
    }

    private void drawRect(Canvas canvas, float left, float top, float right, float bottom,
                          int fill, int stroke) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(stroke);
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDpad(Canvas canvas, float x, float y, boolean vertical, boolean on,
                          int active, int idle, int edge) {
        float left = vertical ? x - 7.5f : x - 10f;
        float top = vertical ? y - 10f : y - 7.5f;
        float right = vertical ? x + 7.5f : x + 10f;
        float bottom = vertical ? y + 10f : y + 7.5f;
        drawRect(canvas, left, top, right, bottom, on ? active : idle, edge);
    }

    private void metaButton(Canvas canvas, float x, float y, String symbol, boolean pressed,
                            int active, int idle, int edge) {
        circle(canvas, x, y, 10, pressed ? active : idle, false);
        circle(canvas, x, y, 10, edge, true);
        paint.setColor(C.TEXT);
        paint.setTextSize(14);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(symbol, x, y + 5, paint);
    }
}
