package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class YutBoardView extends View {
    private static final float[][] POINTS = {
            {0.90f, 0.90f}, {0.90f, 0.74f}, {0.90f, 0.58f}, {0.90f, 0.42f}, {0.90f, 0.26f},
            {0.90f, 0.10f}, {0.74f, 0.10f}, {0.58f, 0.10f}, {0.42f, 0.10f}, {0.26f, 0.10f},
            {0.10f, 0.10f}, {0.10f, 0.26f}, {0.10f, 0.42f}, {0.10f, 0.58f}, {0.10f, 0.74f},
            {0.10f, 0.90f}, {0.26f, 0.90f}, {0.42f, 0.90f}, {0.58f, 0.90f}, {0.74f, 0.90f},
            {0.76f, 0.24f}, {0.63f, 0.37f}, {0.50f, 0.50f}, {0.37f, 0.63f}, {0.24f, 0.76f},
            {0.24f, 0.24f}, {0.37f, 0.37f}, {0.63f, 0.63f}, {0.76f, 0.76f}
    };

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public YutBoardView(Context context) {
        super(context);
        init();
    }

    public YutBoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public YutBoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(getResources().getColor(R.color.board_line));

        softLinePaint.setStyle(Paint.Style.STROKE);
        softLinePaint.setStrokeCap(Paint.Cap.ROUND);
        softLinePaint.setColor(getResources().getColor(R.color.board_line_soft));

        boardFillPaint.setStyle(Paint.Style.FILL);
        boardFillPaint.setColor(getResources().getColor(R.color.surface_board));

        nodePaint.setStyle(Paint.Style.FILL);
        nodePaint.setColor(getResources().getColor(R.color.board_node));

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(getResources().getColor(R.color.board_center));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(getResources().getColor(R.color.board_node_stroke));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float pad = size * 0.035f;
        float left = pad;
        float top = pad;
        float right = size - pad;
        float bottom = size - pad;
        float lineWidth = Math.max(4f, size * 0.012f);
        softLinePaint.setStrokeWidth(lineWidth * 2.1f);
        linePaint.setStrokeWidth(lineWidth);
        strokePaint.setStrokeWidth(Math.max(2f, size * 0.006f));

        RectF boardRect = new RectF(left, top, right, bottom);
        canvas.drawRoundRect(boardRect, size * 0.045f, size * 0.045f, boardFillPaint);
        canvas.drawRoundRect(boardRect, size * 0.045f, size * 0.045f, softLinePaint);
        canvas.drawRoundRect(boardRect, size * 0.035f, size * 0.035f, linePaint);
        drawLine(canvas, 10, 0, size, softLinePaint);
        drawLine(canvas, 5, 15, size, softLinePaint);
        drawLine(canvas, 10, 0, size, linePaint);
        drawLine(canvas, 5, 15, size, linePaint);

        for (int i = 0; i < POINTS.length; i++) {
            float radius = isLargeNode(i) ? size * 0.047f : size * 0.033f;
            Paint fill = i == 22 ? centerPaint : nodePaint;
            canvas.drawCircle(x(i, size), y(i, size), radius * 1.18f, softLinePaint);
            canvas.drawCircle(x(i, size), y(i, size), radius, fill);
            canvas.drawCircle(x(i, size), y(i, size), radius, strokePaint);
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
