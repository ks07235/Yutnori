package com.example.yutnoriapp;

final class BoardTouchTarget {
    static int nearestSpot(float x, float y, float size) {
        int nearest = -1;
        float shortest = Float.MAX_VALUE;
        for (int i = 0; i < BoardGeometry.POINTS.length; i++) {
            float dx = x - BoardGeometry.POINTS[i][0] * size;
            float dy = y - BoardGeometry.POINTS[i][1] * size;
            float distance = dx * dx + dy * dy;
            if (distance < shortest) {
                shortest = distance;
                nearest = i;
            }
        }
        return nearest;
    }

    private BoardTouchTarget() {}
}
