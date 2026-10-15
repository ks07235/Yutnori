package com.example.yutnoriapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class TurnClockTest {
    private TurnClock clock() {
        TurnClock clock = new TurnClock(180_000L);
        clock.restart(180_000L, 1_000L);
        return clock;
    }

    @Test public void elapsedTimeIsChargedOnceAndClampedAtZero() {
        TurnClock clock = clock();
        clock.sync(11_000L, true);
        clock.sync(11_000L, true);
        assertEquals(170_000L, clock.remainingTurnMillis);
        clock.sync(900_000L, true);
        assertEquals(0L, clock.remainingTurnMillis);
    }

    @Test public void nestedDialogsAndAnimationCannotClearManualPause() {
        TurnClock clock = clock();
        clock.sync(2_000L, true);
        clock.isTimerPaused = true;
        clock.beginDialogHold();
        clock.beginDialogHold();
        clock.isTimerHeldForAnimation = true;
        clock.sync(30_000L, true);
        clock.endDialogHold(30_000L);
        assertFalse(clock.shouldRun(true));
        clock.endDialogHold(40_000L);
        clock.isTimerHeldForAnimation = false;
        clock.sync(50_000L, true);
        assertEquals(179_000L, clock.remainingTurnMillis);
        assertTrue(clock.isTimerPaused);
        clock.isTimerPaused = false;
        clock.sync(51_000L, true);
        assertEquals(178_000L, clock.remainingTurnMillis);
    }

    @Test public void stoppedActivityDoesNotChargeOfflineTime() {
        TurnClock clock = clock();
        clock.sync(2_000L, true);
        clock.sync(20_000L, false);
        clock.sync(800_000L, false);
        assertEquals(179_000L, clock.remainingTurnMillis);
        clock.timerCheckpointElapsedMillis = 900_000L;
        clock.sync(901_000L, true);
        assertEquals(178_000L, clock.remainingTurnMillis);
    }

    @Test public void eachHoldIndependentlyPreventsCharging() {
        for (int reason = 0; reason < 3; reason++) {
            TurnClock clock = clock();
            clock.isTimerPaused = reason == 0;
            clock.isTimerHeldForAnimation = reason == 1;
            if (reason == 2) clock.beginDialogHold();
            clock.sync(120_000L, true);
            assertEquals(180_000L, clock.remainingTurnMillis);
        }
    }

    @Test public void restartPreservesDialogHoldButResetsTurnPause() {
        TurnClock clock = clock();
        clock.beginDialogHold();
        clock.isTimerPaused = true;
        clock.isTimerHeldForAnimation = true;
        clock.restart(60_000L, 5_000L);
        assertFalse(clock.isTimerPaused);
        assertFalse(clock.isTimerHeldForAnimation);
        assertEquals(1, clock.timerDialogHoldCount);
        clock.sync(9_000L, true);
        assertEquals(60_000L, clock.remainingTurnMillis);
        clock.endDialogHold(9_000L);
        clock.endDialogHold(9_000L);
        assertEquals(0, clock.timerDialogHoldCount);
        clock.sync(10_000L, true);
        assertEquals(59_000L, clock.remainingTurnMillis);
    }

    @Test public void bonusAndUndoRestorationUseSameClockState() {
        TurnClock clock = clock();
        clock.sync(11_000L, true);
        long snapshot = clock.remainingTurnMillis;
        clock.remainingTurnMillis += 30_000L;
        clock.sync(12_000L, true);
        assertEquals(199_000L, clock.remainingTurnMillis);
        clock.remainingTurnMillis = snapshot;
        clock.timerCheckpointElapsedMillis = 20_000L;
        clock.sync(21_000L, true);
        assertEquals(169_000L, clock.remainingTurnMillis);
    }

    @Test public void unlimitedAndGameOverEligibilityDoNotConsumeTime() {
        TurnClock clock = clock();
        clock.sync(500_000L, false);
        assertEquals(180_000L, clock.remainingTurnMillis);
        clock.restart(0L, 500_000L);
        clock.sync(900_000L, false);
        assertEquals(0L, clock.remainingTurnMillis);
    }

    @Test public void uninitializedAndBackwardsClockCannotAddTime() {
        TurnClock clock = new TurnClock(180_000L);
        clock.sync(1_000L, true);
        assertEquals(180_000L, clock.remainingTurnMillis);
        clock.sync(900L, true);
        assertEquals(180_000L, clock.remainingTurnMillis);
        clock.sync(1_900L, true);
        assertEquals(179_000L, clock.remainingTurnMillis);
    }
}
