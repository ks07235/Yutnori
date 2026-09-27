package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class YutBoardView extends View {

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint softLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint directionArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startLabelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();
    private final RectF boardRect = new RectF();

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
        centerPaint.setColor(getResources().getColor(R.color.board_node));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(getResources().getColor(R.color.board_node_stroke));

        directionArrowPaint.setStyle(Paint.Style.STROKE);
        directionArrowPaint.setStrokeCap(Paint.Cap.ROUND);
        directionArrowPaint.setStrokeJoin(Paint.Join.ROUND);
        directionArrowPaint.setColor(getResources().getColor(R.color.board_direction));

        startLabelTextPaint.setColor(getResources().getColor(R.color.board_start_text));
        startLabelTextPaint.setTextAlign(Paint.Align.CENTER);
        startLabelTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

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

        boardRect.set(left, top, right, bottom);
        canvas.drawRoundRect(boardRect, size * 0.045f, size * 0.045f, boardFillPaint);
        canvas.drawRoundRect(boardRect, size * 0.045f, size * 0.045f, softLinePaint);
        canvas.drawRoundRect(boardRect, size * 0.035f, size * 0.035f, linePaint);

        // Route lines stay continuous and are never used as the arrow layer.
        drawBoardRoutes(canvas, size, softLinePaint);
        drawBoardRoutes(canvas, size, linePaint);

        for (int[] segment : BoardGeometry.OUTER_ROUTE_SEGMENTS) {
            drawOuterDirectionMarker(
                    canvas,
                    segment[0],
                    segment[1],
                    size,
                    segment[0] == BoardGeometry.START_SPOT);
        }
        for (int[] marker : BoardGeometry.SHORTCUT_DIRECTION_MARKERS) {
            drawOffsetDirectionMarker(
                    canvas,
                    marker[0],
                    marker[1],
                    marker[2],
                    BoardGeometry.SHORTCUT_DIRECTION_POSITION,
                    size,
                    false);
        }

        for (int i = 0; i < BoardGeometry.POINTS.length; i++) {
            float radius = BoardGeometry.isLargeNode(i) ? size * 0.047f : size * 0.033f;
            Paint fill = i == 22 ? centerPaint : nodePaint;
            canvas.drawCircle(x(i, size), y(i, size), radius * 1.18f, softLinePaint);
            canvas.drawCircle(x(i, size), y(i, size), radius, fill);
            canvas.drawCircle(x(i, size), y(i, size), radius, strokePaint);
            if (i == 22 || i == BoardGeometry.START_SPOT) {
                canvas.drawCircle(x(i, size), y(i, size), radius * 0.76f, strokePaint);
            }
        }
        drawStartLabel(canvas, size);
    }

    private void drawBoardRoutes(Canvas canvas, float size, Paint paint) {
        for (int[] segment : BoardGeometry.OUTER_ROUTE_SEGMENTS) {
            drawLine(canvas, segment[0], segment[1], size, paint);
        }
        drawLine(canvas, 10, 0, size, paint);
        drawLine(canvas, 5, 15, size, paint);
    }

    private void drawLine(Canvas canvas, int from, int to, float size, Paint paint) {
        canvas.drawLine(x(from, size), y(from, size), x(to, size), y(to, size), paint);
    }


    private float x(int index, float size) {
        return BoardGeometry.POINTS[index][0] * size;
    }

    private void drawOuterDirectionMarker(
            Canvas canvas,
            int from,
            int to,
            float size,
            boolean emphasized) {
        float fromX = x(from, size);
        float fromY = y(from, size);
        float dx = x(to, size) - fromX;
        float dy = y(to, size) - fromY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= 0f) {
            return;
        }
        float unitX = dx / distance;
        float unitY = dy / distance;
        float normalX = -unitY;
        float normalY = unitX;
        float midpointX = fromX + dx * 0.5f;
        float midpointY = fromY + dy * 0.5f;
        float towardCenterX = size * 0.5f - midpointX;
        float towardCenterY = size * 0.5f - midpointY;
        int inwardSide = normalX * towardCenterX + normalY * towardCenterY >= 0f ? 1 : -1;
        drawOffsetDirectionMarker(canvas, from, to, inwardSide, 0.5f, size, emphasized);
    }

    private void drawOffsetDirectionMarker(
            Canvas canvas,
            int from,
            int to,
            int side,
            float position,
            float size,
            boolean emphasized) {
        float fromX = x(from, size);
        float fromY = y(from, size);
        float dx = x(to, size) - fromX;
        float dy = y(to, size) - fromY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance <= 0f) {
            return;
        }

        float unitX = dx / distance;
        float unitY = dy / distance;
        float normalX = -unitY;
        float normalY = unitX;
        float offset = size * (emphasized ? 0.034f : 0.028f);
        float centerX = fromX + dx * position + normalX * side * offset;
        float centerY = fromY + dy * position + normalY * side * offset;
        float arrowLength = size * (emphasized ? 0.064f : 0.050f);
        float headLength = arrowLength * 0.34f;
        float headWidth = arrowLength * 0.24f;
        float tailX = centerX - unitX * arrowLength * 0.5f;
        float tailY = centerY - unitY * arrowLength * 0.5f;
        float tipX = centerX + unitX * arrowLength * 0.5f;
        float tipY = centerY + unitY * arrowLength * 0.5f;

        arrowPath.reset();
        arrowPath.moveTo(tailX, tailY);
        arrowPath.lineTo(tipX, tipY);
        arrowPath.moveTo(tipX, tipY);
        arrowPath.lineTo(
                tipX - unitX * headLength + normalX * headWidth,
                tipY - unitY * headLength + normalY * headWidth);
        arrowPath.moveTo(tipX, tipY);
        arrowPath.lineTo(
                tipX - unitX * headLength - normalX * headWidth,
                tipY - unitY * headLength - normalY * headWidth);
        directionArrowPaint.setStrokeWidth(
                Math.max(2f, size * (emphasized ? 0.0065f : 0.005f)));
        canvas.drawPath(arrowPath, directionArrowPaint);
    }

    private void drawStartLabel(Canvas canvas, float size) {
        float centerX = x(BoardGeometry.START_SPOT, size);
        float centerY = y(BoardGeometry.START_SPOT, size);
        String label = getResources().getString(R.string.board_start);
        float textSize = size * 0.024f;
        float maxWidth = size * 0.072f;
        startLabelTextPaint.setTextSize(textSize);
        float measuredWidth = startLabelTextPaint.measureText(label);
        if (measuredWidth > maxWidth) {
            startLabelTextPaint.setTextSize(textSize * maxWidth / measuredWidth);
        }
        Paint.FontMetrics metrics = startLabelTextPaint.getFontMetrics();
        float baseline = centerY - ((metrics.ascent + metrics.descent) * 0.5f);
        canvas.drawText(label, centerX, baseline, startLabelTextPaint);
    }

    private float y(int index, float size) {
        return BoardGeometry.POINTS[index][1] * size;
    }
}
