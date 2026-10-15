package com.example.yutnoriapp;

import java.util.Arrays;

/** Logical match totals; snapshots travel with the engine's undo/save state. */
final class MatchStatistics {
    static final int[] STEPS = {1, 2, 3, 4, 5, -1};
    private static final int STRIDE = 9;
    private final int[] totals = new int[4 * STRIDE];
    private boolean complete = true;

    void reset() { Arrays.fill(totals, 0); complete = true; }
    boolean isComplete() { return complete; }
    int rolls(int team, int index) { return totals[team * STRIDE + index]; }
    int captures(int team) { return totals[team * STRIDE + 6]; }
    int moves(int team) { return totals[team * STRIDE + 7]; }
    int maxStack(int team) { return Math.max(1, totals[team * STRIDE + 8]); }

    void recordRoll(int team, int steps) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == steps) increment(team * STRIDE + i, 1);
        }
    }

    void undoRoll(int team, int steps) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] == steps) totals[team * STRIDE + i] = Math.max(0, rolls(team, i) - 1);
        }
    }

    void recordMove(YutGameEngine.MoveResult result) {
        if (!result.success) return;
        int base = result.teamId * STRIDE;
        increment(base + 7, result.animationSegments.size());
        for (YutGameEngine.MoveAnimation segment : result.animationSegments) {
            increment(base + 6, segment.caughtPieces.size());
            totals[base + 8] = Math.max(totals[base + 8],
                    Math.max(segment.pieceIds.size(), segment.arrivedPieceIds.size()));
        }
    }

    private void increment(int index, int amount) {
        totals[index] = (int) Math.min(Integer.MAX_VALUE, (long) totals[index] + amount);
    }

    int[] snapshot() {
        int[] data = new int[totals.length + 2];
        data[0] = 1;
        data[1] = complete ? 1 : 0;
        System.arraycopy(totals, 0, data, 2, totals.length);
        return data;
    }

    void restore(int[] data) {
        Arrays.fill(totals, 0);
        complete = false;
        if (!valid(data)) return;
        complete = data[1] == 1;
        System.arraycopy(data, 2, totals, 0, totals.length);
    }

    static boolean valid(int[] data) {
        if (data == null || data.length != 38 || data[0] != 1 || data[1] < 0 || data[1] > 1) return false;
        for (int i = 2; i < data.length; i++) {
            if (data[i] < 0 || ((i - 2) % STRIDE == 8 && data[i] > 4)) return false;
        }
        return true;
    }
}
