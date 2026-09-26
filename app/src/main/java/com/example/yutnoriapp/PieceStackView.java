package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
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
            canvas.drawCircle(centerX, centerY, radius, fillPaint);
            canvas.drawCircle(centerX, centerY, radius, strokePaint);
        }

        if (groupCount == 1) {
            textPaint.setTextSize(diameter * 0.40f);
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = (getHeight() / 2f) - ((metrics.ascent + metrics.descent) / 2f);
            canvas.drawText(pieceLabel, getWidth() / 2f, baseline, textPaint);
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
