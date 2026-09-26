package com.example.yutnoriapp;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameStateStorePolicyTest {
    @Test
    public void recentSavedGameResumesWithoutPrompt() {
        long now = 2_000_000_000L;
        assertFalse(GameStateStore.shouldConfirmSavedGame(
                now - GameStateStore.SAVED_GAME_CONFIRM_AFTER_MILLIS + 1L,
                now));
    }

    @Test
    public void dayOldSavedGameRequiresAChoice() {
        long now = 2_000_000_000L;
        assertTrue(GameStateStore.shouldConfirmSavedGame(
                now - GameStateStore.SAVED_GAME_CONFIRM_AFTER_MILLIS,
                now));
    }

    @Test
    public void legacySavedGameWithoutTimestampRequiresAChoice() {
        assertTrue(GameStateStore.shouldConfirmSavedGame(0L, 2_000_000_000L));
    }

    @Test
    public void futureTimestampDoesNotDiscardOrInterruptTheGame() {
        assertFalse(GameStateStore.shouldConfirmSavedGame(2_000_000_001L, 2_000_000_000L));
    }
}
