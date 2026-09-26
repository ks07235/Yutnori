package com.example.yutnoriapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PieceStackLayoutTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void expandedLayoutsKeepTheirCenterOfMassOnTheNodeCenter() {
        for (int count = 1; count <= 4; count++) {
            float totalX = 0f;
            float totalY = 0f;
            for (int index = 0; index < count; index++) {
                totalX += PieceStackLayout.centerX(count, index, 0f);
                totalY += PieceStackLayout.centerY(count, index, 0f);
            }

            assertEquals(0.5f, totalX / count, DELTA);
            assertEquals(0.5f, totalY / count, DELTA);
        }
    }

    @Test
    public void everyExpandedPieceFitsInsideItsVisualBounds() {
        for (int count = 1; count <= 4; count++) {
            float radius = PieceStackLayout.radius(count, 0f);
            for (int index = 0; index < count; index++) {
                float x = PieceStackLayout.centerX(count, index, 0f);
                float y = PieceStackLayout.centerY(count, index, 0f);
                assertTrue(x - radius >= 0f);
                assertTrue(x + radius <= 1f);
                assertTrue(y - radius >= 0f);
                assertTrue(y + radius <= 1f);
            }
        }
    }

    @Test
    public void compactLayoutsStayCenteredAndInsideVisualBounds() {
        int[] arrangements = {
                PieceStackLayout.COMPACT_HORIZONTAL,
                PieceStackLayout.COMPACT_VERTICAL
        };
        for (int arrangement : arrangements) {
            for (int count = 2; count <= 4; count++) {
                float totalX = 0f;
                float totalY = 0f;
                float radius = PieceStackLayout.radius(count, 0f);
                for (int index = 0; index < count; index++) {
                    float x = PieceStackLayout.centerX(count, index, 0f, arrangement);
                    float y = PieceStackLayout.centerY(count, index, 0f, arrangement);
                    totalX += x;
                    totalY += y;
                    assertTrue(x - radius >= 0f && x + radius <= 1f);
                    assertTrue(y - radius >= 0f && y + radius <= 1f);
                }
                assertEquals(0.5f, totalX / count, DELTA);
                assertEquals(0.5f, totalY / count, DELTA);
            }
        }
    }

    @Test
    public void compactVerticalRotatesTheTwoPieceLayout() {
        assertEquals(
                PieceStackLayout.centerX(2, 0, 0f, PieceStackLayout.COMPACT_HORIZONTAL),
                PieceStackLayout.centerY(2, 0, 0f, PieceStackLayout.COMPACT_VERTICAL),
                DELTA);
        assertEquals(
                PieceStackLayout.centerY(2, 0, 0f, PieceStackLayout.COMPACT_HORIZONTAL),
                PieceStackLayout.centerX(2, 0, 0f, PieceStackLayout.COMPACT_VERTICAL),
                DELTA);
    }

    @Test
    public void stackedTokensRemainVisiblyLargerThanTheOldMiniMarkers() {
        assertTrue(PieceStackLayout.radius(2, 0f) >= 0.30f);
        assertTrue(PieceStackLayout.radius(3, 0f) >= 0.27f);
        assertTrue(PieceStackLayout.radius(4, 0f) >= 0.27f);
    }

    @Test
    public void collapsedLayoutsBecomeOneCenteredToken() {
        for (int count = 2; count <= 4; count++) {
            for (int index = 0; index < count; index++) {
                assertEquals(0.5f, PieceStackLayout.centerX(count, index, 1f), DELTA);
                assertEquals(0.5f, PieceStackLayout.centerY(count, index, 1f), DELTA);
            }
            assertEquals(PieceStackLayout.radius(1, 0f), PieceStackLayout.radius(count, 1f), DELTA);
        }
    }
}
