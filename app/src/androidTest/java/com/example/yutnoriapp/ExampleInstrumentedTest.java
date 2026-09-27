package com.example.yutnoriapp;

import android.app.LocaleManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Before
    public void clearSavedGame() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = appContext.getSystemService(LocaleManager.class);
            localeManager.setApplicationLocales(LocaleList.forLanguageTags("en-US"));
        }
        appContext.getSharedPreferences("yutnori_state", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = appContext.getSystemService(LocaleManager.class);
            localeManager.setApplicationLocales(LocaleList.forLanguageTags("en-US"));
        }
        assertEquals("com.das312.yutnori", appContext.getPackageName());
    }

    @Test
    public void firstLaunchRequiresTeamSelection() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.setup_panel).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_team_2).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_team_3).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_team_4).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_setup_help).getVisibility());
                assertEquals(
                        activity.getString(
                                R.string.app_version_update_format,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE),
                        ((TextView) activity.findViewById(R.id.btn_setup_version)).getText().toString());
            });
        }
    }

    @Test
    public void cutoutAndGestureInsetsProtectEveryEdge() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.root_layout);
                WindowInsetsCompat emptyInsets = new WindowInsetsCompat.Builder()
                        .setInsets(
                                WindowInsetsCompat.Type.displayCutout(),
                                Insets.of(0, 0, 0, 0))
                        .setInsets(
                                WindowInsetsCompat.Type.mandatorySystemGestures(),
                                Insets.of(0, 0, 0, 0))
                        .build();
                ViewCompat.dispatchApplyWindowInsets(root, emptyInsets);
                int baseLeft = root.getPaddingLeft();
                int baseTop = root.getPaddingTop();
                int baseRight = root.getPaddingRight();
                int baseBottom = root.getPaddingBottom();

                WindowInsetsCompat protectedInsets = new WindowInsetsCompat.Builder()
                        .setInsets(
                                WindowInsetsCompat.Type.displayCutout(),
                                Insets.of(37, 23, 41, 19))
                        .setInsets(
                                WindowInsetsCompat.Type.mandatorySystemGestures(),
                                Insets.of(5, 7, 11, 29))
                        .build();
                ViewCompat.dispatchApplyWindowInsets(root, protectedInsets);

                assertEquals(baseLeft + 37, root.getPaddingLeft());
                assertEquals(baseTop + 23, root.getPaddingTop());
                assertEquals(baseRight + 41, root.getPaddingRight());
                assertEquals(baseBottom + 29, root.getPaddingBottom());
            });
        }
    }

    @Test
    public void selectingTwoTeamsBuildsOnlyTwoTeamSummaries() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> {
                ViewGroup rows = activity.findViewById(R.id.layout_finished_summary);
                int teams = 0;
                for (int i = 0; i < rows.getChildCount(); i++) teams += ((ViewGroup) rows.getChildAt(i)).getChildCount();
                assertEquals(2, teams);
            });
        }
    }

    @Test
    public void dayOldSavedGameShowsContinueOrNewGameChoice() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
        }

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        appContext.getSharedPreferences("yutnori_state", Context.MODE_PRIVATE)
                .edit()
                .putLong(
                        "game_saved_at",
                        System.currentTimeMillis() - GameStateStore.SAVED_GAME_CONFIRM_AFTER_MILLIS - 1L)
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> assertTrue(activity.isSavedGameChoiceVisible()));
        }
    }

    @Test
    public void unresolvedOldGameKeepsItsOriginalSaveTimeAndAsksAgain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
        }

        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long originalSavedAt = System.currentTimeMillis()
                - GameStateStore.SAVED_GAME_CONFIRM_AFTER_MILLIS
                - 10_000L;
        appContext.getSharedPreferences("yutnori_state", Context.MODE_PRIVATE)
                .edit()
                .putLong("game_saved_at", originalSavedAt)
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> assertTrue(activity.isSavedGameChoiceVisible()));
        }

        long savedAfterDismisslessClose = appContext
                .getSharedPreferences("yutnori_state", Context.MODE_PRIVATE)
                .getLong("game_saved_at", -1L);
        assertEquals(originalSavedAt, savedAfterDismisslessClose);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> assertTrue(activity.isSavedGameChoiceVisible()));
        }
    }

    @Test
    public void moveFlowRequiresResultThenPieceThenDestination() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);

            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            scenario.onActivity(activity -> assertEquals(
                    activity.getString(R.string.select_result_first),
                    ((TextView) activity.findViewById(R.id.text_status)).getText().toString()));

            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(350L);
            scenario.onActivity(activity -> {
                assertTrue(((TextView) activity.findViewById(R.id.text_status))
                        .getText()
                        .toString()
                        .equals(activity.getString(R.string.tap_destination)));
                assertTrue(hasDestinationPreview(activity));
                firstWaitingSpot(activity).performClick();
                assertEquals(1, ((ViewGroup) activity.findViewById(R.id.layout_results)).getChildCount());
            });
        }
    }

    @Test
    public void backDoCannotPreviewOrConsumeWaitingPiece() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);

            scenario.onActivity(activity -> activity.findViewById(R.id.btn_bdo).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(150L);

            scenario.onActivity(activity -> {
                assertTrue(((TextView) activity.findViewById(R.id.text_status))
                        .getText()
                        .toString()
                        .contains(activity.getString(R.string.yut_backdo_steps).split("\\s")[0]));
                assertFalse(hasDestinationPreview(activity));
                assertEquals(1, ((ViewGroup) activity.findViewById(R.id.layout_results)).getChildCount());
                assertEquals(1f, firstWaitingSpot(activity).getScaleX(), 0.01f);
            });
        }
    }

    @Test
    public void backgroundingDoesNotConsumeTurnTime() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            int before = readTimerSeconds(scenario);

            scenario.moveToState(Lifecycle.State.STARTED);
            SystemClock.sleep(2_200L);
            scenario.moveToState(Lifecycle.State.RESUMED);
            SystemClock.sleep(150L);

            int after = readTimerSeconds(scenario);
            assertTrue("Background time must not be charged", before - after <= 1);
        }
    }

    @Test
    public void waitingPieceHasLargeTouchTargetAndGuidanceAfterResult() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);

            scenario.onActivity(activity -> {
                View waitingSpot = firstWaitingSpot(activity);
                int minimumTouchSize = Math.round(48f * activity.getResources().getDisplayMetrics().density);
                assertTrue(waitingSpot.getWidth() >= minimumTouchSize);
                assertTrue(waitingSpot.getHeight() >= minimumTouchSize);
                activity.findViewById(R.id.btn_do).performClick();
            });
            SystemClock.sleep(300L);

            scenario.onActivity(activity -> {
                assertTrue(hasPieceGuidance(activity.findViewById(R.id.root_layout)));
                assertFalse(activity.findViewById(R.id.btn_bdo).isEnabled());
                assertFalse(activity.findViewById(R.id.btn_do).isEnabled());
                assertFalse(activity.findViewById(R.id.btn_gae).isEnabled());
                assertFalse(activity.findViewById(R.id.btn_geol).isEnabled());
                assertTrue(activity.findViewById(R.id.btn_yut).isEnabled());
                assertTrue(activity.findViewById(R.id.btn_mo).isEnabled());
            });
        }
    }

    @Test
    public void destinationAndMovedPieceShareTheExactBoardNodeCenter() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(250L);

            scenario.onActivity(activity -> {
                ViewGroup board = activity.findViewById(R.id.board_overlay);
                float[] expected = BoardGeometry.centerInContent(
                        16,
                        0f,
                        0f,
                        board.getWidth(),
                        board.getHeight());
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                assertEquals(expected[0], destination.getX() + (destination.getWidth() / 2f), 1f);
                assertEquals(expected[1], destination.getY() + (destination.getHeight() / 2f), 1f);
                destination.performClick();
            });
            SystemClock.sleep(1_100L);

            scenario.onActivity(activity -> {
                ViewGroup board = activity.findViewById(R.id.board_overlay);
                float[] expected = BoardGeometry.centerInContent(
                        16,
                        0f,
                        0f,
                        board.getWidth(),
                        board.getHeight());
                PieceStackView movedPiece = null;
                for (int index = 0; index < board.getChildCount(); index++) {
                    if (board.getChildAt(index) instanceof PieceStackView) {
                        movedPiece = (PieceStackView) board.getChildAt(index);
                        break;
                    }
                }
                assertNotNull(movedPiece);
                assertEquals(expected[0], movedPiece.getX() + (movedPiece.getWidth() / 2f), 1f);
                assertEquals(expected[1], movedPiece.getY() + (movedPiece.getHeight() / 2f), 1f);
            });
        }
    }

    @Test
    public void destinationRecentersAfterASecondBoardLayoutPass() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(300L);

            scenario.onActivity(activity -> {
                ViewGroup board = activity.findViewById(R.id.board_container);
                int extra = Math.round(9f * activity.getResources().getDisplayMetrics().density);
                board.setPadding(
                        board.getPaddingLeft() + extra,
                        board.getPaddingTop(),
                        board.getPaddingRight() + extra,
                        board.getPaddingBottom());
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                ViewGroup overlay = activity.findViewById(R.id.board_overlay);
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                float[] expected = BoardGeometry.centerInContent(
                        16,
                        0f,
                        0f,
                        overlay.getWidth(),
                        overlay.getHeight());
                assertEquals(expected[0], destination.getX() + (destination.getWidth() / 2f), 1f);
                assertEquals(expected[1], destination.getY() + (destination.getHeight() / 2f), 1f);
            });
        }
    }

    @Test
    public void destinationAndPieceStayCenteredAcrossRotation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> assertDestinationCentered(activity, 16));

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> {
                assertEquals(
                        Configuration.ORIENTATION_LANDSCAPE,
                        activity.getResources().getConfiguration().orientation);
                assertDestinationCentered(activity, 16);
            });

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> {
                assertEquals(
                        Configuration.ORIENTATION_PORTRAIT,
                        activity.getResources().getConfiguration().orientation);
                assertDestinationCentered(activity, 16);
            });

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> {
                assertDestinationCentered(activity, 16);
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                destination.performClick();
            });
            SystemClock.sleep(1_100L);

            scenario.onActivity(activity -> assertPieceCentered(activity, 16));

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> assertPieceCentered(activity, 16));

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> assertPieceCentered(activity, 16));

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            SystemClock.sleep(800L);
            scenario.onActivity(activity -> assertPieceCentered(activity, 16));
        }
    }

    @Test
    public void coordinatesTrackRenderedBoardThroughPanelsAndMidMoveRotation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                assertDestinationOnRenderedBoard(activity, 16);
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                assertDestinationOnRenderedBoard(activity, 16);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                assertDestinationOnRenderedBoard(activity, 16);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                assertDestinationOnRenderedBoard(activity, 16);
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(900L);

            scenario.onActivity(activity -> {
                assertEquals(
                        Configuration.ORIENTATION_LANDSCAPE,
                        activity.getResources().getConfiguration().orientation);
                assertDestinationOnRenderedBoard(activity, 16);
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                assertDestinationOnRenderedBoard(activity, 16);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                assertViewCenteredOnRenderedBoard(activity, destination, 16);
                destination.performClick();
            });
            SystemClock.sleep(100L);

            scenario.onActivity(activity -> activity.setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            SystemClock.sleep(900L);

            scenario.onActivity(activity -> {
                assertEquals(
                        Configuration.ORIENTATION_PORTRAIT,
                        activity.getResources().getConfiguration().orientation);
                assertPieceOnRenderedBoard(activity, 16);
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(400L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                assertPieceOnRenderedBoard(activity, 16);
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(900L);

            scenario.onActivity(activity -> {
                assertEquals(
                        Configuration.ORIENTATION_LANDSCAPE,
                        activity.getResources().getConfiguration().orientation);
                assertPanelState(activity, false);
                assertPieceOnRenderedBoard(activity, 16);
            });
        }
    }

    @Test
    public void destinationAndPieceStayCenteredAcrossRepeatedRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(350L);

            for (int pass = 0; pass < 3; pass++) {
                scenario.onActivity(activity -> assertDestinationCentered(activity, 16));
                scenario.recreate();
                SystemClock.sleep(500L);
            }

            scenario.onActivity(activity -> {
                assertDestinationCentered(activity, 16);
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                destination.performClick();
            });
            SystemClock.sleep(1_100L);

            for (int pass = 0; pass < 3; pass++) {
                scenario.onActivity(activity -> assertPieceCentered(activity, 16));
                scenario.recreate();
                SystemClock.sleep(500L);
            }
            scenario.onActivity(activity -> assertPieceCentered(activity, 16));
        }
    }
    @Test
    public void timedModeAutomaticallyEndsTurnAtZeroBeforeInput() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);

            scenario.onActivity(activity -> {
                setRemainingTurnMillis(activity, 0L);
                activity.findViewById(R.id.btn_do).performClick();
            });
            SystemClock.sleep(300L);

            scenario.onActivity(activity -> {
                assertEquals(
                        activity.getString(R.string.timer_turn_ended, "Team 2"),
                        ((TextView) activity.findViewById(R.id.text_status)).getText().toString());
                assertEquals(0, ((ViewGroup) activity.findViewById(R.id.layout_results)).getChildCount());
                assertTrue(activity.findViewById(R.id.btn_do).isEnabled());
            });
        }
    }

    @Test
    public void activeGameKeepsScreenOnButManualPauseDoesNot() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> assertFalse(
                    activity.getWindow().getDecorView().getKeepScreenOn()));

            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> assertTrue(
                    activity.getWindow().getDecorView().getKeepScreenOn()));

            scenario.onActivity(activity -> activity.findViewById(R.id.btn_time_stop).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> assertFalse(
                    activity.getWindow().getDecorView().getKeepScreenOn()));
        }
    }

    @Test
    public void pendingResultsRequireConfirmationBeforeManualTurnEnd() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_end_turn).performClick());
            SystemClock.sleep(150L);

            scenario.onActivity(activity -> assertEquals(
                    1,
                    ((ViewGroup) activity.findViewById(R.id.layout_results)).getChildCount()));
        }
    }

    @Test
    public void lastMoveCanBeUndoneAfterActivityRestart() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(400L);
            scenario.onActivity(activity -> {
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                destination.performClick();
            });
            SystemClock.sleep(900L);
            scenario.onActivity(activity -> assertPieceCentered(activity, 16));
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(350L);
            scenario.onActivity(activity -> {
                TextView undo = activity.findViewById(R.id.btn_undo_roll);
                assertEquals(activity.getString(R.string.undo_move), undo.getText().toString());
                assertTrue(undo.isEnabled());
                undo.performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertEquals(0, countBoardPieces(activity));
                assertEquals(1, ((ViewGroup) activity.findViewById(R.id.layout_results)).getChildCount());
                assertEquals(
                        activity.getString(R.string.undo_last),
                        ((TextView) activity.findViewById(R.id.btn_undo_roll)).getText().toString());
                assertNotNull(findDestinationPreview(activity));
            });
        }
    }

    private void setRemainingTurnMillis(MainActivity activity, long value) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("remainingTurnMillis");
            field.setAccessible(true);
            field.setLong(activity, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private boolean hasPieceGuidance(View view) {
        Object tag = view.getTag();
        if (tag != null && tag.toString().startsWith("piece_guide_")) {
            return true;
        }
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            if (hasPieceGuidance(group.getChildAt(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDestinationPreview(MainActivity activity) {
        return findDestinationPreview(activity) != null;
    }

    private View findDestinationPreview(MainActivity activity) {
        ViewGroup boardOverlay = activity.findViewById(R.id.board_overlay);
        for (int index = 0; index < boardOverlay.getChildCount(); index++) {
            View child = boardOverlay.getChildAt(index);
            CharSequence description = child.getContentDescription();
            if (description != null
                    && description.toString().equals(activity.getString(R.string.destination_description))) {
                return child;
            }
        }
        return null;
    }

    private void assertDestinationCentered(MainActivity activity, int node) {
        View destination = findDestinationPreview(activity);
        assertNotNull(destination);
        assertViewCenteredAtNode(activity, destination, node);
    }

    private void assertPieceCentered(MainActivity activity, int node) {
        ViewGroup overlay = activity.findViewById(R.id.board_overlay);
        PieceStackView movedPiece = null;
        for (int index = 0; index < overlay.getChildCount(); index++) {
            if (overlay.getChildAt(index) instanceof PieceStackView) {
                movedPiece = (PieceStackView) overlay.getChildAt(index);
                break;
            }
        }
        assertNotNull(movedPiece);
        assertViewCenteredAtNode(activity, movedPiece, node);
    }

    private void assertDestinationOnRenderedBoard(MainActivity activity, int node) {
        View destination = findDestinationPreview(activity);
        assertNotNull(destination);
        assertViewCenteredOnRenderedBoard(activity, destination, node);
    }

    private void assertPieceOnRenderedBoard(MainActivity activity, int node) {
        ViewGroup overlay = activity.findViewById(R.id.board_overlay);
        PieceStackView movedPiece = null;
        for (int index = 0; index < overlay.getChildCount(); index++) {
            if (overlay.getChildAt(index) instanceof PieceStackView) {
                movedPiece = (PieceStackView) overlay.getChildAt(index);
                break;
            }
        }
        assertNotNull(movedPiece);
        assertViewCenteredOnRenderedBoard(activity, movedPiece, node);
    }

    private void assertViewCenteredOnRenderedBoard(MainActivity activity, View view, int node) {
        View boardArt = activity.findViewById(R.id.board_art);
        View overlay = activity.findViewById(R.id.board_overlay);
        int[] boardLocation = new int[2];
        int[] overlayLocation = new int[2];
        int[] viewLocation = new int[2];
        boardArt.getLocationOnScreen(boardLocation);
        overlay.getLocationOnScreen(overlayLocation);
        view.getLocationOnScreen(viewLocation);

        assertEquals(boardLocation[0], overlayLocation[0], 1f);
        assertEquals(boardLocation[1], overlayLocation[1], 1f);
        assertEquals(boardArt.getWidth(), overlay.getWidth());
        assertEquals(boardArt.getHeight(), overlay.getHeight());

        float boardSize = Math.min(boardArt.getWidth(), boardArt.getHeight());
        float expectedX = boardLocation[0] + BoardGeometry.POINTS[node][0] * boardSize;
        float expectedY = boardLocation[1] + BoardGeometry.POINTS[node][1] * boardSize;
        // Screen origin includes pulse scaling; use the transformed center as well.
        float actualX = viewLocation[0] + (view.getWidth() * view.getScaleX() / 2f);
        float actualY = viewLocation[1] + (view.getHeight() * view.getScaleY() / 2f);
        assertEquals(expectedX, actualX, 2f);
        assertEquals(expectedY, actualY, 2f);
    }

    private int countBoardPieces(MainActivity activity) {
        ViewGroup overlay = activity.findViewById(R.id.board_overlay);
        int count = 0;
        for (int index = 0; index < overlay.getChildCount(); index++) {
            if (overlay.getChildAt(index) instanceof PieceStackView) {
                count++;
            }
        }
        return count;
    }

    private void assertViewCenteredAtNode(MainActivity activity, View view, int node) {
        ViewGroup overlay = activity.findViewById(R.id.board_overlay);
        float[] expected = BoardGeometry.centerInContent(
                node,
                0f,
                0f,
                overlay.getWidth(),
                overlay.getHeight());
        assertEquals(expected[0], view.getX() + (view.getWidth() / 2f), 1f);
        assertEquals(expected[1], view.getY() + (view.getHeight() / 2f), 1f);
    }
    private int readTimerSeconds(ActivityScenario<MainActivity> scenario) {
        AtomicInteger seconds = new AtomicInteger();
        scenario.onActivity(activity -> {
            String[] parts = ((TextView) activity.findViewById(R.id.text_timer))
                    .getText()
                    .toString()
                    .split(":");
            seconds.set(Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]));
        });
        return seconds.get();
    }

    private View firstWaitingSpot(MainActivity activity) {
        ViewGroup waitingArea = activity.findViewById(R.id.waiting_area);
        ViewGroup row = (ViewGroup) waitingArea.getChildAt(0);
        ViewGroup spots = (ViewGroup) row.getChildAt(1);
        return spots.getChildAt(0);
    }


    @Test
    public void localizedResourcesUseKoreanAndEnglishFallback() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager localeManager = appContext.getSystemService(LocaleManager.class);
            localeManager.setApplicationLocales(LocaleList.forLanguageTags("en-US"));
        }

        Configuration englishConfig = new Configuration(appContext.getResources().getConfiguration());
        englishConfig.setLocale(Locale.ENGLISH);
        Context english = appContext.createConfigurationContext(englishConfig);
        assertEquals("Yutnori", english.getString(R.string.app_name));
        assertEquals("2 teams", english.getString(R.string.start_two_teams));

        Configuration koreanConfig = new Configuration(appContext.getResources().getConfiguration());
        koreanConfig.setLocale(Locale.KOREAN);
        Context korean = appContext.createConfigurationContext(koreanConfig);
        assertEquals("윷놀이", korean.getString(R.string.app_name));
        assertEquals("2팀으로 시작", korean.getString(R.string.start_two_teams));
    }
    @Test
    public void englishPrimaryControlsFitWithoutEllipsis() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> {
                assertTextFits(activity.findViewById(R.id.btn_team_2));
                assertTextFits(activity.findViewById(R.id.btn_team_3));
                assertTextFits(activity.findViewById(R.id.btn_team_4));
                TeamAppearanceInstrumentedTest.startRecommendedGame(activity);
            });
            SystemClock.sleep(300L);

            scenario.onActivity(activity -> {
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(300L);
            scenario.onActivity(activity -> assertTextFits(activity.findViewById(R.id.text_status)));

            scenario.onActivity(activity -> activity.findViewById(R.id.btn_toggle_controls).performClick());
            SystemClock.sleep(300L);
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btn_time_stop).performClick();
                int[] textIds = {
                        R.id.text_timer,
                        R.id.btn_time_stop,
                        R.id.btn_end_turn,
                        R.id.btn_undo_roll,
                        R.id.btn_bdo,
                        R.id.btn_do,
                        R.id.btn_gae,
                        R.id.btn_geol,
                        R.id.btn_yut,
                        R.id.btn_mo
                };
                for (int id : textIds) {
                    assertTextFits(activity.findViewById(id));
                }
            });
        }
    }
    @Test
    public void foldingInputReturnsSpaceWithoutHidingTeamProgress() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(350L);

            AtomicInteger boardSideWithControls = new AtomicInteger();
            scenario.onActivity(activity -> {
                View board = activity.findViewById(R.id.board_container);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.top_panel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_info).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
                assertEquals(board.getWidth(), board.getHeight());
                boardSideWithControls.set(board.getWidth());
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            AtomicInteger boardSideWithPanelsClosed = new AtomicInteger();
            scenario.onActivity(activity -> {
                View board = activity.findViewById(R.id.board_container);
                assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.top_panel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_info).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
                assertEquals(board.getWidth(), board.getHeight());
                assertTrue(board.getWidth() >= boardSideWithControls.get());
                boardSideWithPanelsClosed.set(board.getWidth());
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                View board = activity.findViewById(R.id.board_container);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.top_panel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_info).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
                assertEquals(board.getWidth(), board.getHeight());
                assertTrue(board.getWidth() <= boardSideWithPanelsClosed.get());
            });
        }
    }
    @Test
    public void edgePanelTabsRemainAvailableThroughMoveFlow() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(250L);
            scenario.onActivity(activity -> activity.findViewById(R.id.btn_do).performClick());
            SystemClock.sleep(150L);
            scenario.onActivity(activity -> firstWaitingSpot(activity).performClick());
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                assertDestinationCentered(activity, 16);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                View destination = findDestinationPreview(activity);
                assertNotNull(destination);
                destination.performClick();
            });
            SystemClock.sleep(1_100L);

            scenario.onActivity(activity -> assertPanelState(activity, false));
        }
    }
    @Test
    public void inputTabTogglesWhileInformationStaysVisible() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> TeamAppearanceInstrumentedTest.startRecommendedGame(activity));
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                if (activity.findViewById(R.id.control_panel).getVisibility() == View.VISIBLE)
                    activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, false);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> {
                assertPanelState(activity, true);
                activity.findViewById(R.id.btn_toggle_controls).performClick();
            });
            SystemClock.sleep(350L);

            scenario.onActivity(activity -> assertPanelState(activity, false));
        }
    }

    private void assertPanelState(
            MainActivity activity,
            boolean expectedControlsOpen) {
        if (activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            assertEquals(View.VISIBLE, activity.findViewById(R.id.top_panel).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.landscape_results).getVisibility());
            assertSame(activity.findViewById(R.id.top_panel), activity.findViewById(R.id.turn_tools).getParent());
            return;
        }
        assertEquals(
                View.VISIBLE,
                activity.findViewById(R.id.top_panel).getVisibility());
        assertEquals(
                expectedControlsOpen ? View.VISIBLE : View.GONE,
                activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_info).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
        assertEquals(
                activity.getString(expectedControlsOpen ? R.string.drawer_close : R.string.input),
                ((TextView) activity.findViewById(R.id.btn_toggle_controls)).getText().toString());
    }
    private void assertTextFits(TextView view) {
        assertNotNull(view);
        assertNotNull(view.getLayout());
        int availableWidth = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        int availableHeight = view.getHeight() - view.getPaddingTop() - view.getPaddingBottom();
        assertTrue("Text width overflow: " + view.getText(), view.getLayout().getWidth() <= availableWidth + 1);
        assertTrue("Text height overflow: " + view.getText(), view.getLayout().getHeight() <= availableHeight + 1);
        for (int line = 0; line < view.getLayout().getLineCount(); line++) {
            assertEquals("Ellipsized text: " + view.getText(), 0, view.getLayout().getEllipsisCount(line));
        }
    }
}
