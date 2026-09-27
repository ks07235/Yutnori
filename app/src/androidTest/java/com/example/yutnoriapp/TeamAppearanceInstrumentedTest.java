package com.example.yutnoriapp;

import android.content.Context;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TeamAppearanceInstrumentedTest {
    @Before public void clearState() {
        InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSharedPreferences("yutnori_state", Context.MODE_PRIVATE).edit().clear().commit();
    }

    static AlertDialog dialog(MainActivity activity) {
        return (AlertDialog) field(activity, "teamAppearanceDialog");
    }

    static void startRecommendedGame(MainActivity activity) {
        activity.findViewById(R.id.btn_team_2).performClick();
        dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    private static Object field(MainActivity activity, String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void choose(MainActivity activity, String tag) {
        View option = dialog(activity).getWindow().getDecorView().findViewWithTag(tag);
        assertNotNull(tag, option);
        option.performClick();
    }

    @Test public void everyTeamCountOpensSetupAndCancelDoesNotStartGame() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (int button : new int[]{R.id.btn_team_2, R.id.btn_team_3, R.id.btn_team_4}) {
                scenario.onActivity(activity -> {
                    activity.findViewById(button).performClick();
                    assertTrue(dialog(activity).isShowing());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.setup_panel).getVisibility());
                    assertFalse((Boolean) field(activity, "gameStarted"));
                    dialog(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    assertNull(dialog(activity));
                    assertFalse((Boolean) field(activity, "gameStarted"));
                });
            }
        }
    }

    @Test public void duplicateColorIsRejectedAndRecommendedButtonRandomizesDistinctDraft() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btn_team_4).performClick();
                choose(activity, "appearance_color_1");
                assertEquals(TeamAppearance.FORSYTHIA, ((int[]) field(activity, "draftTeamColors"))[0]);
                choose(activity, "appearance_color_5");
                choose(activity, "appearance_shape_4");
                dialog(activity).getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
                assertTrue(dialog(activity).isShowing());
                int[] colors = (int[]) field(activity, "draftTeamColors");
                int[] shapes = (int[]) field(activity, "draftTeamShapes");
                assertTrue(TeamAppearance.distinctColors(colors, 4));
                for (int i = 0; i < 4; i++) for (int j = 0; j < i; j++) assertNotEquals(shapes[i], shapes[j]);
                dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertEquals(4, ((YutGameEngine) field(activity, "game")).getTeamCount());
            });
        }
    }

    @Test public void draftAndStartedGameKeepWhiteBlackAndShapesAfterRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btn_team_3).performClick();
                choose(activity, "appearance_color_5");
                choose(activity, "appearance_shape_4");
                choose(activity, "appearance_team_1");
                choose(activity, "appearance_color_6");
                choose(activity, "appearance_shape_5");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(dialog(activity).isShowing());
                assertArrayEquals(new int[]{5, 6, 0, 3}, (int[]) field(activity, "draftTeamColors"));
                assertArrayEquals(new int[]{4, 5, 2, 3}, (int[]) field(activity, "draftTeamShapes"));
                dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNull(dialog(activity));
                assertTrue((Boolean) field(activity, "gameStarted"));
                assertArrayEquals(new int[]{5, 6, 0, 3}, (int[]) field(activity, "teamColors"));
                assertArrayEquals(new int[]{4, 5, 2, 3}, (int[]) field(activity, "teamShapes"));
                assertEquals(3, ((YutGameEngine) field(activity, "game")).getTeamCount());
                PieceStackView[][] pieces = (PieceStackView[][]) field(activity, "pieceViews");
                for (int team = 0; team < 3; team++) assertNotNull(pieces[team][0]);
            });
        }
    }
}
