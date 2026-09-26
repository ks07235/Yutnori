package com.example.yutnoriapp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import java.util.IdentityHashMap;
import java.util.Map;

/** Node anchors and nearest-node touch arbitration share the artwork viewport. */
public final class BoardOverlayLayout extends FrameLayout {
    private final Map<View, Integer> anchors = new IdentityHashMap<>();
    private View touchTarget;
    private float downX;
    private float downY;
    private boolean touchCancelled;

    public BoardOverlayLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    float[] centerForSpot(int spot) {
        return BoardGeometry.centerInContent(spot, 0f, 0f, getWidth(), getHeight());
    }

    void anchor(View child, int spot) {
        anchors.put(child, spot);
        child.setTranslationX(0f);
        child.setTranslationY(0f);
        requestLayout();
    }

    void releaseAnchor(View child) {
        anchors.remove(child);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) child.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.leftMargin = child.getLeft();
        params.topMargin = child.getTop();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        anchors.remove(child);
        if (touchTarget == child) touchCancelled = true;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        for (Map.Entry<View, Integer> entry : anchors.entrySet()) {
            View child = entry.getKey();
            float[] center = centerForSpot(entry.getValue());
            int x = Math.round(center[0] - child.getMeasuredWidth() / 2f);
            int y = Math.round(center[1] - child.getMeasuredHeight() / 2f);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
        }
    }

    private View targetAt(float x, float y) {
        int nearestSpot = BoardTouchTarget.nearestSpot(x, y, Math.min(getWidth(), getHeight()));
        View best = null;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            Integer spot = anchors.get(child);
            if (spot == null || spot != nearestSpot || !child.isShown()
                    || !child.isEnabled() || !child.isClickable()) continue;
            float[] center = centerForSpot(spot);
            float radius = Math.max(24f * getResources().getDisplayMetrics().density,
                    Math.max(child.getWidth(), child.getHeight()) / 2f);
            if (Math.hypot(x - center[0], y - center[1]) > radius) continue;
            if (best == null || child.getElevation() >= best.getElevation()) best = child;
        }
        return best;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchTarget = targetAt(event.getX(), event.getY());
            if (touchTarget == null) return true;
            downX = event.getX();
            downY = event.getY();
            touchCancelled = false;
            touchTarget.setPressed(true);
        }
        if (touchTarget == null) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
            if (Math.hypot(event.getX() - downX, event.getY() - downY) > slop) {
                touchCancelled = true;
                touchTarget.setPressed(false);
            }
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            View target = touchTarget;
            touchTarget = null;
            target.setPressed(false);
            if (!touchCancelled && event.getActionMasked() == MotionEvent.ACTION_UP
                    && target.isShown() && target.isEnabled()) target.performClick();
        }
        return true;
    }
}
