package com.example.yutnoriapp;

import android.content.Context;
import android.content.SharedPreferences;

final class GameStateStore {
    static final long DEFAULT_TURN_DURATION_MILLIS = 180_000L;
    static final long SAVED_GAME_CONFIRM_AFTER_MILLIS = 24L * 60L * 60L * 1_000L;
    static final long[] TURN_DURATION_OPTIONS_MILLIS = {
            60_000L, 120_000L, 180_000L, 300_000L, 0L
    };

    private static final String PREFS_NAME = "yutnori_state";
    private static final String KEY_TURN_DURATION = "turn_duration";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    private static final String KEY_GAME_STARTED = "game_started";
    private static final String KEY_GAME_SAVED_AT = "game_saved_at";
    private static final String KEY_REMAINING_TIMER = "remaining_timer";
    private static final String KEY_TIMER_CHECKPOINT = "timer_checkpoint";
    private static final String KEY_TIMER_PAUSED = "timer_paused";
    private static final String KEY_TIME_EXPIRED = "time_expired";
    private static final String KEY_STATUS_TEXT = "status_text";
    private static final String KEY_STATUS_COLOR = "status_color";
    private static final String KEY_TURN_LOG = "turn_log";
    private static final String KEY_LANGUAGE_TAG = "language_tag";
    private static final String KEY_SELECTED_TEAM = "selected_team";
    private static final String KEY_SELECTED_PIECE = "selected_piece";
    private static final String KEY_ENGINE_TEAM_COUNT = "engine_team_count";
    private static final String KEY_ENGINE_CURRENT_TEAM = "engine_current_team";
    private static final String KEY_ENGINE_NEXT_RESULT_ID = "engine_next_result_id";
    private static final String KEY_ENGINE_NORMAL_ROLL_ALLOWANCE = "engine_normal_roll_allowance";
    private static final String KEY_ENGINE_ROLL_ALLOWED = "engine_roll_allowed";
    private static final String KEY_ENGINE_MUST_ROLL = "engine_must_roll";
    private static final String KEY_ENGINE_CATCH_BONUS = "engine_catch_bonus";
    private static final String KEY_ENGINE_GAME_OVER = "engine_game_over";
    private static final String KEY_ENGINE_PENDING_IDS = "engine_pending_ids";
    private static final String KEY_ENGINE_PENDING_STEPS = "engine_pending_steps";
    private static final String KEY_ENGINE_SELECTED_IDS = "engine_selected_ids";
    private static final String KEY_ENGINE_POSITIONS = "engine_positions";
    private static final String KEY_ENGINE_ROUTES = "engine_routes";
    private static final String KEY_ENGINE_FINISHED = "engine_finished";
    private static final String KEY_MOVE_UNDO_AVAILABLE = "move_undo_available";
    private static final String KEY_MOVE_UNDO_REMAINING_TIMER = "move_undo_remaining_timer";
    private static final String KEY_MOVE_UNDO_TIMER_PAUSED = "move_undo_timer_paused";
    private static final String KEY_MOVE_UNDO_TIME_EXPIRED = "move_undo_time_expired";
    private static final String KEY_MOVE_UNDO_SELECTED_TEAM = "move_undo_selected_team";
    private static final String KEY_MOVE_UNDO_SELECTED_PIECE = "move_undo_selected_piece";
    private static final String KEY_MOVE_UNDO_STATUS_TEXT = "move_undo_status_text";
    private static final String KEY_MOVE_UNDO_STATUS_COLOR = "move_undo_status_color";
    private static final String KEY_MOVE_UNDO_TURN_LOG = "move_undo_turn_log";
    private static final String MOVE_UNDO_ENGINE_PREFIX = "move_undo_engine_";

    private final SharedPreferences prefs;

    GameStateStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    Settings loadSettings() {
        Settings settings = new Settings();
        settings.turnDurationMillis = normalizeTurnDuration(
                prefs.getLong(KEY_TURN_DURATION, DEFAULT_TURN_DURATION_MILLIS));
        settings.soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true);
        settings.vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
        return settings;
    }

    AppState restoreAppState(int defaultStatusColor) {
        if (!prefs.getBoolean(KEY_GAME_STARTED, false) || !prefs.contains(KEY_ENGINE_TEAM_COUNT)) {
            return null;
        }

        AppState appState = new AppState();
        appState.gameStarted = true;
        appState.victoryPending = prefs.getBoolean("victory_pending", false);
        appState.bonusTimeBaselineResultId = prefs.getInt("bonus_time_baseline_result_id", 0);
        appState.savedAtEpochMillis = Math.max(0L, prefs.getLong(KEY_GAME_SAVED_AT, 0L));
        appState.remainingTurnMillis = Math.max(0L, prefs.getLong(KEY_REMAINING_TIMER, DEFAULT_TURN_DURATION_MILLIS));
        appState.timerCheckpointEpochMillis = Math.max(0L, prefs.getLong(KEY_TIMER_CHECKPOINT, 0L));
        appState.timerPaused = prefs.getBoolean(KEY_TIMER_PAUSED, false);
        appState.timeExpiredNotified = prefs.getBoolean(KEY_TIME_EXPIRED, false);
        appState.selectedTeamId = prefs.getInt(KEY_SELECTED_TEAM, -1);
        appState.selectedPieceId = prefs.getInt(KEY_SELECTED_PIECE, -1);
        appState.statusMessage = prefs.getString(KEY_STATUS_TEXT, "");
        appState.statusColor = prefs.getInt(KEY_STATUS_COLOR, defaultStatusColor);
        appState.turnLog = parseStrings(prefs.getString(KEY_TURN_LOG, ""));
        appState.languageTag = prefs.getString(KEY_LANGUAGE_TAG, "");

        YutGameEngine.SavedState engineState = new YutGameEngine.SavedState();
        engineState.teamCount = prefs.getInt(KEY_ENGINE_TEAM_COUNT, YutGameEngine.MIN_TEAM_COUNT);
        engineState.currentTeam = prefs.getInt(KEY_ENGINE_CURRENT_TEAM, 0);
        engineState.nextResultId = prefs.getInt(KEY_ENGINE_NEXT_RESULT_ID, 1);
        engineState.normalRollAllowance = prefs.getInt(KEY_ENGINE_NORMAL_ROLL_ALLOWANCE, -1);
        engineState.rollAllowed = prefs.getBoolean(KEY_ENGINE_ROLL_ALLOWED, true);
        engineState.mustRollBeforeMoving = prefs.getBoolean(KEY_ENGINE_MUST_ROLL, false);
        engineState.catchBonusPending = prefs.getBoolean(KEY_ENGINE_CATCH_BONUS, false);
        engineState.gameOver = prefs.getBoolean(KEY_ENGINE_GAME_OVER, false);
        engineState.pendingIds = parseInts(prefs.getString(KEY_ENGINE_PENDING_IDS, ""));
        engineState.pendingSteps = parseInts(prefs.getString(KEY_ENGINE_PENDING_STEPS, ""));
        engineState.selectedResultIds = parseInts(prefs.getString(KEY_ENGINE_SELECTED_IDS, ""));
        engineState.piecePositions = parseInts(prefs.getString(KEY_ENGINE_POSITIONS, ""));
        engineState.pieceRoutes = parseInts(prefs.getString(KEY_ENGINE_ROUTES, ""));
        engineState.pieceFinished = parseBooleans(prefs.getString(KEY_ENGINE_FINISHED, ""));
        appState.engineState = engineState;
        appState.moveUndoState = readMoveUndoState(defaultStatusColor);
        return appState;
    }

    void save(Settings settings, AppState appState) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_TURN_DURATION, settings.turnDurationMillis);
        editor.putBoolean(KEY_SOUND_ENABLED, settings.soundEnabled);
        editor.putBoolean(KEY_VIBRATION_ENABLED, settings.vibrationEnabled);
        editor.putBoolean(KEY_GAME_STARTED, appState.gameStarted);
        editor.putBoolean("victory_pending", appState.victoryPending);
        editor.putInt("bonus_time_baseline_result_id", appState.bonusTimeBaselineResultId);
        editor.putLong(
                KEY_GAME_SAVED_AT,
                appState.preserveSavedAtEpochMillis
                        ? Math.max(0L, appState.savedAtEpochMillis)
                        : System.currentTimeMillis());
        editor.putLong(KEY_REMAINING_TIMER, appState.remainingTurnMillis);
        editor.putLong(KEY_TIMER_CHECKPOINT, appState.timerCheckpointEpochMillis);
        editor.putBoolean(KEY_TIMER_PAUSED, appState.timerPaused);
        editor.putBoolean(KEY_TIME_EXPIRED, appState.timeExpiredNotified);
        editor.putInt(KEY_SELECTED_TEAM, appState.selectedTeamId);
        editor.putInt(KEY_SELECTED_PIECE, appState.selectedPieceId);
        editor.putString(KEY_STATUS_TEXT, appState.statusMessage);
        editor.putInt(KEY_STATUS_COLOR, appState.statusColor);
        editor.putString(KEY_TURN_LOG, joinStrings(appState.turnLog));
        editor.putString(KEY_LANGUAGE_TAG, appState.languageTag);

        YutGameEngine.SavedState state = appState.engineState;
        editor.putInt(KEY_ENGINE_TEAM_COUNT, state.teamCount);
        editor.putInt(KEY_ENGINE_CURRENT_TEAM, state.currentTeam);
        editor.putInt(KEY_ENGINE_NEXT_RESULT_ID, state.nextResultId);
        editor.putInt(KEY_ENGINE_NORMAL_ROLL_ALLOWANCE, state.normalRollAllowance);
        editor.putBoolean(KEY_ENGINE_ROLL_ALLOWED, state.rollAllowed);
        editor.putBoolean(KEY_ENGINE_MUST_ROLL, state.mustRollBeforeMoving);
        editor.putBoolean(KEY_ENGINE_CATCH_BONUS, state.catchBonusPending);
        editor.putBoolean(KEY_ENGINE_GAME_OVER, state.gameOver);
        editor.putString(KEY_ENGINE_PENDING_IDS, joinInts(state.pendingIds));
        editor.putString(KEY_ENGINE_PENDING_STEPS, joinInts(state.pendingSteps));
        editor.putString(KEY_ENGINE_SELECTED_IDS, joinInts(state.selectedResultIds));
        editor.putString(KEY_ENGINE_POSITIONS, joinInts(state.piecePositions));
        editor.putString(KEY_ENGINE_ROUTES, joinInts(state.pieceRoutes));
        editor.putString(KEY_ENGINE_FINISHED, joinBooleans(state.pieceFinished));
        writeMoveUndoState(editor, appState.moveUndoState);
        editor.apply();
    }

    private MoveUndoState readMoveUndoState(int defaultStatusColor) {
        if (!prefs.getBoolean(KEY_MOVE_UNDO_AVAILABLE, false)) {
            return null;
        }
        MoveUndoState state = new MoveUndoState();
        state.remainingTurnMillis = Math.max(
                0L,
                prefs.getLong(KEY_MOVE_UNDO_REMAINING_TIMER, DEFAULT_TURN_DURATION_MILLIS));
        state.timerPaused = prefs.getBoolean(KEY_MOVE_UNDO_TIMER_PAUSED, false);
        state.timeExpiredNotified = prefs.getBoolean(KEY_MOVE_UNDO_TIME_EXPIRED, false);
        state.selectedTeamId = prefs.getInt(KEY_MOVE_UNDO_SELECTED_TEAM, -1);
        state.selectedPieceId = prefs.getInt(KEY_MOVE_UNDO_SELECTED_PIECE, -1);
        state.statusMessage = prefs.getString(KEY_MOVE_UNDO_STATUS_TEXT, "");
        state.statusColor = prefs.getInt(KEY_MOVE_UNDO_STATUS_COLOR, defaultStatusColor);
        state.turnLog = parseStrings(prefs.getString(KEY_MOVE_UNDO_TURN_LOG, ""));
        state.engineState = readEngineState(MOVE_UNDO_ENGINE_PREFIX);
        return state;
    }

    private void writeMoveUndoState(SharedPreferences.Editor editor, MoveUndoState state) {
        boolean available = state != null && state.engineState != null;
        editor.putBoolean(KEY_MOVE_UNDO_AVAILABLE, available);
        if (!available) {
            return;
        }
        editor.putLong(KEY_MOVE_UNDO_REMAINING_TIMER, state.remainingTurnMillis);
        editor.putBoolean(KEY_MOVE_UNDO_TIMER_PAUSED, state.timerPaused);
        editor.putBoolean(KEY_MOVE_UNDO_TIME_EXPIRED, state.timeExpiredNotified);
        editor.putInt(KEY_MOVE_UNDO_SELECTED_TEAM, state.selectedTeamId);
        editor.putInt(KEY_MOVE_UNDO_SELECTED_PIECE, state.selectedPieceId);
        editor.putString(KEY_MOVE_UNDO_STATUS_TEXT, state.statusMessage);
        editor.putInt(KEY_MOVE_UNDO_STATUS_COLOR, state.statusColor);
        editor.putString(KEY_MOVE_UNDO_TURN_LOG, joinStrings(state.turnLog));
        writeEngineState(editor, MOVE_UNDO_ENGINE_PREFIX, state.engineState);
    }

    private YutGameEngine.SavedState readEngineState(String prefix) {
        YutGameEngine.SavedState state = new YutGameEngine.SavedState();
        state.teamCount = prefs.getInt(prefix + "team_count", YutGameEngine.MIN_TEAM_COUNT);
        state.currentTeam = prefs.getInt(prefix + "current_team", 0);
        state.nextResultId = prefs.getInt(prefix + "next_result_id", 1);
        state.normalRollAllowance = prefs.getInt(prefix + "normal_roll_allowance", -1);
        state.rollAllowed = prefs.getBoolean(prefix + "roll_allowed", true);
        state.mustRollBeforeMoving = prefs.getBoolean(prefix + "must_roll", false);
        state.catchBonusPending = prefs.getBoolean(prefix + "catch_bonus", false);
        state.gameOver = prefs.getBoolean(prefix + "game_over", false);
        state.pendingIds = parseInts(prefs.getString(prefix + "pending_ids", ""));
        state.pendingSteps = parseInts(prefs.getString(prefix + "pending_steps", ""));
        state.selectedResultIds = parseInts(prefs.getString(prefix + "selected_ids", ""));
        state.piecePositions = parseInts(prefs.getString(prefix + "positions", ""));
        state.pieceRoutes = parseInts(prefs.getString(prefix + "routes", ""));
        state.pieceFinished = parseBooleans(prefs.getString(prefix + "finished", ""));
        return state;
    }

    private void writeEngineState(
            SharedPreferences.Editor editor,
            String prefix,
            YutGameEngine.SavedState state) {
        editor.putInt(prefix + "team_count", state.teamCount);
        editor.putInt(prefix + "current_team", state.currentTeam);
        editor.putInt(prefix + "next_result_id", state.nextResultId);
        editor.putInt(prefix + "normal_roll_allowance", state.normalRollAllowance);
        editor.putBoolean(prefix + "roll_allowed", state.rollAllowed);
        editor.putBoolean(prefix + "must_roll", state.mustRollBeforeMoving);
        editor.putBoolean(prefix + "catch_bonus", state.catchBonusPending);
        editor.putBoolean(prefix + "game_over", state.gameOver);
        editor.putString(prefix + "pending_ids", joinInts(state.pendingIds));
        editor.putString(prefix + "pending_steps", joinInts(state.pendingSteps));
        editor.putString(prefix + "selected_ids", joinInts(state.selectedResultIds));
        editor.putString(prefix + "positions", joinInts(state.piecePositions));
        editor.putString(prefix + "routes", joinInts(state.pieceRoutes));
        editor.putString(prefix + "finished", joinBooleans(state.pieceFinished));
    }

    int getTurnDurationIndex(long turnDurationMillis) {
        for (int i = 0; i < TURN_DURATION_OPTIONS_MILLIS.length; i++) {
            if (TURN_DURATION_OPTIONS_MILLIS[i] == turnDurationMillis) {
                return i;
            }
        }
        return 2;
    }

    static boolean shouldConfirmSavedGame(long savedAtEpochMillis, long nowEpochMillis) {
        if (savedAtEpochMillis <= 0L) {
            return true;
        }
        if (nowEpochMillis < savedAtEpochMillis) {
            return false;
        }
        return nowEpochMillis - savedAtEpochMillis >= SAVED_GAME_CONFIRM_AFTER_MILLIS;
    }

    private long normalizeTurnDuration(long value) {
        for (long option : TURN_DURATION_OPTIONS_MILLIS) {
            if (option == value) {
                return value;
            }
        }
        return DEFAULT_TURN_DURATION_MILLIS;
    }

    private String joinInts(int[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private int[] parseInts(String value) {
        if (value == null || value.isEmpty()) {
            return new int[0];
        }
        String[] parts = value.split(",");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                values[i] = 0;
            }
        }
        return values;
    }

    private String joinBooleans(boolean[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i] ? '1' : '0');
        }
        return builder.toString();
    }

    private boolean[] parseBooleans(String value) {
        if (value == null || value.isEmpty()) {
            return new boolean[0];
        }
        String[] parts = value.split(",");
        boolean[] values = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = "1".equals(parts[i]) || "true".equals(parts[i]);
        }
        return values;
    }

    private String joinStrings(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\u001E');
            }
            builder.append(value.replace('\u001E', ' '));
        }
        return builder.toString();
    }

    private String[] parseStrings(String value) {
        if (value == null || value.isEmpty()) {
            return new String[0];
        }
        return value.split("\u001E", -1);
    }

    static class Settings {
        long turnDurationMillis;
        boolean soundEnabled;
        boolean vibrationEnabled;
    }

    static class AppState {
        boolean gameStarted;
        boolean victoryPending;
        int bonusTimeBaselineResultId;
        long savedAtEpochMillis;
        boolean preserveSavedAtEpochMillis;
        long remainingTurnMillis;
        long timerCheckpointEpochMillis;
        boolean timerPaused;
        boolean timeExpiredNotified;
        int selectedTeamId = -1;
        int selectedPieceId = -1;
        String statusMessage = "";
        int statusColor;
        String[] turnLog = new String[0];
        String languageTag = "";
        YutGameEngine.SavedState engineState;
        MoveUndoState moveUndoState;
    }

    static class MoveUndoState {
        long remainingTurnMillis;
        boolean timerPaused;
        boolean timeExpiredNotified;
        int selectedTeamId = -1;
        int selectedPieceId = -1;
        String statusMessage = "";
        int statusColor;
        String[] turnLog = new String[0];
        YutGameEngine.SavedState engineState;
    }
}
