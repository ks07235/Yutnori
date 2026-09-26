package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public final class PieceStackView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix gradientMatrix = new Matrix();
    private final Path shapePath = new Path();
    private final RectF shapeBounds = new RectF();
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int shape = TeamAppearance.CIRCLE;
    private boolean progressIndicator;
    private boolean finishedIndicator;
    private final Paint pendingOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int brightColor = Color.WHITE;
    private int darkColor = Color.DKGRAY;
    private String pieceLabel = "1";
    private int groupCount = 1;
    private int visualDiameterPx;
    private float collapseProgress;
    private int arrangement = PieceStackLayout.SPACIOUS;
    private RadialGradient fillGradient;

    public PieceStackView(Context context) {
        super(context);
        init();
    }

    public PieceStackView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(dp(1.5f));
        haloPaint.setStyle(Paint.Style.STROKE);
        haloPaint.setColor(Color.WHITE);
        haloPaint.setStrokeWidth(dp(3f));
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        haloPaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        rebuildGradient();
    }

    void configure(int brightColor, int darkColor, int pieceNumber) {
        this.brightColor = brightColor;
        this.darkColor = darkColor;
        pieceLabel = String.valueOf(pieceNumber);
        rebuildGradient();
        invalidate();
    }

    void configureAppearance(int colorId, int shape, int pieceNumber) {
        this.shape = TeamAppearance.isShape(shape) ? shape : TeamAppearance.CIRCLE;
        textPaint.setColor(TeamAppearance.ink(colorId));
        strokePaint.setColor(TeamAppearance.outline(colorId));
        pendingOutlinePaint.setStyle(Paint.Style.STROKE);
        pendingOutlinePaint.setStrokeWidth(dp(1.2f));
        pendingOutlinePaint.setColor(TeamAppearance.label(colorId));
        configure(TeamAppearance.highlight(colorId), TeamAppearance.fill(colorId), pieceNumber);
    }

    void setFinishedIndicator(boolean finished) {
        progressIndicator = true;
        finishedIndicator = finished;
        invalidate();
    }

    void setGroupCount(int groupCount) {
        this.groupCount = PieceStackLayout.normalizeCount(groupCount);
        invalidate();
    }

    int getGroupCount() {
        return groupCount;
    }
    void setArrangement(int arrangement) {
        if (arrangement != PieceStackLayout.COMPACT_HORIZONTAL
                && arrangement != PieceStackLayout.COMPACT_VERTICAL) {
            arrangement = PieceStackLayout.SPACIOUS;
        }
        this.arrangement = arrangement;
        invalidate();
    }


    void setVisualDiameterPx(int visualDiameterPx) {
        this.visualDiameterPx = Math.max(0, visualDiameterPx);
        invalidate();
    }

    void setCollapseProgress(float collapseProgress) {
        this.collapseProgress = Math.max(0f, Math.min(1f, collapseProgress));
        invalidate();
    }

    float getCollapseProgress() {
        return collapseProgress;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float available = Math.min(getWidth(), getHeight());
        float diameter = visualDiameterPx > 0 ? Math.min(visualDiameterPx, available) : available;
        float left = (getWidth() - diameter) / 2f;
        float top = (getHeight() - diameter) / 2f;
        float radius = PieceStackLayout.radius(groupCount, collapseProgress) * diameter;

        for (int index = 0; index < groupCount; index++) {
            float centerX = left + PieceStackLayout.centerX(
                    groupCount,
                    index,
                    collapseProgress,
                    arrangement) * diameter;
            float centerY = top + PieceStackLayout.centerY(
                    groupCount,
                    index,
                    collapseProgress,
                    arrangement) * diameter;
            gradientMatrix.setScale(radius, radius);
            gradientMatrix.postTranslate(centerX, centerY);
            fillGradient.setLocalMatrix(gradientMatrix);
            fillPaint.setShader(fillGradient);
            buildShape(centerX, centerY, Math.max(0f, radius - dp(1.5f)));
            if (!progressIndicator || finishedIndicator) canvas.drawPath(shapePath, fillPaint);
            canvas.drawPath(shapePath, haloPaint);
            canvas.drawPath(shapePath, progressIndicator && !finishedIndicator ? pendingOutlinePaint : strokePaint);
        }

        if (groupCount == 1 && (!progressIndicator || finishedIndicator)) {
            textPaint.setTextSize(diameter * (shape == TeamAppearance.STAR ? 0.30f : 0.36f));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = (getHeight() / 2f) - ((metrics.ascent + metrics.descent) / 2f);
            canvas.drawText(progressIndicator ? "✓" : pieceLabel, getWidth() / 2f, baseline, textPaint);
        }
    }

    private void buildShape(float x, float y, float radius) {
        shapePath.reset();
        if (shape == TeamAppearance.CIRCLE) {
            shapePath.addCircle(x, y, radius, Path.Direction.CW);
        } else if (shape == TeamAppearance.ROUNDED_SQUARE) {
            float half = radius * 0.84f;
            shapeBounds.set(x - half, y - half, x + half, y + half);
            shapePath.addRoundRect(shapeBounds, radius * 0.28f, radius * 0.28f, Path.Direction.CW);
        } else {
            int vertices = shape == TeamAppearance.TRIANGLE ? 3
                    : shape == TeamAppearance.DIAMOND ? 4
                    : shape == TeamAppearance.STAR ? 10 : 6;
            for (int i = 0; i < vertices; i++) {
                double angle = -Math.PI / 2 + i * Math.PI * 2 / vertices;
                float r = shape == TeamAppearance.STAR && i % 2 == 1 ? radius * 0.52f : radius;
                float px = x + (float) Math.cos(angle) * r;
                // Shift the triangle slightly upward to center its numeral within the silhouette.
                float py = y + (float) Math.sin(angle) * r
                        + (shape == TeamAppearance.TRIANGLE ? radius * 0.15f : 0f);
                if (i == 0) shapePath.moveTo(px, py); else shapePath.lineTo(px, py);
            }
            shapePath.close();
        }
    }

    private void rebuildGradient() {
        fillGradient = new RadialGradient(
                -0.32f,
                -0.35f,
                1.45f,
                brightColor,
                darkColor,
                Shader.TileMode.CLAMP);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
