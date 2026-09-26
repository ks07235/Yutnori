package com.example.yutnoriapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/** Owns the square viewport shared by the artwork and interactive board layer. */
public final class BoardSurfaceLayout extends SquareFrameLayout {
    public BoardSurfaceLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        int size = Math.max(0, Math.min(width, height));
        int x = getPaddingLeft() + (width - size) / 2;
        int y = getPaddingTop() + (height - size) / 2;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.layout(x, y, x + size, y + size);
        }
    }
}
