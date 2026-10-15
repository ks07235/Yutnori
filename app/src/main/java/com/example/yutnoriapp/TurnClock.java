package com.example.yutnoriapp;

/**
 * Monotonic turn-time state. The Activity coordinates commands and persistence;
 * this class has no Android, View, Handler, or game-engine dependency.
 */
final class TurnClock {
    long remainingTurnMillis;
    boolean isTimerPaused;
    boolean isTimerHeldForAnimation;
    int timerDialogHoldCount;
    long timerCheckpointElapsedMillis;

    TurnClock(long durationMillis) {
        remainingTurnMillis = durationMillis;
    }

    boolean shouldRun(boolean gameEligible) {
        return gameEligible && !isTimerPaused && !isTimerHeldForAnimation
                && timerDialogHoldCount == 0;
    }

    void sync(long now, boolean gameEligible) {
        if (timerCheckpointElapsedMillis <= 0L) {
            timerCheckpointElapsedMillis = now;
            return;
        }
        if (shouldRun(gameEligible)) {
            long elapsed = Math.max(0L, now - timerCheckpointElapsedMillis);
            remainingTurnMillis = Math.max(0L, remainingTurnMillis - elapsed);
        }
        timerCheckpointElapsedMillis = now;
    }

    void restart(long durationMillis, long now) {
        isTimerHeldForAnimation = false;
        remainingTurnMillis = durationMillis;
        isTimerPaused = false;
        timerCheckpointElapsedMillis = now;
    }

    void beginDialogHold() {
        timerDialogHoldCount++;
    }

    void endDialogHold(long now) {
        if (timerDialogHoldCount > 0) timerDialogHoldCount--;
        timerCheckpointElapsedMillis = now;
    }
}
