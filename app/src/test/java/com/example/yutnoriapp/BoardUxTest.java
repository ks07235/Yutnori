package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowChoreographer;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ko-rKR-w360dp-h740dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BoardUxTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void start() {
        RuntimeEnvironment.getApplication().getSharedPreferences("yutnori_state", Context.MODE_PRIVATE)
                .edit().clear().putBoolean("sound_enabled", false).putBoolean("vibration_enabled", false).commit();
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        activity.findViewById(R.id.btn_team_4).performClick();
        ((AlertDialog) field(activity, "teamAppearanceDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle();
    }

    @After public void close() { if (controller != null) controller.pause().stop().destroy(); }

    @Test public void informationAndAllFourTeamsRemainVisibleWhenInputToggles() throws Exception {
        assertInfoVisible();
        activity.findViewById(R.id.btn_toggle_controls).performClick();
        settle();
        assertInfoVisible();
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
        activity.findViewById(R.id.btn_toggle_controls).performClick();
        settle();
        assertInfoVisible();
        showProgressExample();
        screenshot("portrait-progress");
    }

    @Test public void finishTargetIsSeparateAndTappingItFinishesTheSelectedStack() throws Exception {
        YutGameEngine game = game();
        game.getPiece(0, 0).position = 15;
        game.getPiece(0, 1).position = 15;
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle();
        View finish = activity.findViewById(R.id.finish_destination);
        assertEquals(View.VISIBLE, finish.getVisibility());
        assertFalse(Rect.intersects(bounds(finish), bounds(activity.findViewById(R.id.board_overlay))));
        assertTrue(finish.getWidth() >= 48 && finish.getHeight() >= 48);
        assertInfoVisible();
        screenshot("portrait-finish");
        Rect rect = bounds(finish);
        View root = activity.findViewById(R.id.root_layout);
        Rect rootRect = bounds(root);
        float x = rect.exactCenterX() - rootRect.left, y = rect.exactCenterY() - rootRect.top;
        MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0);
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle(); up.recycle();
        settle();
        assertTrue(game.getPiece(0, 0).isFinished);
        assertTrue(game.getPiece(0, 1).isFinished);
        assertEquals(View.GONE, finish.getVisibility());
        PieceStackView[][] icons = (PieceStackView[][]) field(activity, "teamProgressPieces");
        assertEquals(true, field(icons[0][0], "finishedIndicator"));
        assertEquals(true, field(icons[0][1], "finishedIndicator"));
    }

    @Test public void merelyLandingOnTheArrivalNodeDoesNotOfferFinish() {
        game().getPiece(0, 0).position = 14;
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
        assertFalse(game().getPiece(0, 0).isFinished);
        assertInfoVisible();
    }

    @Test public void backDoAndClearedSelectionHideFinishTarget() {
        game().getPiece(0, 0).position = 15;
        call("handleYutInput", -1);
        call("selectPiece", 0, 0);
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
        call("clearSelectedPiece");
        call("updateMovePreviews");
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
    }

    @Test @Config(qualifiers = "ko-rKR-w640dp-h360dp-land-mdpi")
    public void narrowLandscapeKeepsInformationAndFinishTargetInsideScreen() throws Exception {
        assertInfoVisible();
        View[][] spots = (View[][]) field(activity, "waitSpots");
        assertTrue(bounds(activity.findViewById(R.id.waiting_area)).contains(bounds(spots[0][3])));
        showProgressExample();
        screenshot("landscape-progress");
        game().getPiece(0, 2).position = 15;
        call("handleYutInput", 1);
        call("selectPiece", 0, 2);
        settle();
        assertInfoVisible();
        View finish = activity.findViewById(R.id.finish_destination);
        assertEquals(View.VISIBLE, finish.getVisibility());
        assertTrue(bounds(activity.findViewById(R.id.root_layout)).contains(bounds(finish)));
        assertFalse(Rect.intersects(bounds(finish), bounds(activity.findViewById(R.id.board_overlay))));
        screenshot("landscape-finish");
    }

    @Test public void clearingAValidFinishSelectionRemovesTheTemporaryTile() {
        game().getPiece(0, 0).position = 15;
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.finish_destination).getVisibility());
        call("clearSelectedPiece");
        call("updateMovePreviews");
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
        assertFalse(game().getPiece(0, 0).isFinished);
    }

    @Test public void progressAndPinnedInformationSurviveRecreation() {
        showProgressExample();
        controller.recreate();
        activity = controller.get();
        settle();
        assertInfoVisible();
        PieceStackView[][] icons = (PieceStackView[][]) field(activity, "teamProgressPieces");
        assertEquals(true, field(icons[3][2], "finishedIndicator"));
        assertEquals(false, field(icons[3][3], "finishedIndicator"));
    }

    private void assertInfoVisible() {
        assertEquals(View.VISIBLE, activity.findViewById(R.id.top_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_info).getVisibility());
        ViewGroup[] rows = (ViewGroup[]) field(activity, "teamProgressRows");
        Rect screen = bounds(activity.findViewById(R.id.root_layout));
        Rect panel = bounds(activity.findViewById(R.id.top_panel));
        for (int team = 0; team < 4; team++) {
            assertNotNull(rows[team]);
            assertEquals(5, rows[team].getChildCount());
            assertTrue("Team " + team + " must be on screen: " + bounds(rows[team]), screen.contains(bounds(rows[team])));
            assertTrue("Team " + team + " must not be clipped by the panel", panel.contains(bounds(rows[team])));
        }
    }

    @Test public void panelStateChangesOnlyWhenUserTogglesIt() {
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
        call("commitSelectedMove");
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
        activity.findViewById(R.id.btn_toggle_controls).performClick();
        settle();
        call("handleYutInput", 1);
        call("selectPiece", game().getCurrentTeam(), 0);
        settle();
        call("commitSelectedMove");
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
        controller.recreate();
        activity = controller.get();
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
    }

    @Test public void portraitKeepsRegularControlsForBothTimeModes() throws Exception {
        settings(0L);
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.text_timer).getVisibility());
        assertEquals(activity.getString(R.string.unlimited), ((android.widget.TextView) activity.findViewById(R.id.text_timer)).getText());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_time_stop).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.turn_tools).getVisibility());
        assertSame(activity.findViewById(R.id.turn_tools), activity.findViewById(R.id.btn_end_turn).getParent());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
        screenshot("unlimited-controls");
        activity.findViewById(R.id.btn_end_turn).performClick();
        settle();
        assertEquals(1, game().getCurrentTeam());
        settings(180000L);
        settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.text_timer).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_time_stop).getVisibility());
        assertSame(activity.findViewById(R.id.turn_tools), activity.findViewById(R.id.btn_end_turn).getParent());
    }

    @Test public void finishAnimationTravelsViaArrivalNodeIntoTheGoalTile() throws Exception {
        game().getPiece(0, 0).position = 14;
        call("handleYutInput", 2);
        call("selectPiece", 0, 0);
        settle();
        ShadowChoreographer.setPaused(true);
        call("commitSelectedMove");
        advanceFrames(120);
        assertEquals(true, field(activity, "isAnimatingMove"));
        assertNull(field(activity, "finishAnimationView"));
        advanceFrames(220);
        View ghost = (View) field(activity, "finishAnimationView");
        assertNotNull("The piece should leave the arrival node towards the goal", ghost);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.finish_destination).getVisibility());
        float initialY = ghost.getY();
        advanceFrames(180);
        assertTrue("The piece must visibly travel down into the goal", ghost.getY() > initialY);
        screenshot("finish-animation");
        advanceFrames(1000);
        settle();
        assertNull(field(activity, "finishAnimationView"));
        assertEquals(false, field(activity, "isAnimatingMove"));
        assertTrue(game().getPiece(0, 0).isFinished);
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
    }

    @Test public void backgroundAndRelaunchDuringFinishPreserveCompletionAndRemoveGhost() {
        game().getPiece(0, 0).position = 15;
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle();
        ShadowChoreographer.setPaused(true);
        call("commitSelectedMove");
        advanceFrames(200);
        assertNotNull(field(activity, "finishAnimationView"));
        controller.pause();
        assertNull(field(activity, "finishAnimationView"));
        controller.stop().destroy();
        ShadowChoreographer.setPaused(false);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        settle();
        assertTrue(game().getPiece(0, 0).isFinished);
        assertNull(field(activity, "finishAnimationView"));
        assertEquals(false, field(activity, "isAnimatingMove"));
        assertEquals(View.GONE, activity.findViewById(R.id.finish_destination).getVisibility());
    }

    private void settings(long duration) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("applySettings", long.class, boolean.class, boolean.class);
            method.setAccessible(true);
            method.invoke(activity, duration, false, false);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test @Config(qualifiers = "ko-rKR-w640dp-h360dp-land-mdpi")
    public void unlimitedLandscapeHasFixedRollButtonsAndAllFourWaitingPieces() throws Exception {
        settings(0L);
        settle();
        assertInfoVisible();
        Rect screen = bounds(activity.findViewById(R.id.root_layout));
        for (int id : new int[]{R.id.btn_do, R.id.btn_gae, R.id.btn_geol, R.id.btn_yut, R.id.btn_mo, R.id.btn_bdo}) {
            View button = activity.findViewById(id);
            assertTrue(screen.contains(bounds(button)));
            assertTrue(button.getHeight() >= 48);
            assertTrue(bounds(button).left >= bounds(activity.findViewById(R.id.board_container)).right);
        }
        View[][] spots = (View[][]) field(activity, "waitSpots");
        assertTrue(bounds(activity.findViewById(R.id.waiting_area)).contains(bounds(spots[0][3])));
        screenshot("unlimited-landscape");
        assertTrue(bounds(spots[0][3]).top > bounds(spots[0][0]).bottom);
        assertEquals(bounds(spots[0][0]).left, bounds(spots[0][3]).left);
        assertTrue(bounds(spots[0][0]).right <= bounds(activity.findViewById(R.id.unlimited_rolls)).left);
        assertTrue("Moving the tray should free board space", activity.findViewById(R.id.board_container).getWidth() > 244);
        showProgressExample();
        screenshot("unlimited-landscape-progress");
        // Restore active pieces before exercising normal play.
        for (int team = 0; team < 4; team++) for (int id = 0; id < 4; id++) {
            game().getPiece(team, id).isFinished = false;
            game().getPiece(team, id).position = BoardPath.START_NODE;
        }
        call("updateFinishedSummary");
        activity.findViewById(R.id.btn_do).performClick();
        call("selectPiece", 0, 0);
        settle();
        call("commitSelectedMove");
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
        PieceStackView[][] views = (PieceStackView[][]) field(activity, "pieceViews");
        assertEquals(36, field(views[0][0], "visualDiameterPx"));
        settings(180000L);
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.turn_tools).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.btn_time_stop).getVisibility());
        int[] columns = {R.id.top_panel, R.id.board_container, R.id.landscape_results,
                R.id.unlimited_waiting, R.id.unlimited_rolls};
        for (int i = 1; i < columns.length; i++) {
            assertTrue(bounds(activity.findViewById(columns[i - 1])).right <= bounds(activity.findViewById(columns[i])).left);
        }
        screenshot("timed-landscape");
    }

    private void advanceFrames(int millis) {
        for (int elapsed = 0; elapsed < millis; elapsed += 16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        }
    }

    @Test @Config(qualifiers = "ko-rKR-w640dp-h360dp-land-mdpi")
    public void landscapeResultsScrollWithoutCoveringWaitingPiecesOrRollButtons() throws Exception {
        for (int i = 0; i < 12; i++) call("handleYutInput", i % 2 == 0 ? 4 : 5);
        settle();
        android.widget.LinearLayout results = activity.findViewById(R.id.layout_results);
        android.widget.ScrollView scroll = (android.widget.ScrollView) results.getParent();
        assertEquals(12, results.getChildCount());
        assertEquals(android.widget.LinearLayout.VERTICAL, results.getOrientation());
        assertTrue(scroll.canScrollVertically(1));
        assertTrue(bounds(scroll).right <= bounds(activity.findViewById(R.id.unlimited_waiting)).left);
        scroll.setSmoothScrollingEnabled(false);
        scroll.fullScroll(View.FOCUS_DOWN);
        settle();
        assertTrue(scroll.getScrollY() > 0);
        assertTrue(bounds(scroll).contains(bounds(results.getChildAt(11))));
        results.getChildAt(11).performClick();
        settle();
        assertTrue(game().getMoveChoices().get(11).selectionOrder > 0);
        screenshot("landscape-results-scrolled");
    }

    @Test public void rotatingBothTimeModesPreservesResultsAndRestoresPortraitControls() {
        for (long duration : new long[]{0L, 180000L}) {
            settings(duration);
            call("handleYutInput", 4);
            call("handleYutInput", 5);
            call("onResultClick", game().getMoveChoices().size() - 1);
            int count = game().getPendingResults().size();
            String selection = game().getSelectedSteps().toString();
            rotate("ko-rKR-w640dp-h360dp-land-mdpi");
            assertEquals(count, game().getPendingResults().size());
            assertEquals(selection, game().getSelectedSteps().toString());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.landscape_results).getVisibility());
            assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
            assertSame(activity.findViewById(R.id.results_row), activity.findViewById(R.id.btn_end_turn).getParent());
            rotate("ko-rKR-w360dp-h740dp-port-mdpi");
            assertEquals(count, game().getPendingResults().size());
            assertEquals(selection, game().getSelectedSteps().toString());
            assertEquals(View.GONE, activity.findViewById(R.id.landscape_results).getVisibility());
            assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
            assertSame(activity.findViewById(R.id.turn_tools), activity.findViewById(R.id.btn_end_turn).getParent());
            assertEquals(android.widget.LinearLayout.HORIZONTAL,
                    ((android.widget.LinearLayout) activity.findViewById(R.id.layout_results)).getOrientation());
        }
    }

    private void rotate(String qualifiers) {
        RuntimeEnvironment.setQualifiers(qualifiers);
        controller.configurationChange(new android.content.res.Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration()));
        activity = controller.get();
        settle();
    }

    @Test @Config(qualifiers = "ko-rKR-sw600dp-w960dp-h600dp-land-mdpi")
    public void tabletLandscapeShowsAllFiveColumnsAndWaitingPieces() throws Exception {
        assertInfoVisible();
        int[] columns = {R.id.top_panel, R.id.board_container, R.id.landscape_results,
                R.id.unlimited_waiting, R.id.unlimited_rolls};
        Rect screen = bounds(activity.findViewById(R.id.root_layout));
        for (int i = 0; i < columns.length; i++) {
            Rect column = bounds(activity.findViewById(columns[i]));
            assertTrue(screen.contains(column));
            if (i > 0) assertTrue(bounds(activity.findViewById(columns[i - 1])).right <= column.left);
        }
        View[][] spots = (View[][]) field(activity, "waitSpots");
        assertTrue(bounds(activity.findViewById(R.id.waiting_area)).contains(bounds(spots[0][3])));
        call("handleYutInput", 4);
        call("handleYutInput", 2);
        settle();
        screenshot("tablet-landscape");
    }

    @Test @Config(qualifiers = "ko-rKR-w360dp-h740dp-port-night-mdpi")
    public void settingsRemainLightInSystemDarkMode() throws Exception {
        call("showSettingsDialog");
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        android.util.TypedValue color = new android.util.TypedValue();
        assertTrue(dialog.getContext().getTheme().resolveAttribute(android.R.attr.colorBackground, color, true));
        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(color.data) > 0.8);
        assertTrue(androidx.core.graphics.ColorUtils.calculateLuminance(
                activity.getResources().getColor(R.color.text_primary)) < 0.2);
        View decor = dialog.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(340, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST));
        decor.layout(0, 0, decor.getMeasuredWidth(), decor.getMeasuredHeight());
        screenshot("settings-system-dark", decor);
        dialog.dismiss();
    }

    @Test @Config(qualifiers = "ko-rKR-sw600dp-w600dp-h960dp-port-mdpi")
    public void tabletPortraitKeepsRegularControlsAndHorizontalResults() throws Exception {
        settings(0L);
        call("handleYutInput", 4);
        call("handleYutInput", 1);
        settle();
        assertInfoVisible();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
        assertEquals(android.widget.LinearLayout.HORIZONTAL,
                ((android.widget.LinearLayout) activity.findViewById(R.id.layout_results)).getOrientation());
        screenshot("tablet-portrait");
    }

    @Test public void newGameRequiresThreeSecondsAndExplicitConfirmation() {
        int[] confirmed = {0};
        int[] dismissed = {0};
        YutDialogs.showNewGameConfirmation(activity, () -> confirmed[0]++, () -> dismissed[0]++);
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        assertFalse(button.isEnabled());
        assertEquals(activity.getString(R.string.new_game_confirm_countdown, 3L), button.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertFalse(button.isEnabled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertTrue(button.isEnabled());
        assertEquals(0, confirmed[0]);
        button.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, confirmed[0]);
        assertEquals(1, dismissed[0]);
    }

    @Test public void dismissingNewGameDuringCountdownDoesNotStartGame() {
        int[] confirmed = {0};
        YutDialogs.showNewGameConfirmation(activity, () -> confirmed[0]++, () -> {});
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        assertEquals(0, confirmed[0]);
        assertFalse(dialog.isShowing());
    }

    @Test public void repeatedUndoCrossesRollMoveAndTurnBoundariesAndSurvivesRecreation() {
        settings(0L);
        settle();
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        call("commitSelectedMove");
        settle();
        call("handleYutInput", 2);
        call("selectPiece", 1, 0);
        call("commitSelectedMove");
        settle();
        call("endCurrentTurn");
        settle();
        assertEquals(3, game().getCurrentTeam());
        call("undoLastAction");
        settle();
        assertEquals(2, game().getCurrentTeam());
        controller.recreate(); activity = controller.get(); settle();
        call("undoLastAction"); settle();
        assertEquals(1, game().getCurrentTeam());
        assertEquals(BoardPath.START_NODE, game().getPiece(1, 0).position);
        assertEquals(1, game().getPendingResults().size());
        call("undoLastAction"); settle();
        assertTrue(game().getPendingResults().isEmpty());
        call("undoLastAction"); settle();
        assertEquals(0, game().getCurrentTeam());
        assertEquals(BoardPath.START_NODE, game().getPiece(0, 0).position);
        call("undoLastAction"); settle();
        assertTrue(game().getPendingResults().isEmpty());
        assertFalse(activity.findViewById(R.id.btn_undo_roll).isEnabled());
    }

    @Test public void undoingFinishRestoresPieceAndRemovesFinishedMark() {
        settings(0L);
        game().getPiece(0, 0).position = 15;
        call("handleYutInput", 1);
        call("selectPiece", 0, 0);
        settle(); call("commitSelectedMove"); settle();
        assertTrue(game().getPiece(0, 0).isFinished);
        call("undoLastAction"); settle();
        assertFalse(game().getPiece(0, 0).isFinished);
        assertEquals(15, game().getPiece(0, 0).position);
        PieceStackView[][] icons = (PieceStackView[][]) field(activity, "teamProgressPieces");
        assertEquals(false, field(icons[0][0], "finishedIndicator"));
        call("undoLastAction"); settle();
        assertTrue(game().getPendingResults().isEmpty());
    }

    @Test public void whiteProgressPiecesStayFilledAndShowBlackBorderAndFinishedX() {
        PieceStackView icon = new PieceStackView(activity);
        icon.configureAppearance(5, TeamAppearance.CIRCLE, 1);
        icon.layout(0, 0, 64, 64);
        icon.setFinishedIndicator(false);
        Bitmap before = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(before));
        assertEquals(android.graphics.Color.WHITE, before.getPixel(32, 32));
        boolean blackBorder = false;
        for (int y = 0; y < 20; y++) if (before.getPixel(32, y) == android.graphics.Color.BLACK) blackBorder = true;
        assertTrue(blackBorder);
        icon.setFinishedIndicator(true);
        Bitmap after = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(after));
        assertNotEquals(android.graphics.Color.WHITE, after.getPixel(32, 32));
        assertEquals(android.graphics.Color.WHITE, after.getPixel(32, 20));
        before.recycle(); after.recycle();
    }

    private void showProgressExample() {
        int[] counts = {2, 1, 0, 3};
        for (int team = 0; team < 4; team++) for (int piece = 0; piece < counts[team]; piece++) {
            game().getPiece(team, piece).isFinished = true;
            game().getPiece(team, piece).position = BoardPath.END_NODE;
        }
        call("updateFinishedSummary");
        settle();
        PieceStackView[][] icons = (PieceStackView[][]) field(activity, "teamProgressPieces");
        for (int team = 0; team < 4; team++) for (int piece = 0; piece < 4; piece++) {
            assertEquals(piece < counts[team], field(icons[team][piece], "finishedIndicator"));
        }
    }

    private void settle() {
        for (int i = 0; i < 3; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
            View root = activity.findViewById(R.id.root_layout);
            int width = activity.getResources().getConfiguration().screenWidthDp;
            int height = activity.getResources().getConfiguration().screenHeightDp;
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
    }

    private Rect bounds(View view) {
        int[] xy = new int[2]; view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
    }

    private void screenshot(String name) throws Exception {
        screenshot(name, activity.findViewById(R.id.root_layout));
    }

    @Test @Config(qualifiers = "ko-rKR-w640dp-h360dp-land-mdpi")
    public void landscapeTimerMovesLeftWithoutShrinkingBoardAndButtonsUseColumnOrder() throws Exception {
        assertEquals(View.GONE, activity.findViewById(R.id.text_status).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.text_title).getVisibility());
        assertSame(activity.findViewById(R.id.top_panel), activity.findViewById(R.id.turn_tools).getParent());
        assertTrue(bounds(activity.findViewById(R.id.top_panel)).contains(bounds(activity.findViewById(R.id.text_timer))));
        assertTrue(bounds(activity.findViewById(R.id.top_panel)).contains(bounds(activity.findViewById(R.id.btn_time_stop))));
        assertTrue(activity.findViewById(R.id.board_container).getWidth() >= 248);
        ViewGroup[] progressRows = (ViewGroup[]) field(activity, "teamProgressRows");
        for (int team = 0; team < 4; team++) {
            View row = (View) progressRows[team].getParent();
            assertEquals(34, row.getHeight());
            if (team > 0) {
                assertEquals(6, ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).topMargin);
            }
        }
        View[][] waitingSpots = (View[][]) field(activity, "waitSpots");
        assertEquals(6, ((ViewGroup.MarginLayoutParams) waitingSpots[0][1].getLayoutParams()).topMargin);
        int[] left = {R.id.btn_do, R.id.btn_gae, R.id.btn_geol};
        int[] right = {R.id.btn_yut, R.id.btn_mo, R.id.btn_bdo};
        for (int i = 0; i < 3; i++) {
            assertEquals(bounds(activity.findViewById(left[i])).top, bounds(activity.findViewById(right[i])).top);
            assertTrue(bounds(activity.findViewById(left[i])).right <= bounds(activity.findViewById(right[i])).left);
            if (i > 0) assertTrue(bounds(activity.findViewById(left[i - 1])).bottom <= bounds(activity.findViewById(left[i])).top);
        }
        call("handleYutInput", 4); settle();
        assertExtraThrowHint();
        ViewGroup results = activity.findViewById(R.id.layout_results);
        android.widget.TextView result = (android.widget.TextView) results.getChildAt(0);
        assertEquals(18, result.getAutoSizeMaxTextSize());
        assertTrue(result.getTextSize() >= 14f);
        screenshot("v150-landscape");
    }

    @Test @Config(qualifiers = "ko-rKR-w780dp-h360dp-land-mdpi")
    public void landscapeUsesHeightWhileProtectingCutoutAndGestureControls() throws Exception {
        View root = activity.findViewById(R.id.root_layout);
        androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                        androidx.core.graphics.Insets.of(28, 0, 0, 0))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures(),
                        androidx.core.graphics.Insets.of(0, 24, 0, 24)).build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets);
        settle();
        assertEquals(34, root.getPaddingLeft());
        assertEquals(8, root.getPaddingTop());
        assertEquals(8, root.getPaddingBottom());
        Rect surface = bounds(activity.findViewById(R.id.landscape_control_surface));
        assertTrue(surface.contains(bounds(activity.findViewById(R.id.btn_do))));
        assertTrue(surface.contains(bounds(activity.findViewById(R.id.btn_undo_roll))));
        assertTrue(surface.contains(bounds(activity.findViewById(R.id.unlimited_waiting))));
        assertTrue(bounds(activity.findViewById(R.id.btn_settings)).top >= 24);
        assertTrue(bounds(activity.findViewById(R.id.btn_do)).top >= 24);
        assertTrue(bounds(activity.findViewById(R.id.btn_undo_roll)).bottom <= 336);
        assertTrue(activity.findViewById(R.id.board_container).getWidth() > 310);
        screenshot("v151-landscape-insets");
        // Insets are absolute, not accumulated when Android redispatches them.
        int footerPadding = activity.findViewById(R.id.unlimited_footer).getPaddingBottom();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets); settle();
        assertEquals(footerPadding, activity.findViewById(R.id.unlimited_footer).getPaddingBottom());
        call("showTeamSetup"); settle();
        assertEquals(View.GONE, activity.findViewById(R.id.landscape_control_surface).getVisibility());
    }

    @Test @Config(qualifiers = "ko-rKR-w780dp-h360dp-land-mdpi")
    public void landscapeProtectsRightCutoutAndVisibleNavigationBar() {
        View root = activity.findViewById(R.id.root_layout);
        androidx.core.view.WindowInsetsCompat emptyInsets =
                new androidx.core.view.WindowInsetsCompat.Builder().build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, emptyInsets);
        settle();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        int footerBottom = activity.findViewById(R.id.unlimited_footer).getPaddingBottom();

        androidx.core.view.WindowInsetsCompat protectedInsets =
                new androidx.core.view.WindowInsetsCompat.Builder()
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(),
                                androidx.core.graphics.Insets.of(0, 0, 32, 0))
                        .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(),
                                androidx.core.graphics.Insets.of(0, 0, 0, 28))
                        .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, protectedInsets);
        settle();

        assertEquals(baseRight + 32, root.getPaddingRight());
        assertEquals(baseBottom + 4, root.getPaddingBottom());
        assertTrue(activity.findViewById(R.id.unlimited_footer).getPaddingBottom() > footerBottom);
        assertTrue(bounds(activity.findViewById(R.id.btn_undo_roll)).bottom <= 360 - 28);
        assertTrue(bounds(activity.findViewById(R.id.btn_end_turn)).bottom <= 360 - 28);
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h914dp-port-mdpi", fontScale = 1.7f)
    public void largeFontSetupKeepsEveryPrimaryChoiceUsable() throws Exception {
        call("showTeamSetup");
        settle();
        int[] ids = {
                R.id.btn_team_2,
                R.id.btn_team_3,
                R.id.btn_team_4,
                R.id.btn_setup_help,
                R.id.btn_setup_version
        };
        for (int id : ids) {
            android.widget.TextView button = activity.findViewById(id);
            assertEquals(View.VISIBLE, button.getVisibility());
            assertTrue(button.isClickable());
            assertTrue(button.getHeight() >= 48);
            assertNotNull(button.getLayout());
            for (int line = 0; line < button.getLayout().getLineCount(); line++) {
                assertEquals(0, button.getLayout().getEllipsisCount(line));
            }
        }
        screenshot("v153-large-font-setup");
    }

    @Test public void portraitSafeAreaRemainsUnchangedAfterLandscapeRotation() {
        rotate("ko-rKR-w780dp-h360dp-land-mdpi");
        assertEquals(View.VISIBLE, activity.findViewById(R.id.landscape_control_surface).getVisibility());
        rotate("ko-rKR-w360dp-h740dp-port-mdpi");
        View root = activity.findViewById(R.id.root_layout);
        androidx.core.view.WindowInsetsCompat insets = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures(),
                        androidx.core.graphics.Insets.of(0, 24, 0, 24)).build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root, insets); settle();
        assertEquals(32, root.getPaddingTop());
        assertEquals(32, root.getPaddingBottom());
        assertEquals(View.GONE, activity.findViewById(R.id.landscape_control_surface).getVisibility());
        assertInfoVisible();
    }

    @Test public void activeSettingsCanSwitchBetweenTimedAndUnlimitedWithoutResettingPieces() {
        call("handleYutInput", 2); call("selectPiece", 0, 0); call("commitSelectedMove"); settle();
        int position = game().getPiece(0, 0).position;
        for (int index : new int[]{4, 2}) {
            call("showSettingsDialog");
            AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            com.google.android.material.slider.Slider slider = dialog.getWindow().getDecorView().findViewWithTag("turn_time_selector");
            assertTrue(slider.isEnabled()); slider.setValue(index);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(index == 4 ? 0L : 180000L, field(activity, "turnDurationMillis"));
            assertEquals(position, game().getPiece(0, 0).position);
            assertEquals(index == 4 ? 0L : 180000L, field(activity, "remainingTurnMillis"));
        }
    }

    @Test public void restartRetainsAppearanceRulesAndTimeButClearsBoardAndUndo() {
        settings(120000L);
        game().setCaptureBonusStacks(true);
        int[] colors = ((int[]) field(activity, "teamColors")).clone();
        int[] shapes = ((int[]) field(activity, "teamShapes")).clone();
        call("handleYutInput", 2); call("selectPiece", 0, 0); call("commitSelectedMove"); settle();
        call("requestNewGame");
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); settle();
        assertEquals(4, game().getTeamCount());
        assertEquals(0, game().getCurrentTeam());
        assertEquals(BoardPath.START_NODE, game().getPiece(0, 0).position);
        assertTrue(game().getPendingResults().isEmpty()); assertNull(field(activity, "moveUndoState"));
        assertArrayEquals(colors, (int[]) field(activity, "teamColors"));
        assertArrayEquals(shapes, (int[]) field(activity, "teamShapes"));
        assertEquals(120000L, field(activity, "turnDurationMillis"));
        assertTrue(game().isCaptureBonusStacks());
        assertEquals(View.GONE, activity.findViewById(R.id.setup_panel).getVisibility());
    }

    @Test public void newGameChoiceRemembersAppearanceEvenAfterRelaunchFromSetup() {
        int[] colors = ((int[]) field(activity, "teamColors")).clone();
        int[] shapes = ((int[]) field(activity, "teamShapes")).clone();
        call("requestNewGame");
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); settle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.setup_panel).getVisibility());
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); settle();
        activity.findViewById(R.id.btn_team_4).performClick();
        assertArrayEquals(colors, (int[]) field(activity, "draftTeamColors"));
        assertArrayEquals(shapes, (int[]) field(activity, "draftTeamShapes"));
        ((AlertDialog) field(activity, "teamAppearanceDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick(); settle();
        assertArrayEquals(colors, (int[]) field(activity, "teamColors"));
    }

    private void assertExtraThrowHint() {
        android.widget.TextView hint = (android.widget.TextView) field(activity, "rollHint");
        assertEquals(activity.getString(R.string.extra_rolls, 1), hint.getText().toString());
    }

    @Test public void undoMovementHasVisibleReverseMotionAndQueuesRepeatedRequests() throws Exception {
        settings(0L);
        for (int steps = 1; steps <= 3; steps++) {
            int team = game().getCurrentTeam();
            call("handleYutInput", steps); call("selectPiece", team, 0);
            call("commitSelectedMove"); settle();
        }
        ShadowChoreographer.setPaused(true);
        call("undoLastAction"); call("undoLastAction"); call("undoLastAction");
        assertEquals(true, field(activity, "undoAnimating"));
        assertEquals(2, field(activity, "queuedUndoCount"));
        advanceFrames(32);
        java.util.List<View> ghosts = (java.util.List<View>) field(activity, "undoGhosts");
        assertFalse(ghosts.isEmpty());
        View ghost = ghosts.get(0);
        float x = ghost.getX(), y = ghost.getY();
        advanceFrames(64);
        assertTrue(Math.hypot(ghost.getX() - x, ghost.getY() - y) > 1f);
        screenshot("v150-undo-motion");
        advanceFrames(1600);
        settle();
        assertEquals(false, field(activity, "undoAnimating"));
        assertEquals(0, field(activity, "queuedUndoCount"));
        assertEquals(1, game().getCurrentTeam());
        assertEquals(BoardPath.START_NODE, game().getPiece(1, 0).position);
        assertEquals(BoardPath.START_NODE, game().getPiece(2, 0).position);
        assertEquals(1, game().getPendingResults().size());
    }

    @Test public void reducedMotionSkipsMoveAndUndoTravelButKeepsFinalState() throws Exception {
        java.lang.reflect.Field reduced = MainActivity.class.getDeclaredField("reducedMotion");
        reduced.setAccessible(true); reduced.setBoolean(activity, true);
        settings(0L);
        call("handleYutInput", 1); call("selectPiece", 0, 0); call("commitSelectedMove");
        assertEquals(false, field(activity, "isAnimatingMove"));
        settle();
        assertEquals(16, game().getPiece(0, 0).position);
        call("undoLastAction");
        assertEquals(false, field(activity, "undoAnimating"));
        assertEquals(BoardPath.START_NODE, game().getPiece(0, 0).position);
        settle();
    }

    @Test public void rotationDuringUndoKeepsRestoredStateAndRemovesGhosts() {
        settings(0L); call("handleYutInput", 3); call("selectPiece", 0, 0);
        call("commitSelectedMove"); settle();
        ShadowChoreographer.setPaused(true);
        call("undoLastAction"); advanceFrames(32);
        rotate("ko-rKR-w640dp-h360dp-land-mdpi");
        assertEquals(false, field(activity, "undoAnimating"));
        assertTrue(((java.util.List<?>) field(activity, "undoGhosts")).isEmpty());
        assertEquals(BoardPath.START_NODE, game().getPiece(0, 0).position);
        ShadowChoreographer.setPaused(false);
        controller.pause().stop().destroy();
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get(); settle();
        assertEquals(BoardPath.START_NODE, game().getPiece(0, 0).position);
        assertEquals(1, game().getPendingResults().size());
    }

    @Test public void backupRoundTripIncludesRuleAppearanceSelectionAndUndoPaths() throws Exception {
        settings(0L);
        call("handleYutInput", 4); call("selectPiece", 0, 0); call("commitSelectedMove"); settle();
        GameStateStore store = (GameStateStore) field(activity, "stateStore");
        String backup = store.exportBackup();
        call("endCurrentTurn"); settle();
        store.importBackup(backup);
        GameStateStore.AppState restored = store.restoreAppState(0);
        assertEquals(0, restored.engineState.currentTeam);
        assertFalse(restored.engineState.captureBonusStacks);
        assertEquals(19, restored.engineState.piecePositions[0]);
        assertTrue(restored.timerPaused);
        assertArrayEquals((int[]) field(activity, "teamColors"), restored.teamColors);
        assertArrayEquals((int[]) field(activity, "teamShapes"), restored.teamShapes);
        assertNotNull(restored.moveUndoState.reversePaths[0]);
        assertTrue(restored.moveUndoState.reversePaths[0].length >= 4);
    }

    @Test public void malformedBackupNeverReplacesCurrentGame() throws Exception {
        call("handleYutInput", 2); call("selectPiece", 0, 0); call("commitSelectedMove"); settle();
        GameStateStore store = (GameStateStore) field(activity, "stateStore");
        String backup = store.exportBackup();
        for (int corruption = 0; corruption < 4; corruption++) {
            org.json.JSONObject json = new org.json.JSONObject(backup);
            if (corruption == 0) json.put("schema", 999);
            if (corruption == 1) json.getJSONObject("entries").getJSONObject("engine_positions").put("value", "invalid");
            if (corruption == 2) json.getJSONObject("entries").getJSONObject("engine_current_team").put("type", "string");
            if (corruption == 3) json.getJSONObject("entries").remove("game_started");
            try { store.importBackup(json.toString()); fail("Invalid backup must be rejected"); }
            catch (org.json.JSONException expected) { }
            assertEquals(17, store.restoreAppState(0).engineState.piecePositions[0]);
        }
    }

    @Test public void ruleSwitchOnlyChangesBeforeStartAndSettingsCopyActualVersion() {
        call("showTeamSetup"); call("showGameRulesDialog");
        AlertDialog rules = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        androidx.appcompat.widget.SwitchCompat toggle = rules.getWindow().getDecorView().findViewWithTag("capture_bonus_stacks");
        assertFalse(toggle.isChecked()); toggle.setChecked(true);
        rules.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        activity.findViewById(R.id.btn_team_4).performClick();
        ((AlertDialog) field(activity, "teamAppearanceDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        settle(); assertTrue(game().isCaptureBonusStacks());
        call("showGameRulesDialog");
        rules = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        toggle = rules.getWindow().getDecorView().findViewWithTag("capture_bonus_stacks");
        assertFalse(toggle.isEnabled()); assertTrue(toggle.isChecked()); rules.dismiss();
        call("showSettingsDialog");
        AlertDialog settings = (AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        View version = settings.getWindow().getDecorView().findViewWithTag("app_version");
        version.performClick();
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        assertEquals("Yutnori " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        settings.dismiss();
    }

    private void screenshot(String name, View root) throws Exception {
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(activity.getResources().getColor(R.color.bg_main));
        root.draw(canvas);
        File folder = new File("build/reports/ux");
        folder.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(folder, name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        image.recycle();
    }

    private YutGameEngine game() { return (YutGameEngine) field(activity, "game"); }
    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void call(String name, int... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) { types[i] = int.class; values[i] = args[i]; }
            Method method = MainActivity.class.getDeclaredMethod(name, types);
            method.setAccessible(true); method.invoke(activity, values);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
