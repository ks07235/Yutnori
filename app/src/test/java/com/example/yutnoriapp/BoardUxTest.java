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

    @Test public void unlimitedModeRemovesTimerRowAndRestoresItForTimedPlay() throws Exception {
        settings(0L);
        settle();
        assertEquals(View.GONE, activity.findViewById(R.id.text_timer).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_time_stop).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.turn_tools).getVisibility());
        assertSame(activity.findViewById(R.id.results_row), activity.findViewById(R.id.btn_end_turn).getParent());
        assertEquals(View.GONE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.btn_toggle_controls).getVisibility());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
        assertTrue(bounds(activity.findViewById(R.id.btn_do)).left >= bounds(activity.findViewById(R.id.board_container)).right);
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
        assertEquals(View.VISIBLE, activity.findViewById(R.id.control_panel).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.unlimited_rolls).getVisibility());
    }

    private void advanceFrames(int millis) {
        for (int elapsed = 0; elapsed < millis; elapsed += 16) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        }
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
        View root = activity.findViewById(R.id.root_layout);
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
