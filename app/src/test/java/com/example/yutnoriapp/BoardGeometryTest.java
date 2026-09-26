package com.example.yutnoriapp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoardGeometryTest {
    private static final float DELTA = 0.0001f;

    @Test
    public void startIsLowerRightAndFirstMoveGoesUp() {
        float[] start = BoardGeometry.POINTS[BoardGeometry.START_SPOT];
        float[] first = BoardGeometry.POINTS[16];

        assertEquals(0.90f, start[0], DELTA);
        assertEquals(0.90f, start[1], DELTA);
        assertEquals(start[0], first[0], DELTA);
        assertTrue(first[1] < start[1]);
        assertEquals(0.74f, first[1], DELTA);
    }

    @Test
    public void rotationKeepsCenterAndEveryPointInsideBoard() {
        assertEquals(0.50f, BoardGeometry.POINTS[22][0], DELTA);
        assertEquals(0.50f, BoardGeometry.POINTS[22][1], DELTA);

        for (float[] point : BoardGeometry.POINTS) {
            assertTrue(point[0] >= 0f && point[0] <= 1f);
            assertTrue(point[1] >= 0f && point[1] <= 1f);
        }
    }

    @Test
    public void normalNodesStaySmallerThanCornersAndCenter() {
        assertTrue(BoardGeometry.isLargeNode(0));
        assertTrue(BoardGeometry.isLargeNode(5));
        assertTrue(BoardGeometry.isLargeNode(10));
        assertTrue(BoardGeometry.isLargeNode(15));
        assertTrue(BoardGeometry.isLargeNode(22));
        assertFalse(BoardGeometry.isLargeNode(16));
    }
    @Test
    public void directionMarkersFollowTheRealRouteWithoutChangingPathOrder() {
        assertEquals(16, BoardGeometry.START_NEXT_SPOT);
        assertEquals(0.20f, BoardGeometry.SHORTCUT_DIRECTION_POSITION, DELTA);
        assertEquals(4, BoardGeometry.OUTER_ROUTE_SEGMENTS.length);
        assertEquals(15, BoardGeometry.OUTER_ROUTE_SEGMENTS[0][0]);
        assertEquals(0, BoardGeometry.OUTER_ROUTE_SEGMENTS[0][1]);
        assertEquals(0, BoardGeometry.OUTER_ROUTE_SEGMENTS[1][0]);
        assertEquals(5, BoardGeometry.OUTER_ROUTE_SEGMENTS[1][1]);
        assertEquals(5, BoardGeometry.OUTER_ROUTE_SEGMENTS[2][0]);
        assertEquals(10, BoardGeometry.OUTER_ROUTE_SEGMENTS[2][1]);
        assertEquals(10, BoardGeometry.OUTER_ROUTE_SEGMENTS[3][0]);
        assertEquals(15, BoardGeometry.OUTER_ROUTE_SEGMENTS[3][1]);

        assertEquals(3, BoardGeometry.SHORTCUT_DIRECTION_MARKERS.length);
        assertEquals(0, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[0][0]);
        assertEquals(22, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[0][1]);
        assertEquals(5, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[1][0]);
        assertEquals(22, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[1][1]);
        assertEquals(22, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[2][0]);
        assertEquals(15, BoardGeometry.SHORTCUT_DIRECTION_MARKERS[2][1]);
    }

    @Test
    public void stackLayoutUsesFreeSpaceAroundEachRoute() {
        assertEquals(
                PieceStackLayout.COMPACT_HORIZONTAL,
                BoardGeometry.stackArrangementForSpot(16));
        assertEquals(
                PieceStackLayout.COMPACT_VERTICAL,
                BoardGeometry.stackArrangementForSpot(11));
        assertEquals(
                PieceStackLayout.SPACIOUS,
                BoardGeometry.stackArrangementForSpot(21));
        assertEquals(
                PieceStackLayout.SPACIOUS,
                BoardGeometry.stackArrangementForSpot(22));
    }

    @Test
    public void contentCoordinatesUseTheBoardArtsInsetAndSquareSize() {
        float[] start = BoardGeometry.centerInContent(BoardGeometry.START_SPOT, 10f, 14f, 320f, 300f);
        float[] center = BoardGeometry.centerInContent(22, 10f, 14f, 320f, 300f);

        assertEquals(280f, start[0], DELTA);
        assertEquals(284f, start[1], DELTA);
        assertEquals(160f, center[0], DELTA);
        assertEquals(164f, center[1], DELTA);
    }
}
