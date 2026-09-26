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
        root.draw(new Canvas(image));
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
