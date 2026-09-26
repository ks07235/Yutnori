package com.example.yutnoriapp;

final class BoardGeometry {
    static final int START_SPOT = 15;
    static final int START_NEXT_SPOT = 16;
    // One third of a shortcut lands on its first node. Keep markers in the clear gap before it.
    static final float SHORTCUT_DIRECTION_POSITION = 0.20f;
    static final int[][] OUTER_ROUTE_SEGMENTS = {
            {15, 0}, {0, 5}, {5, 10}, {10, 15}
    };
    // The third value chooses which empty side of the diagonal receives the marker.
    static final int[][] SHORTCUT_DIRECTION_MARKERS = {
            {0, 22, 1},
            {5, 22, -1},
            {22, 15, 1}
    };


    private static final float[][] UNROTATED_POINTS = {
            {0.90f, 0.90f}, {0.90f, 0.74f}, {0.90f, 0.58f}, {0.90f, 0.42f}, {0.90f, 0.26f},
            {0.90f, 0.10f}, {0.74f, 0.10f}, {0.58f, 0.10f}, {0.42f, 0.10f}, {0.26f, 0.10f},
            {0.10f, 0.10f}, {0.10f, 0.26f}, {0.10f, 0.42f}, {0.10f, 0.58f}, {0.10f, 0.74f},
            {0.10f, 0.90f}, {0.26f, 0.90f}, {0.42f, 0.90f}, {0.58f, 0.90f}, {0.74f, 0.90f},
            {0.76f, 0.24f}, {0.63f, 0.37f}, {0.50f, 0.50f}, {0.37f, 0.63f}, {0.24f, 0.76f},
            {0.24f, 0.24f}, {0.37f, 0.37f}, {0.63f, 0.63f}, {0.76f, 0.76f}
    };

    // Rotate the existing visual board 90 degrees counter-clockwise around its center.
    // Logical node ids remain untouched, so routes, shortcuts, saved games, and Back Do stay compatible.
    static final float[][] POINTS = rotateCounterClockwise(UNROTATED_POINTS);

    static boolean isLargeNode(int spotIndex) {
        return spotIndex == 0
                || spotIndex == 5
                || spotIndex == 10
                || spotIndex == 15
                || spotIndex == 22;
    }
    static boolean isDiagonalNode(int spotIndex) {
        return spotIndex >= 20 && spotIndex < POINTS.length;
    }

    static int stackArrangementForSpot(int spotIndex) {
        if (spotIndex < 0 || spotIndex >= POINTS.length
                || isLargeNode(spotIndex)
                || isDiagonalNode(spotIndex)) {
            return PieceStackLayout.SPACIOUS;
        }

        float x = POINTS[spotIndex][0];
        boolean verticalEdge = x <= 0.11f || x >= 0.89f;
        return verticalEdge
                ? PieceStackLayout.COMPACT_HORIZONTAL
                : PieceStackLayout.COMPACT_VERTICAL;
    }


    static float[] centerInContent(
            int spotIndex,
            float contentLeft,
            float contentTop,
            float contentWidth,
            float contentHeight) {
        if (spotIndex < 0 || spotIndex >= POINTS.length) {
            throw new IllegalArgumentException("Invalid board spot: " + spotIndex);
        }
        float size = Math.min(contentWidth, contentHeight);
        return new float[]{
                contentLeft + POINTS[spotIndex][0] * size,
                contentTop + POINTS[spotIndex][1] * size
        };
    }

    private static float[][] rotateCounterClockwise(float[][] source) {
        float[][] rotated = new float[source.length][2];
        for (int index = 0; index < source.length; index++) {
            float x = source[index][0];
            float y = source[index][1];
            rotated[index][0] = y;
            rotated[index][1] = 1f - x;
        }
        return rotated;
    }

    private BoardGeometry() {
    }
}
