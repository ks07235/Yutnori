package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "ko-rKR-w360dp-h740dp-port-mdpi")
public class HistoryPresentationTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void start() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("yutnori_display", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("yutnori_state", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("sound_enabled", false).putBoolean("vibration_enabled", false).commit();
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @After public void close() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void emptyHistoryOpensTheFullLogAndReleasesTimerHold() {
        assertIcon(R.drawable.ic_log_history);
        assertEquals(activity.getString(R.string.history_none), latestIcon().getContentDescription());
        latestIcon().performClick();
        AlertDialog dialog = latestDialog();
        assertEquals(1, field(field(activity, "turnClock"), "timerDialogHoldCount"));
        assertNotNull(dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
        dialog.dismiss();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, field(field(activity, "turnClock"), "timerDialogHoldCount"));
    }

    @Test public void latestEntryAndFullHistoryKeepOrderColorAndNestedHolds() {
        startGame();
        call("handleYutInput", new Class<?>[]{int.class}, 4);
        String latest = "1팀: 윷 입력";
        assertEquals(latest, latestIcon().getContentDescription());
        assertIcon(R.drawable.ic_log_yut);
        int[] colors = (int[]) field(activity, "teamColors");
        assertEquals(TeamAppearance.label(colors[0]),
                ((GradientDrawable) latestIcon().getBackground()).getColor().getDefaultColor());
        ArrayList<String> before = new ArrayList<>(log());
        latestIcon().performClick();
        AlertDialog entry = latestDialog();
        assertEquals(latest, ((TextView) entry.findViewById(android.R.id.message)).getText().toString());
        assertEquals(1, field(field(activity, "turnClock"), "timerDialogHoldCount"));
        entry.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog all = latestDialog();
        assertNotSame(entry, all);
        assertEquals(1, field(field(activity, "turnClock"), "timerDialogHoldCount"));
        assertEquals(Arrays.asList("1팀: 윷 입력", "1팀: 새 게임 시작"), log());
        all.dismiss();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, field(field(activity, "turnClock"), "timerDialogHoldCount"));
        assertEquals(before, log());
        rail().performClick();
        latestDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        assertTrue(log().isEmpty());
        assertIcon(R.drawable.ic_log_history);
    }

    @Test public void koreanMoveLogKeepsStackCaptureFinishAndVictoryText() {
        YutGameEngine.MoveResult result = exampleMove();
        assertEquals("2팀: 1,3번 말 윷+도 이동(출발 대기 → 완주), 상대 2개 잡음, 2개 완주, 승리",
                call("formatMoveLog", new Class<?>[]{YutGameEngine.MoveResult.class, String.class}, result, "윷+도"));
        result.startNode = 29;
        result.targetNode = 22;
        assertEquals("2팀: 1,3번 말 개 이동(중앙 → 중앙), 상대 2개 잡음, 2개 완주, 승리",
                call("formatMoveLog", new Class<?>[]{YutGameEngine.MoveResult.class, String.class}, result, "개"));
    }

    @Test @Config(qualifiers = "en-rUS-w360dp-h740dp-port-mdpi")
    public void englishMoveLogKeepsItsOwnNamesAndHistoryIcons() {
        startGame();
        assertEquals("Team 1: new game started", latestIcon().getContentDescription());
        assertIcon(R.drawable.ic_log_start);
        assertEquals("Team 2: Piece(s) 1,3 moved with Yut+Do(Waiting area → Finished), captured 2 opponent piece(s), finished 2 piece(s), victory",
                call("formatMoveLog", new Class<?>[]{YutGameEngine.MoveResult.class, String.class}, exampleMove(), "Yut+Do"));
        assertIconPriority();
    }

    @Test public void historyIconPriorityAndTeamColorsRemainUnchanged() {
        startGame();
        assertIconPriority();
    }

    @Test public void undoAndRecreationKeepTheSameHistoryAndGameState() {
        startGame();
        call("handleYutInput", new Class<?>[]{int.class}, 4);
        ArrayList<String> afterYut = new ArrayList<>(log());
        call("handleYutInput", new Class<?>[]{int.class}, 1);
        call("undoLastAction", new Class<?>[0]);
        assertEquals(afterYut, log());
        YutGameEngine.SavedState before = game().saveState();
        controller.recreate();
        activity = controller.get();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(afterYut, log());
        assertIcon(R.drawable.ic_log_yut);
        assertArrayEquals(before.pendingIds, game().saveState().pendingIds);
        assertArrayEquals(before.piecePositions, game().saveState().piecePositions);
    }

    @Test public void formattingVictorySummaryDoesNotModifyEngineOrLog() {
        startGame();
        for (int id = 0; id < 4; id++) game().getPiece(1, id).isFinished = true;
        YutGameEngine.SavedState before = game().saveState();
        ArrayList<String> history = new ArrayList<>(log());
        assertEquals("모든 말이 완주했습니다.\n\n승리 팀: 2팀\n완주: 4/4\n최근 기록: 1개\n\n마지막 진행\n1. 1팀: 새 게임 시작",
                call("buildGameOverMessage", new Class<?>[]{YutGameEngine.MoveResult.class}, exampleMove()));
        assertEquals(history, log());
        assertArrayEquals(before.piecePositions, game().saveState().piecePositions);
        assertArrayEquals(before.pieceFinished, game().saveState().pieceFinished);
        assertArrayEquals(before.selectedResultIds, game().saveState().selectedResultIds);
    }

    private void assertIconPriority() {
        int[] keywords = {R.string.log_keyword_undo, R.string.log_keyword_finished,
                R.string.log_keyword_captured, R.string.log_keyword_moved,
                R.string.log_keyword_turn_end, R.string.log_keyword_new_game, R.string.log_keyword_roll};
        int[] icons = {R.drawable.ic_log_undo, R.drawable.ic_log_finish, R.drawable.ic_log_capture,
                R.drawable.ic_log_move, R.drawable.ic_log_turn_end, R.drawable.ic_log_start, R.drawable.ic_log_yut};
        for (int i = 0; i < keywords.length; i++) {
            call("addTurnLog", new Class<?>[]{String.class}, activity.getString(keywords[i]));
            assertIcon(icons[i]);
        }
        call("addTurnLog", new Class<?>[]{String.class}, activity.getString(R.string.log_keyword_undo)
                + " " + activity.getString(R.string.log_keyword_finished));
        assertIcon(R.drawable.ic_log_undo);
    }

    private YutGameEngine.MoveResult exampleMove() {
        YutGameEngine.MoveResult result = YutGameEngine.MoveResult.success(1, 5);
        result.startNode = BoardPath.START_NODE;
        result.targetNode = BoardPath.END_NODE;
        result.usedPieceIds.addAll(Arrays.asList(0, 2));
        result.caughtPieces.add(new YutGameEngine.PieceRef(0, 0));
        result.caughtPieces.add(new YutGameEngine.PieceRef(0, 1));
        result.finishedPieceIds.addAll(Arrays.asList(0, 2));
        result.gameWon = true;
        return result;
    }

    private void startGame() {
        activity.findViewById(R.id.btn_team_2).performClick();
        ((AlertDialog) field(activity, "teamAppearanceDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private LinearLayout rail() { return activity.findViewById(R.id.action_log_rail); }
    private ImageView latestIcon() { return (ImageView) rail().getChildAt(0); }
    private AlertDialog latestDialog() { return (AlertDialog) ShadowDialog.getLatestDialog(); }
    private void assertIcon(int resource) { assertEquals(resource, shadowOf(latestIcon().getDrawable()).getCreatedFromResId()); }
    private YutGameEngine game() { return (YutGameEngine) field(activity, "game"); }
    @SuppressWarnings("unchecked") private ArrayList<String> log() { return (ArrayList<String>) field(activity, "turnLog"); }
    private static Object field(Object target, String name) {
        try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private Object call(String name, Class<?>[] types, Object... args) {
        try { Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(activity, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
