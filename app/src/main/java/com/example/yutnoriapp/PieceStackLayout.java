package com.example.yutnoriapp;

final class PieceStackLayout {
    static final int SPACIOUS = 0;
    static final int COMPACT_HORIZONTAL = 1;
    static final int COMPACT_VERTICAL = 2;

    private static final float[][][] SPACIOUS_CENTERS = {
            {{0.50f, 0.50f}},
            {{0.30f, 0.50f}, {0.70f, 0.50f}},
            {{0.50f, 0.27f}, {0.29f, 0.615f}, {0.71f, 0.615f}},
            {{0.31f, 0.31f}, {0.69f, 0.31f}, {0.31f, 0.69f}, {0.69f, 0.69f}}
    };
    private static final float[][][] COMPACT_CENTERS = {
            {{0.50f, 0.50f}},
            {{0.36f, 0.50f}, {0.64f, 0.50f}},
            {{0.50f, 0.36f}, {0.37f, 0.57f}, {0.63f, 0.57f}},
            {{0.37f, 0.37f}, {0.63f, 0.37f}, {0.37f, 0.63f}, {0.63f, 0.63f}}
    };
    private static final float[] RADII = {0.43f, 0.30f, 0.27f, 0.27f};
    private static final float COLLAPSED_RADIUS = 0.43f;

    static int normalizeCount(int count) {
        return Math.max(1, Math.min(4, count));
    }

    static float centerX(int count, int index, float collapseProgress) {
        return centerX(count, index, collapseProgress, SPACIOUS);
    }

    static float centerX(int count, int index, float collapseProgress, int arrangement) {
        return lerp(
                expandedCenter(count, index, arrangement, 0),
                0.50f,
                clamp(collapseProgress));
    }

    static float centerY(int count, int index, float collapseProgress) {
        return centerY(count, index, collapseProgress, SPACIOUS);
    }

    static float centerY(int count, int index, float collapseProgress, int arrangement) {
        return lerp(
                expandedCenter(count, index, arrangement, 1),
                0.50f,
                clamp(collapseProgress));
    }

    static float radius(int count, float collapseProgress) {
        return lerp(RADII[normalizeCount(count) - 1], COLLAPSED_RADIUS, clamp(collapseProgress));
    }

    private static float expandedCenter(int count, int index, int arrangement, int coordinate) {
        int normalizedArrangement = normalizeArrangement(arrangement);
        float[][][] centers = normalizedArrangement == SPACIOUS
                ? SPACIOUS_CENTERS
                : COMPACT_CENTERS;
        float[] center = centers[normalizeCount(count) - 1][index];
        if (normalizedArrangement == COMPACT_VERTICAL) {
            return center[1 - coordinate];
        }
        return center[coordinate];
    }

    private static int normalizeArrangement(int arrangement) {
        return arrangement == COMPACT_HORIZONTAL || arrangement == COMPACT_VERTICAL
                ? arrangement
                : SPACIOUS;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + ((end - start) * progress);
    }

    private PieceStackLayout() {
    }
}
