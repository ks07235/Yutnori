package com.example.yutnoriapp;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatchStatisticsTest {
    @Test public void countsAllRollTypesAndSnapshotsAreIndependent() {
        MatchStatistics stats = new MatchStatistics();
        int[] before = stats.snapshot();
        for (int step : MatchStatistics.STEPS) stats.recordRoll(2, step);
        for (int i = 0; i < 6; i++) assertEquals(1, stats.rolls(2, i));
        stats.undoRoll(2, -1);
        assertEquals(0, stats.rolls(2, 5));
        stats.restore(before);
        for (int i = 0; i < 6; i++) assertEquals(0, stats.rolls(2, i));
        assertTrue(stats.isComplete());
    }

    @Test public void countsCapturedPiecesNotJustEventsAndKeepsLargestStack() {
        MatchStatistics stats = new MatchStatistics();
        YutGameEngine.MoveResult result = YutGameEngine.MoveResult.success(0, 4);
        YutGameEngine.MoveAnimation move = new YutGameEngine.MoveAnimation(
                Arrays.asList(0, 1), 1, Arrays.asList(2, 3));
        move.arrivedPieceIds.add(2);
        move.caughtPieces.add(new YutGameEngine.PieceRef(1, 0));
        move.caughtPieces.add(new YutGameEngine.PieceRef(1, 1));
        result.animationSegments.add(move);
        stats.recordMove(result);
        assertEquals(2, stats.captures(0));
        assertEquals(1, stats.moves(0));
        assertEquals(3, stats.maxStack(0));
        int[] after = stats.snapshot();
        stats.recordMove(result);
        stats.restore(after);
        assertEquals(2, stats.captures(0));
        stats.recordMove(YutGameEngine.MoveResult.failure(""));
        assertEquals(1, stats.moves(0));
    }

    @Test public void legacyOrInvalidSnapshotsNeverInventACompleteMatch() {
        MatchStatistics stats = new MatchStatistics();
        stats.restore(null);
        assertFalse(stats.isComplete());
        stats.recordRoll(0, 4);
        stats.restore(stats.snapshot());
        assertFalse(stats.isComplete());
        assertEquals(1, stats.rolls(0, 3));
        int[] invalid = stats.snapshot();
        invalid[10] = 5;
        stats.restore(invalid);
        assertFalse(stats.isComplete());
        assertEquals(0, stats.rolls(0, 3));
        stats.reset();
        assertTrue(stats.isComplete());
    }
}
