package com.example.yutnoriapp;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RotationStabilityTest {
    @Before
    public void clearState() {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("yutnori_state", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private ActivityScenario<MainActivity> start() {
        ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class);
        s.onActivity(a -> a.findViewById(R.id.btn_team_2).performClick());
        SystemClock.sleep(500);
        return s;
    }

    private static Object field(MainActivity a, String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(a); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static void set(MainActivity a, String name, Object value) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(a, value); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static void call(MainActivity a, String name) {
        try { Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(a); }
        catch (Exception e) { throw new AssertionError(e); }
    }

    private static YutGameEngine game(MainActivity a) { return (YutGameEngine) field(a, "game"); }

    private static void commit(MainActivity a) {
        set(a, "selectedPreviewTeamId", 0);
        set(a, "selectedPreviewPieceId", 0);
        call(a, "commitSelectedMove");
    }

    @Test
    public void rotationDuringMoveKeepsNewTurnTimeAndExactlyOneHistoryEntry() {
        try (ActivityScenario<MainActivity> s = start()) {
            s.onActivity(a -> {
                set(a, "remainingTurnMillis", 7_000L);
                a.findViewById(R.id.btn_do).performClick();
                commit(a);
                assertTrue((boolean) field(a, "isAnimatingMove"));
                assertEquals(1, game(a).getCurrentTeam());
                assertEquals(180_000L, (long) field(a, "remainingTurnMillis"), 100);
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(900);
            for (int i = 0; i < 6; i++) {
                final int orientation = i % 2 == 0 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                s.onActivity(a -> a.setRequestedOrientation(orientation));
                SystemClock.sleep(450);
                s.onActivity(a -> {
                    assertEquals(16, game(a).getPiece(0, 0).position);
                    assertEquals(1, game(a).getCurrentTeam());
                    assertTrue((long) field(a, "remainingTurnMillis") > 165_000L);
                    assertEquals(3, ((List<?>) field(a, "turnLog")).size());
                    assertFalse((boolean) field(a, "isAnimatingMove"));
                    assertBoardLayersAndPiece(a, 16);
                });
            }
        }
    }

    @Test
    public void victorySurvivesMidMoveRotationAndActivityRecreation() {
        try (ActivityScenario<MainActivity> s = start()) {
            s.onActivity(a -> {
                for (int id = 1; id < 4; id++) {
                    game(a).getPiece(0, id).position = BoardPath.END_NODE;
                    game(a).getPiece(0, id).isFinished = true;
                }
                game(a).getPiece(0, 0).position = 15;
                a.findViewById(R.id.btn_do).performClick();
                commit(a);
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(800);
            s.recreate();
            SystemClock.sleep(500);
            s.onActivity(a -> {
                assertTrue(game(a).isGameOver());
                assertTrue((boolean) field(a, "victoryPending"));
                assertTrue((boolean) field(a, "gameOverDialogVisible"));
            });
        }
    }

    @Test
    public void undoBonusRollRemovesTime() {
        try (ActivityScenario<MainActivity> s = start()) {
            s.onActivity(a -> {
                set(a, "isTimerPaused", true);
                long before = (long) field(a, "remainingTurnMillis");
                for (int i = 0; i < 4; i++) {
                    a.findViewById(R.id.btn_yut).performClick();
                    assertEquals(before + 30_000L, (long) field(a, "remainingTurnMillis"));
                    a.findViewById(R.id.btn_undo_roll).performClick();
                    assertEquals(before, (long) field(a, "remainingTurnMillis"));
                }
            });
        }
    }

    @Test
    public void stoppedActivityCannotChargeTimeWhenSavedAgain() {
        try (ActivityScenario<MainActivity> s = start()) {
            AtomicReference<MainActivity> ref = new AtomicReference<>();
            s.onActivity(ref::set);
            s.moveToState(Lifecycle.State.CREATED);
            long before = (long) field(ref.get(), "remainingTurnMillis");
            SystemClock.sleep(1_500);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> call(ref.get(), "persistGameState"));
            assertEquals(before, (long) field(ref.get(), "remainingTurnMillis"));
            s.moveToState(Lifecycle.State.RESUMED);
        }
    }

    @Test
    public void twoCaptureBonusesSurviveMidMoveRotation() {
        try (ActivityScenario<MainActivity> s = start()) {
            s.onActivity(a -> {
                set(a, "isTimerPaused", true);
                game(a).getPiece(1, 0).position = 16;
                game(a).getPiece(1, 1).position = 0;
                a.findViewById(R.id.btn_do).performClick();
                a.findViewById(R.id.btn_yut).performClick();
                game(a).selectResult(0);
                game(a).selectResult(1);
                set(a, "remainingTurnMillis", 10_000L);
                commit(a);
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(900);
            s.onActivity(a -> {
                assertEquals(2, game(a).getNormalRollAllowance());
                assertEquals(70_000L, (long) field(a, "remainingTurnMillis"));
                assertEquals(BoardPath.START_NODE, game(a).getPiece(1, 0).position);
                assertEquals(BoardPath.START_NODE, game(a).getPiece(1, 1).position);
            });
        }
    }

    @Test
    public void pendingResultReopensControlsAfterPartialMove() {
        try (ActivityScenario<MainActivity> s = start()) {
            s.onActivity(a -> {
                a.findViewById(R.id.btn_yut).performClick();
                a.findViewById(R.id.btn_gae).performClick();
                game(a).selectResult(0);
                commit(a);
                a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            SystemClock.sleep(1_500);
            s.onActivity(a -> {
                assertEquals(1, game(a).getPendingResults().size());
                assertEquals(View.VISIBLE, a.findViewById(R.id.control_panel).getVisibility());
            });
        }
    }

    @Test
    public void fourthWaitingPieceIsInsideTrayInEitherOrientation() {
        try (ActivityScenario<MainActivity> s = start()) {
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                s.onActivity(a -> a.setRequestedOrientation(orientation));
                SystemClock.sleep(600);
                s.onActivity(a -> {
                    FrameLayout[][] spots = (FrameLayout[][]) field(a, "waitSpots");
                    View fourth = spots[game(a).getCurrentTeam()][3];
                    ViewGroup parent = (ViewGroup) fourth.getParent();
                    assertTrue("Last waiting piece is clipped", fourth.getRight() <= parent.getWidth());
                    assertTrue(fourth.getBottom() <= parent.getHeight());
                });
            }
        }
    }

    @Test
    public void destinationWinsTouchOverAPieceOnTheSameNode() {
        try (ActivityScenario<MainActivity> s = start()) {
            AtomicReference<View> destination = new AtomicReference<>();
            s.onActivity(a -> {
                BoardOverlayLayout overlay = a.findViewById(R.id.board_overlay);
                View piece = new View(a);
                piece.setOnClickListener(v -> fail("Piece intercepted destination"));
                piece.setElevation(10);
                overlay.addView(piece, new FrameLayout.LayoutParams(100, 100));
                overlay.anchor(piece, 16);
                View arrow = new View(a);
                arrow.setElevation(14);
                arrow.setOnClickListener(v -> destination.set(v));
                overlay.addView(arrow, new FrameLayout.LayoutParams(100, 100));
                overlay.anchor(arrow, 16);
            });
            SystemClock.sleep(200);
            s.onActivity(a -> {
                BoardOverlayLayout overlay = a.findViewById(R.id.board_overlay);
                float[] point = overlay.centerForSpot(16);
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
                MotionEvent up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, point[0], point[1], 0);
                overlay.dispatchTouchEvent(down);
                overlay.dispatchTouchEvent(up);
                down.recycle(); up.recycle();
                assertNotNull(destination.get());
            });
        }
    }

    private void assertBoardLayersAndPiece(MainActivity a, int node) {
        View art = a.findViewById(R.id.board_art);
        BoardOverlayLayout overlay = a.findViewById(R.id.board_overlay);
        assertEquals(art.getWidth(), overlay.getWidth());
        assertEquals(art.getHeight(), overlay.getHeight());
        int[] artOrigin = new int[2]; int[] overlayOrigin = new int[2];
        art.getLocationOnScreen(artOrigin); overlay.getLocationOnScreen(overlayOrigin);
        assertArrayEquals(artOrigin, overlayOrigin);
        PieceStackView[][] pieces = (PieceStackView[][]) field(a, "pieceViews");
        View piece = pieces[0][0];
        float[] center = overlay.centerForSpot(node);
        assertEquals(center[0], piece.getX() + piece.getWidth() / 2f, 1.5f);
        assertEquals(center[1], piece.getY() + piece.getHeight() / 2f, 1.5f);
    }
}
