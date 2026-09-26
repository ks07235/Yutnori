package com.example.yutnoriapp;

import com.google.android.play.core.install.model.UpdateAvailability;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppUpdateCheckerTest {
    @Test
    public void promptsOnlyForANewerAvailablePlayVersion() {
        assertTrue(AppUpdateChecker.shouldPrompt(
                UpdateAvailability.UPDATE_AVAILABLE,
                42,
                41));
        assertFalse(AppUpdateChecker.shouldPrompt(
                UpdateAvailability.UPDATE_AVAILABLE,
                41,
                41));
        assertFalse(AppUpdateChecker.shouldPrompt(
                UpdateAvailability.UPDATE_NOT_AVAILABLE,
                42,
                41));
        assertFalse(AppUpdateChecker.shouldPrompt(
                UpdateAvailability.UNKNOWN,
                42,
                41));
    }
}
