package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/* JADX INFO: loaded from: classes3.dex */
public class YutBoardView extends View {
    private static final float[][] POINTS = {new float[]{0.9f, 0.9f}, new float[]{0.9f, 0.74f}, new float[]{0.9f, 0.58f}, new float[]{0.9f, 0.42f}, new float[]{0.9f, 0.26f}, new float[]{0.9f, 0.1f}, new float[]{0.74f, 0.1f}, new float[]{0.58f, 0.1f}, new float[]{0.42f, 0.1f}, new float[]{0.26f, 0.1f}, new float[]{0.1f, 0.1f}, new float[]{0.1f, 0.26f}, new float[]{0.1f, 0.42f}, new float[]{0.1f, 0.58f}, new float[]{0.1f, 0.74f}, new float[]{0.1f, 0.9f}, new float[]{0.26f, 0.9f}, new float[]{0.42f, 0.9f}, new float[]{0.58f, 0.9f}, new float[]{0.74f, 0.9f}, new float[]{0.76f, 0.24f}, new float[]{0.63f, 0.37f}, new float[]{0.5f, 0.5f}, new float[]{0.37f, 0.63f}, new float[]{0.24f, 0.76f}, new float[]{0.24f, 0.24f}, new float[]{0.37f, 0.37f}, new float[]{0.63f, 0.63f}, new float[]{0.76f, 0.76f}};
    private final Paint boardFillPaint;
    private final RectF boardRect;
    private final Paint centerPaint;
    private final Paint linePaint;
    private final Paint nodePaint;
    private final Paint softLinePaint;
    private final Paint strokePaint;

    public YutBoardView(Context context) {
        super(context);
        this.linePaint = new Paint(1);
        this.softLinePaint = new Paint(1);
        this.boardFillPaint = new Paint(1);
        this.nodePaint = new Paint(1);
        this.centerPaint = new Paint(1);
        this.strokePaint = new Paint(1);
        this.boardRect = new RectF();
        init();
    }

    public YutBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.linePaint = new Paint(1);
        this.softLinePaint = new Paint(1);
        this.boardFillPaint = new Paint(1);
        this.nodePaint = new Paint(1);
        this.centerPaint = new Paint(1);
        this.strokePaint = new Paint(1);
        this.boardRect = new RectF();
        init();
    }

    public YutBoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.linePaint = new Paint(1);
        this.softLinePaint = new Paint(1);
        this.boardFillPaint = new Paint(1);
        this.nodePaint = new Paint(1);
        this.centerPaint = new Paint(1);
        this.strokePaint = new Paint(1);
        this.boardRect = new RectF();
        init();
    }

    private void init() {
        this.linePaint.setStyle(Paint.Style.STROKE);
        this.linePaint.setStrokeCap(Paint.Cap.ROUND);
        this.linePaint.setColor(getResources().getColor(R.color.board_line));
        this.softLinePaint.setStyle(Paint.Style.STROKE);
        this.softLinePaint.setStrokeCap(Paint.Cap.ROUND);
        this.softLinePaint.setColor(getResources().getColor(R.color.board_line_soft));
        this.boardFillPaint.setStyle(Paint.Style.FILL);
        this.boardFillPaint.setColor(getResources().getColor(R.color.surface_board));
        this.nodePaint.setStyle(Paint.Style.FILL);
        this.nodePaint.setColor(getResources().getColor(R.color.board_node));
        this.centerPaint.setStyle(Paint.Style.FILL);
        this.centerPaint.setColor(getResources().getColor(R.color.board_center));
        this.strokePaint.setStyle(Paint.Style.STROKE);
        this.strokePaint.setColor(getResources().getColor(R.color.board_node_stroke));
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float pad = size * 0.035f;
        float right = size - pad;
        float bottom = size - pad;
        float lineWidth = Math.max(4.0f, 0.012f * size);
        this.softLinePaint.setStrokeWidth(2.1f * lineWidth);
        this.linePaint.setStrokeWidth(lineWidth);
        this.strokePaint.setStrokeWidth(Math.max(2.0f, 0.006f * size));
        this.boardRect.set(pad, pad, right, bottom);
        canvas.drawRoundRect(this.boardRect, size * 0.045f, size * 0.045f, this.boardFillPaint);
        canvas.drawRoundRect(this.boardRect, size * 0.045f, 0.045f * size, this.softLinePaint);
        canvas.drawRoundRect(this.boardRect, size * 0.035f, 0.035f * size, this.linePaint);
        drawLine(canvas, 10, 0, size, this.softLinePaint);
        drawLine(canvas, 5, 15, size, this.softLinePaint);
        drawLine(canvas, 10, 0, size, this.linePaint);
        drawLine(canvas, 5, 15, size, this.linePaint);
        int i = 0;
        while (i < POINTS.length) {
            float radius = (isLargeNode(i) ? 0.047f : 0.033f) * size;
            Paint fill = i == 22 ? this.centerPaint : this.nodePaint;
            canvas.drawCircle(x(i, size), y(i, size), 1.18f * radius, this.softLinePaint);
            canvas.drawCircle(x(i, size), y(i, size), radius, fill);
            canvas.drawCircle(x(i, size), y(i, size), radius, this.strokePaint);
            i++;
        }
    }

    private void drawLine(Canvas canvas, int from, int to, float size, Paint paint) {
        canvas.drawLine(x(from, size), y(from, size), x(to, size), y(to, size), paint);
    }

    private boolean isLargeNode(int index) {
        return index == 0 || index == 5 || index == 10 || index == 15 || index == 22;
    }

    private float x(int index, float size) {
        return POINTS[index][0] * size;
    }

    private float y(int index, float size) {
        return POINTS[index][1] * size;
    }
}
