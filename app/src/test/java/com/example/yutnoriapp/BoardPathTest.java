package com.example.yutnoriapp;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BoardPathTest {
    private final BoardPath path = new BoardPath();

    @Test
    public void outerRouteStillRunsFromStartToFinishInLogicalOrder() {
        Piece piece = new Piece(0, 0);

        BoardPath.MoveTrace trace = path.trace(piece, 21);

        int[] expected = {
                16, 17, 18, 19, 0,
                1, 2, 3, 4, 5,
                6, 7, 8, 9, 10,
                11, 12, 13, 14, 15,
                BoardPath.END_NODE
        };
        assertArrayEquals(expected, toArray(trace.visitedNodes));
        assertEquals(BoardPath.END_NODE, trace.node);
    }

    @Test
    public void cornerZeroShortcutStillCrossesCenterToNodeTen() {
        Piece piece = pieceAt(0, 0);

        BoardPath.MoveTrace trace = path.trace(piece, 6);

        assertArrayEquals(new int[]{28, 27, 29, 26, 25, 10}, toArray(trace.visitedNodes));
        assertEquals(10, trace.node);
        assertEquals(3, trace.route);
    }

    @Test
    public void cornerFiveShortcutStillCrossesCenterToFinishCorner() {
        Piece piece = pieceAt(5, 0);

        BoardPath.MoveTrace trace = path.trace(piece, 6);

        assertArrayEquals(new int[]{20, 21, 22, 23, 24, 15}, toArray(trace.visitedNodes));
        assertEquals(15, trace.node);
        assertEquals(1, trace.route);
    }

    @Test
    public void eitherLogicalCenterContinuesTowardFinish() {
        BoardPath.MoveTrace firstCenter = path.trace(pieceAt(22, 1), 1);
        BoardPath.MoveTrace secondCenter = path.trace(pieceAt(29, 3), 1);

        assertEquals(23, firstCenter.node);
        assertEquals(23, secondCenter.node);
        assertEquals(1, firstCenter.route);
        assertEquals(1, secondCenter.route);
        assertTrue(path.isSameBoardSpot(22, 29));
        assertEquals(22, path.visualSpotFor(29));
    }

    @Test
    public void backDoPreservesShortcutHistoryAtMergeCorners() {
        assertEquals(24, path.trace(pieceAt(15, 1), -1).node);
        assertEquals(25, path.trace(pieceAt(10, 3), -1).node);
        assertEquals(21, path.trace(pieceAt(22, 1), -1).node);
        assertEquals(27, path.trace(pieceAt(29, 3), -1).node);
    }

    @Test
    public void waitingPieceCannotMoveWithBackDo() {
        Piece piece = new Piece(0, 0);

        BoardPath.MoveTrace trace = path.trace(piece, -1);

        assertEquals(BoardPath.START_NODE, trace.node);
        assertTrue(trace.visitedNodes.isEmpty());
    }

    private Piece pieceAt(int node, int route) {
        Piece piece = new Piece(0, 0);
        piece.position = node;
        piece.route = route;
        return piece;
    }

    private int[] toArray(java.util.List<Integer> values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index);
        }
        return result;
    }
}
