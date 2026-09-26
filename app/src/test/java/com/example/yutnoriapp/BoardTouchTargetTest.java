package com.example.yutnoriapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class BoardTouchTargetTest {
    @Test
    public void everyNodeRetainsItsTouchTargetAtSmallAndLargeSizes() {
        for (float size : new float[]{120, 160, 240, 320, 600, 1024}) {
            for (int node = 0; node < BoardGeometry.POINTS.length; node++) {
                float[] p = BoardGeometry.POINTS[node];
                assertEquals(node, BoardTouchTarget.nearestSpot(p[0] * size, p[1] * size, size));
            }
        }
    }

    @Test
    public void adjacentTouchAreasArePartitionedEvenWhenButtonsOverlap() {
        float size = 160;
        float[] first = BoardGeometry.POINTS[16];
        float[] second = BoardGeometry.POINTS[17];
        float midY = (first[1] + second[1]) * size / 2;
        assertEquals(16, BoardTouchTarget.nearestSpot(first[0] * size, midY + 1, size));
        assertEquals(17, BoardTouchTarget.nearestSpot(first[0] * size, midY - 1, size));
    }
}
