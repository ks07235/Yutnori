package com.example.yutnoriapp;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final long BONUS_TURN_MILLIS = 30_000L;
    private static final long TIMER_TICK_MILLIS = 1_000L;
    private static final long BACK_EXIT_INTERVAL_MILLIS = 1_800L;
    private static final int MAX_TURN_LOG_ENTRIES = 12;

    private final YutGameEngine game = new YutGameEngine();
    private GameStateStore stateStore;
    private GameFeedback feedback;

    private TextView[][] pieceViews;
    private FrameLayout[][] waitSpots;
    private LinearLayout[] finishedLayouts;
    private FrameLayout boardContainer;
    private View topPanel;
    private View controlPanel;
    private View btnToggleInfo;
    private View btnToggleControls;
    private TextView textStatus;
    private TextView textTimer;
    private LinearLayout actionLogRail;
    private TextView btnTimeStop;
    private LinearLayout resultLayout;
    private LinearLayout finishedSummaryLayout;
    private LinearLayout waitingArea;
    private View setupPanel;
    private final ArrayList<View> previewViews = new ArrayList<>();
    private final ArrayList<String> turnLog = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (shouldTimerRun()) {
                syncTimerToNow();
                updateTimerView();
                if (remainingTurnMillis == 0L && !timeExpiredNotified) {
                    timeExpiredNotified = true;
                    textStatus.setText("\uc2dc\uac04\uc774 \uc885\ub8cc\ub410\uc2b5\ub2c8\ub2e4. \ud134 \uc885\ub8cc\ub97c \ub20c\ub7ec \ub2e4\uc74c \ud300\uc73c\ub85c \ub118\uae30\uc138\uc694.");
                    textStatus.setTextColor(getResources().getColor(R.color.btn_restart));
                    persistGameState();
                }
            }
            timerHandler.postDelayed(this, TIMER_TICK_MILLIS);
        }
    };
    private int selectedPreviewTeamId = -1;
    private int selectedPreviewPieceId = -1;
    private boolean isAnimatingMove = false;
    private long turnDurationMillis = GameStateStore.DEFAULT_TURN_DURATION_MILLIS;
    private long remainingTurnMillis = GameStateStore.DEFAULT_TURN_DURATION_MILLIS;
    private long lastBackPressMillis = 0L;
    private boolean isTimerPaused = false;
    private boolean isTimerHeldForAnimation = false;
    private int timerDialogHoldCount = 0;
    private boolean timeExpiredNotified = false;
    private long timerCheckpointEpochMillis = 0L;
    private boolean gameStarted = false;
    private boolean landscapeInfoOpen = false;
    private boolean landscapeControlsOpen = true;
    private String restoredStatusMessage = "";
    private int restoredStatusColor = Color.TRANSPARENT;
    private int layoutGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stateStore = new GameStateStore(this);
        feedback = new GameFeedback(this);
        loadSettings();
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        bindBackExitHandler();
        if (restorePersistedGameState()) {
            restoreGameScreen(restoredStatusMessage, restoredStatusColor);
        } else {
            showTeamSetup();
        }
        timerHandler.post(timerTick);
        enterImmersiveMode();
    }

    private void bindViewsAndActions() {
        layoutGeneration++;
        textStatus = findViewById(R.id.text_status);
        textTimer = findViewById(R.id.text_timer);
        actionLogRail = findViewById(R.id.action_log_rail);
        if (actionLogRail != null) {
            actionLogRail.setOnClickListener(v -> showTurnLogDialog());
            actionLogRail.setClickable(true);
            actionLogRail.setFocusable(true);
        }
        btnTimeStop = findViewById(R.id.btn_time_stop);
        topPanel = findViewById(R.id.top_panel);
        boardContainer = findViewById(R.id.board_container);
        controlPanel = findViewById(R.id.control_panel);
        btnToggleInfo = findViewById(R.id.btn_toggle_info);
        btnToggleControls = findViewById(R.id.btn_toggle_controls);
        resultLayout = findViewById(R.id.layout_results);
        finishedSummaryLayout = findViewById(R.id.layout_finished_summary);
        waitingArea = findViewById(R.id.waiting_area);
        setupPanel = findViewById(R.id.setup_panel);

        pieceViews = new TextView[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
        waitSpots = new FrameLayout[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
        finishedLayouts = new LinearLayout[YutGameEngine.MAX_TEAM_COUNT];

        createPieceViews();
        bindYutButtons();
        bindTeamSetupButtons();
        bindLandscapeDrawerButtons();
        applyButtonContrast();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Leaving the app pauses a physical-board game automatically.
        timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.post(timerTick);
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        syncTimerToNow();
        persistGameState();
        timerHandler.removeCallbacks(timerTick);
        super.onPause();
    }

    @Override
    protected void onStop() {
        persistGameState();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        persistGameState();
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        String statusMessage = textStatus == null
                ? ""
                : textStatus.getText().toString();
        int statusColor = textStatus == null
                ? getResources().getColor(R.color.text_status)
                : textStatus.getCurrentTextColor();
        boolean wasGameStarted = gameStarted;

        super.onConfigurationChanged(newConfig);
        isAnimatingMove = false;
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        if (wasGameStarted) {
            restoreGameScreen(statusMessage, statusColor);
        } else {
            showTeamSetup();
        }
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerTick);
        if (feedback != null) {
            feedback.release();
        }
        super.onDestroy();
    }

    private void createPieceViews() {
        for (int team = 0; team < YutGameEngine.TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                TextView view = new TextView(this);
                view.setGravity(Gravity.CENTER);
                view.setText(String.valueOf(id + 1));
                view.setTextColor(Color.WHITE);
                view.setTextSize(14);
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setBackground(createPieceDrawable(team));
                view.setElevation(dp(7));

                final int teamIndex = team;
                final int pieceIndex = id;
                view.setOnClickListener(v -> selectPiece(teamIndex, pieceIndex));
                pieceViews[team][id] = view;
            }
        }
    }

    private void bindYutButtons() {
        bindPressAction(R.id.btn_bdo, () -> handleYutInput(-1));
        bindPressAction(R.id.btn_do, () -> handleYutInput(1));
        bindPressAction(R.id.btn_gae, () -> handleYutInput(2));
        bindPressAction(R.id.btn_geol, () -> handleYutInput(3));
        bindPressAction(R.id.btn_yut, () -> handleYutInput(4));
        bindPressAction(R.id.btn_mo, () -> handleYutInput(5));
        bindPressAction(R.id.btn_undo_roll, this::undoLastRoll);
        bindPressAction(R.id.btn_end_turn, this::endCurrentTurn);
        bindPressAction(R.id.btn_time_stop, this::toggleTimeStop);
        bindPressAction(R.id.btn_settings, this::showSettingsDialog);
        bindPressAction(R.id.btn_setup_settings, this::showSettingsDialog);
        bindPressAction(R.id.btn_restart, this::requestNewGame);
    }

    private void bindTeamSetupButtons() {
        bindPressAction(R.id.btn_team_2, () -> startGame(2));
        bindPressAction(R.id.btn_team_3, () -> startGame(3));
        bindPressAction(R.id.btn_team_4, () -> startGame(4));
    }

    private void bindLandscapeDrawerButtons() {
        if (!hasLandscapeDrawers()) {
            return;
        }
        bindPressAction(R.id.btn_toggle_info, () -> {
            landscapeInfoOpen = !landscapeInfoOpen;
            applyLandscapeDrawerState(true);
        });
        bindPressAction(R.id.btn_toggle_controls, () -> {
            landscapeControlsOpen = !landscapeControlsOpen;
            applyLandscapeDrawerState(true);
        });
    }

    private void applyButtonContrast() {
        int darkText = getResources().getColor(R.color.ink_black);
        int lightText = getResources().getColor(R.color.btn_text_color);
        int[] darkButtonIds = {
                R.id.btn_bdo, R.id.btn_do, R.id.btn_gae, R.id.btn_geol, R.id.btn_yut, R.id.btn_mo,
                R.id.btn_time_stop, R.id.btn_undo_roll,
                R.id.btn_team_2, R.id.btn_team_3, R.id.btn_team_4
        };
        int[] lightButtonIds = {R.id.btn_end_turn, R.id.btn_restart};

        for (int id : darkButtonIds) {
            TextView button = findViewById(id);
            if (button != null) {
                button.setTextColor(darkText);
                button.setBackgroundTintList(null);
            }
        }
        for (int id : lightButtonIds) {
            TextView button = findViewById(id);
            if (button != null) {
                button.setTextColor(lightText);
                button.setBackgroundTintList(null);
            }
        }
    }

    private void showTeamSetup() {
        gameStarted = false;
        isAnimatingMove = false;
        isTimerHeldForAnimation = false;
        pauseTimer();
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        finishedSummaryLayout.removeAllViews();
        waitingArea.removeAllViews();
        removeAllPiecesFromScreen();
        topPanel.setVisibility(View.INVISIBLE);
        boardContainer.setVisibility(View.INVISIBLE);
        controlPanel.setVisibility(View.INVISIBLE);
        setLandscapeDrawerTabsVisible(false);
        setupPanel.setVisibility(View.VISIBLE);
        setupPanel.setElevation(dp(24));
        setupPanel.bringToFront();
        updateTurnLogView();
        persistGameState();
        enterImmersiveMode();
    }

    private void requestNewGame() {
        if (!gameStarted || game.isGameOver()) {
            showTeamSetup();
            return;
        }

        beginTimerDialogHold();
        YutDialogs.showNewGameConfirmation(
                this,
                this::showTeamSetup,
                this::endTimerDialogHold);
    }

    private void startGame(int teamCount) {
        YutGameEngine.ActionResult result = game.setTeamCount(teamCount);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        gameStarted = true;
        setupPanel.setVisibility(View.GONE);
        topPanel.setVisibility(View.VISIBLE);
        boardContainer.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        resetGame();
        prepareLandscapeDrawers(true);
        persistGameState();
        enterImmersiveMode();
    }

    private void restoreGameScreen(String statusMessage, int statusColor) {
        isTimerHeldForAnimation = false;
        setupPanel.setVisibility(View.GONE);
        topPanel.setVisibility(View.VISIBLE);
        boardContainer.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(View.VISIBLE);
        setLandscapeDrawerTabsVisible(true);
        resultLayout.removeAllViews();
        buildTeamAreas();

        for (int team = 0; team < game.getTeamCount(); team++) {
            finishedLayouts[team].removeAllViews();
        }

        boardContainer.post(() -> {
            for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
                for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                    Piece piece = game.getPiece(team, id);
                    if (team >= game.getTeamCount()) {
                        removeFromParent(pieceViews[team][id]);
                    } else if (piece.isFinished) {
                        moveToFinishedArea(team, id);
                    } else if (piece.position == BoardPath.START_NODE) {
                        if (team == game.getCurrentTeam()) {
                            moveToWaitSpot(team, id);
                        } else {
                            removeFromParent(pieceViews[team][id]);
                        }
                    } else {
                        movePieceUI(team, id, piece.position);
                    }
                }
            }
            updatePieceSelectionStyles();
            updateMovePreviews();
        });

        if (statusMessage.isEmpty()) {
            textStatus.setText(YutGameEngine.getTeamName(game.getCurrentTeam()) + " \ucc28\ub840\uc785\ub2c8\ub2e4.");
            textStatus.setTextColor(getStatusColor());
        } else {
            textStatus.setText(statusMessage);
            textStatus.setTextColor(statusColor);
        }
        updateResultButtons();
        updateTimerView();
        updateTurnLogView();
        setControlsEnabled(!game.isGameOver());
        if (game.isGameOver()) {
            findViewById(R.id.btn_restart).setEnabled(true);
        }
        prepareLandscapeDrawers(false);
    }

    private void resetGame() {
        isAnimatingMove = false;
        isTimerHeldForAnimation = false;
        game.reset();
        resultLayout.removeAllViews();
        buildTeamAreas();
        for (int team = 0; team < game.getTeamCount(); team++) {
            finishedLayouts[team].removeAllViews();
        }

        textStatus.setText("1\ud300 \ucc28\ub840\uc785\ub2c8\ub2e4. \uc737 \uacb0\uacfc\ub97c \uc785\ub825\ud558\uc138\uc694.");
        textStatus.setTextColor(getStatusColor());
        textStatus.animate().cancel();
        textStatus.setScaleX(1f);
        textStatus.setScaleY(1f);
        clearSelectedPiece();
        clearMovePreviews();
        setControlsEnabled(true);
        startTurnTimer();
        turnLog.clear();
        addTurnLog("1\ud300: \uc0c8 \uac8c\uc784 \uc2dc\uc791");

        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                if (team == game.getCurrentTeam()) {
                    moveToWaitSpot(team, id);
                } else {
                    removeFromParent(pieceViews[team][id]);
                }
            }
        }
    }

    private void buildTeamAreas() {
        finishedSummaryLayout.removeAllViews();
        finishedSummaryLayout.setOrientation(LinearLayout.VERTICAL);
        waitingArea.removeAllViews();
        waitingArea.setOrientation(LinearLayout.VERTICAL);

        for (int team = 0; team < game.getTeamCount(); team++) {
            finishedLayouts[team] = createFinishedTeamRow(team);
        }
        rebuildCurrentWaitingArea();
    }

    private LinearLayout createFinishedTeamRow(int team) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));
        finishedSummaryLayout.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(28)));

        TextView label = new TextView(this);
        label.setText(YutGameEngine.getTeamName(team) + " \uc644\uc8fc");
        label.setTextColor(getTeamColor(team));
        label.setTextSize(11);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(dp(66), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout finishedLayout = new LinearLayout(this);
        finishedLayout.setGravity(Gravity.CENTER_VERTICAL);
        finishedLayout.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(finishedLayout, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));

        return finishedLayout;
    }

    private void createWaitingTeamRow(int team) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36));
        if (team > 0) {
            rowParams.setMargins(0, dp(5), 0, 0);
        }
        waitingArea.addView(row, rowParams);

        TextView label = new TextView(this);
        label.setText(YutGameEngine.getTeamName(team));
        label.setTextColor(getTeamColor(team));
        label.setTextSize(12);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(dp(42), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout spots = new LinearLayout(this);
        spots.setGravity(Gravity.CENTER_VERTICAL);
        spots.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(spots, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            FrameLayout spot = new FrameLayout(this);
            spot.setBackgroundResource(R.drawable.shape_wait_spot);
            spot.setClipChildren(false);
            spot.setClipToPadding(false);
            LinearLayout.LayoutParams spotParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            spotParams.setMargins(0, 0, dp(5), 0);
            spots.addView(spot, spotParams);
            waitSpots[team][id] = spot;
        }
    }

    private void rebuildCurrentWaitingArea() {
        waitingArea.removeAllViews();
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                waitSpots[team][id] = null;
            }
        }

        if (game.isGameOver()) {
            return;
        }

        createWaitingTeamRow(game.getCurrentTeam());
        syncWaitingPiecesForCurrentTurn();
    }

    private void syncWaitingPiecesForCurrentTurn() {
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                Piece piece = game.getPiece(team, id);
                if (team >= game.getTeamCount()
                        || piece.isFinished
                        || piece.position != BoardPath.START_NODE) {
                    continue;
                }

                if (team == game.getCurrentTeam()) {
                    moveToWaitSpot(team, id);
                } else {
                    removeFromParent(pieceViews[team][id]);
                }
            }
        }
    }

    private void handleYutInput(int steps) {
        if (isAnimatingMove) {
            showToast("\ub9d0\uc774 \uc774\ub3d9 \uc911\uc785\ub2c8\ub2e4.");
            return;
        }

        YutGameEngine.ActionResult result = game.addRoll(steps);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        selectSinglePendingResult();
        textStatus.setText(getRollStatusMessage(steps, result.message));
        textStatus.setTextColor(getStatusColor());
        if (game.isRollAllowed()) {
            addBonusTime();
        }
        addTurnLog(YutGameEngine.getTeamName(game.getCurrentTeam()) + ": "
                + YutGameEngine.getResultName(steps) + " \uc785\ub825");
        playFeedback(GameFeedback.TAP);
        updateResultButtons();
        updateMovePreviews();
        persistGameState();
    }

    private void undoLastRoll() {
        if (isAnimatingMove) {
            showToast("\ub9d0\uc774 \uc774\ub3d9 \uc911\uc785\ub2c8\ub2e4.");
            return;
        }

        YutGameEngine.ActionResult result = game.undoLastRoll();
        if (!result.success) {
            showToast(result.message);
            return;
        }

        selectSinglePendingResult();
        textStatus.setText(result.message);
        textStatus.setTextColor(getStatusColor());
        addTurnLog(YutGameEngine.getTeamName(game.getCurrentTeam()) + ": \ub9c8\uc9c0\ub9c9 \uc737 \uacb0\uacfc \ucde8\uc18c");
        updateResultButtons();
        updateMovePreviews();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private void selectSinglePendingResult() {
        if (game.getPendingResults().size() == 1 && game.getSelectedSteps().isEmpty()) {
            game.selectResult(0);
        }
    }

    private String getRollStatusMessage(int steps, String fallbackMessage) {
        if (game.getPendingResults().size() != 1 || game.getSelectedSteps().isEmpty()) {
            return fallbackMessage;
        }

        String nextAction = game.isRollAllowed()
                ? "\ub354 \ub358\uc9c0\uac70\ub098 \ub9d0\uc744 \uace0\ub974\uba74 \ub3c4\ucc29\uc9c0\uac00 \ud45c\uc2dc\ub429\ub2c8\ub2e4."
                : "\ub9d0\uc744 \uace0\ub974\uba74 \ub3c4\ucc29\uc9c0\uac00 \ud45c\uc2dc\ub429\ub2c8\ub2e4.";
        return YutGameEngine.getResultName(steps) + "\uc774 \ub098\uc654\uc2b5\ub2c8\ub2e4. " + nextAction;
    }

    private void endCurrentTurn() {
        if (isAnimatingMove) {
            showToast("\ub9d0\uc774 \uc774\ub3d9 \uc911\uc785\ub2c8\ub2e4.");
            return;
        }

        String endedTeamName = YutGameEngine.getTeamName(game.getCurrentTeam());
        YutGameEngine.ActionResult result = game.endTurn();
        if (!result.success) {
            showToast(result.message);
            return;
        }

        textStatus.setText(result.message);
        textStatus.setTextColor(getStatusColor());
        addTurnLog(endedTeamName + ": \ud134 \uc885\ub8cc");
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        rebuildCurrentWaitingArea();
        startTurnTimer();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private void updateResultButtons() {
        resultLayout.removeAllViews();
        List<YutGameEngine.MoveChoice> moveChoices = game.getMoveChoices();

        for (int i = 0; i < moveChoices.size(); i++) {
            YutGameEngine.MoveChoice choice = moveChoices.get(i);
            Button button = new Button(this);
            button.setText(choice.label);
            button.setAllCaps(false);
            button.setTextColor(getResources().getColor(R.color.text_primary));
            button.setTextSize(moveChoices.size() > 1 ? 13 : 15);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setBackgroundResource(R.drawable.shape_result_button);
            button.setBackgroundTintList(null);
            button.setStateListAnimator(null);
            button.setSelected(choice.selectionOrder > 0);
            button.setEnabled(!isAnimatingMove);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(moveChoices.size() > 1 ? 82 : 68), dp(38));
            params.setMargins(0, 0, dp(6), 0);
            button.setLayoutParams(params);

            final int index = i;
            button.setOnClickListener(v -> runPressAction(v, () -> onResultClick(index)));
            resultLayout.addView(button);
            button.setScaleX(0.94f);
            button.setScaleY(0.94f);
            button.setAlpha(0.8f);
            button.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
        }
    }

    private void onResultClick(int index) {
        if (isAnimatingMove) {
            showToast("\ub9d0\uc774 \uc774\ub3d9 \uc911\uc785\ub2c8\ub2e4.");
            return;
        }

        YutGameEngine.ActionResult result = game.selectResult(index);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        textStatus.setText(result.message);
        textStatus.setTextColor(getStatusColor());
        updateResultButtons();
        updateMovePreviews();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private void selectPiece(int teamId, int pieceId) {
        if (isAnimatingMove) {
            showToast("\ub9d0\uc774 \uc774\ub3d9 \uc911\uc785\ub2c8\ub2e4.");
            return;
        }

        if (teamId != game.getCurrentTeam()) {
            showToast("\uc9c0\uae08\uc740 " + YutGameEngine.getTeamName(game.getCurrentTeam()) + " \ucc28\ub840\uc785\ub2c8\ub2e4.");
            return;
        }

        if (game.getSelectedSteps().isEmpty()) {
            selectedPreviewTeamId = teamId;
            selectedPreviewPieceId = pieceId;
            updatePieceSelectionStyles();
            clearMovePreviews();
            textStatus.setText("\uc774 \ub9d0\uc5d0 \uc0ac\uc6a9\ud560 \uc737 \uacb0\uacfc\ub97c \uc21c\uc11c\ub300\ub85c \uc120\ud0dd\ud558\uc138\uc694.");
            textStatus.setTextColor(getStatusColor());
            persistGameState();
            return;
        }

        if (selectedPreviewTeamId != teamId || selectedPreviewPieceId != pieceId) {
            selectedPreviewTeamId = teamId;
            selectedPreviewPieceId = pieceId;
            updatePieceSelectionStyles();
            updateMovePreviews();
            textStatus.setText("\ub3c4\ucc29\uc9c0\ub97c \ud655\uc778\ud588\uc2b5\ub2c8\ub2e4. \uac19\uc740 \ub9d0\uc774\ub098 \ud654\uc0b4\ud45c\ub97c \ub204\ub974\uba74 \uc774\ub3d9\ud569\ub2c8\ub2e4.");
            textStatus.setTextColor(getStatusColor());
            playFeedback(GameFeedback.TAP);
            persistGameState();
            return;
        }

        commitSelectedMove();
    }

    private void commitSelectedMove() {
        if (isAnimatingMove
                || selectedPreviewTeamId != game.getCurrentTeam()
                || selectedPreviewPieceId == -1) {
            return;
        }

        String planText = getSelectedStepsText();
        YutGameEngine.MoveResult result = game.moveSelectedPiece(selectedPreviewTeamId, selectedPreviewPieceId);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        applyMoveResult(result, planText);
    }

    private void applyMoveResult(YutGameEngine.MoveResult result, String planText) {
        playFeedback(GameFeedback.MOVE);
        isAnimatingMove = true;
        isTimerHeldForAnimation = true;
        setControlsEnabled(false);
        clearSelectedPiece();
        clearMovePreviews();

        animateMoveResult(result, () -> {
            for (YutGameEngine.PieceRef caughtPiece : result.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }

            for (int id : result.finishedPieceIds) {
                moveToFinishedArea(result.teamId, id);
            }

            isAnimatingMove = false;
            isTimerHeldForAnimation = false;
            rebuildCurrentWaitingArea();
            updateResultButtons();
            setControlsEnabled(true);
            updateStatusAfterMove(result);
            addTurnLog(formatMoveLog(result, planText));
            if (result.gameWon) {
                playFeedback(GameFeedback.WIN);
                setControlsEnabled(false);
                findViewById(R.id.btn_restart).setEnabled(true);
                persistGameState();
                showGameOverDialog(result);
                return;
            }
            if (result.turnChanged) {
                startTurnTimer();
            } else if (result.caught) {
                playFeedback(GameFeedback.CATCH);
                addBonusTime();
            }
            updateMovePreviews();
            persistGameState();
        });
    }

    private void updateStatusAfterMove(YutGameEngine.MoveResult result) {
        textStatus.setText(result.message);
        textStatus.setTextColor(result.gameWon ? getResources().getColor(R.color.btn_restart) : getStatusColor());

        if (result.gameWon) {
            textStatus.animate().cancel();
            textStatus.setScaleX(1.08f);
            textStatus.setScaleY(1.08f);
            return;
        }

        if (result.turnChanged) {
            textStatus.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(180)
                    .withEndAction(() -> textStatus.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180)
                            .start())
                    .start();
        }
    }

    private void showGameOverDialog(YutGameEngine.MoveResult result) {
        YutDialogs.showGameOver(
                this,
                YutGameEngine.getTeamName(result.teamId) + " \uc2b9\ub9ac!",
                buildGameOverMessage(result),
                this::showTeamSetup,
                this::showTurnLogDialog);
    }

    private void animateMoveResult(YutGameEngine.MoveResult result, Runnable onComplete) {
        if (result.animationSegments.isEmpty()) {
            onComplete.run();
            return;
        }

        int animationGeneration = layoutGeneration;
        boardContainer.post(() -> {
            if (animationGeneration == layoutGeneration) {
                animateMoveSegment(result, 0, onComplete, animationGeneration);
            }
        });
    }

    private void animateMoveSegment(YutGameEngine.MoveResult result, int segmentIndex, Runnable onComplete, int animationGeneration) {
        if (animationGeneration != layoutGeneration) {
            return;
        }
        if (segmentIndex >= result.animationSegments.size()) {
            onComplete.run();
            return;
        }

        YutGameEngine.MoveAnimation segment = result.animationSegments.get(segmentIndex);
        ArrayList<Integer> boardPath = new ArrayList<>();
        for (int node : segment.path) {
            if (node != BoardPath.START_NODE && node != BoardPath.END_NODE) {
                boardPath.add(node);
            }
        }

        Runnable nextSegment = () -> {
            if (animationGeneration != layoutGeneration) {
                return;
            }
            for (YutGameEngine.PieceRef caughtPiece : segment.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }
            animateMoveSegment(result, segmentIndex + 1, onComplete, animationGeneration);
        };
        if (segment.pieceIds.isEmpty() || boardPath.isEmpty()) {
            nextSegment.run();
            return;
        }

        int size = getBoardPieceSize();
        for (int id : segment.pieceIds) {
            TextView pieceView = pieceViews[result.teamId][id];
            moveViewToParent(pieceView, boardContainer);
            pieceView.animate().cancel();
            pieceView.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            placePieceOnBoardNode(pieceView, segment.startNode, id, size);
        }
        animatePathStep(result.teamId, segment.pieceIds, boardPath, 0, nextSegment, animationGeneration);
    }

    private void animatePathStep(int teamId, List<Integer> pieceIds, List<Integer> path, int index, Runnable onComplete, int animationGeneration) {
        if (animationGeneration != layoutGeneration) {
            return;
        }
        if (index >= path.size()) {
            bounceArrivedPieces(teamId, pieceIds, () -> {
                if (animationGeneration == layoutGeneration) {
                    onComplete.run();
                }
            });
            return;
        }

        int node = path.get(index);
        int duration = Math.max(110, 190 - Math.min(index, 4) * 12);
        Runnable nextStep = () -> animatePathStep(teamId, pieceIds, path, index + 1, onComplete, animationGeneration);

        for (int i = 0; i < pieceIds.size(); i++) {
            int pieceId = pieceIds.get(i);
            TextView pieceView = pieceViews[teamId][pieceId];
            float[] target = getBoardNodePosition(node, pieceId, getBoardPieceSize());
            pieceView.animate()
                    .x(target[0])
                    .y(target[1])
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .setDuration(duration)
                    .withEndAction(i == pieceIds.size() - 1 ? nextStep : null)
                    .start();
        }
    }

    private void movePieceUI(int teamId, int pieceId, int logicalNode) {
        if (logicalNode == BoardPath.START_NODE || logicalNode == BoardPath.END_NODE) {
            return;
        }

        TextView pieceView = pieceViews[teamId][pieceId];
        moveViewToParent(pieceView, boardContainer);

        boardContainer.post(() -> {
            int size = getBoardPieceSize();
            pieceView.setLayoutParams(new FrameLayout.LayoutParams(size, size));

            float[] target = getBoardNodePosition(logicalNode, pieceId, size);
            pieceView.animate().x(target[0]).y(target[1]).setDuration(220).start();
        });
    }

    private void placePieceOnBoardNode(View pieceView, int logicalNode, int pieceId, int size) {
        float[] position = getBoardNodePosition(logicalNode, pieceId, size);
        pieceView.setX(position[0]);
        pieceView.setY(position[1]);
    }

    private float[] getBoardNodePosition(int logicalNode, int pieceId, int size) {
        int spotIndex = logicalNode == BoardPath.START_NODE ? 15 : game.visualSpotFor(logicalNode);
        if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
            spotIndex = 15;
        }
        float[] point = BoardGeometry.POINTS[spotIndex];
        float x = point[0] * boardContainer.getWidth() - (size / 2f) + getBoardStackOffsetX(pieceId);
        float y = point[1] * boardContainer.getHeight() - (size / 2f) + getBoardStackOffsetY(pieceId);
        return new float[]{x, y};
    }

    private void updateMovePreviews() {
        clearMovePreviews();
        if (game.getSelectedSteps().isEmpty()
                || game.isGameOver()
                || selectedPreviewTeamId != game.getCurrentTeam()
                || selectedPreviewPieceId == -1) {
            return;
        }

        boardContainer.post(() -> {
            YutGameEngine.MovePreview preview = game.previewMove(selectedPreviewTeamId, selectedPreviewPieceId);
            if (!preview.available) {
                return;
            }
            int spotIndex = preview.finishes
                    ? game.visualSpotFor(15)
                    : preview.targetNode == BoardPath.START_NODE ? 15 : game.visualSpotFor(preview.targetNode);
            if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
                return;
            }
            addPreviewGlow(selectedPreviewPieceId, spotIndex);
        });
    }

    private void addPreviewGlow(int pieceId, int spotIndex) {
        TextView glow = new TextView(this);
        glow.setText("\u2193");
        glow.setGravity(Gravity.CENTER);
        glow.setTextColor(getResources().getColor(R.color.text_primary));
        glow.setTextSize(17);
        glow.setTypeface(Typeface.DEFAULT_BOLD);
        glow.setBackground(createPreviewDrawable());
        glow.setElevation(dp(8));
        glow.setClickable(true);
        glow.setFocusable(true);
        glow.setOnClickListener(v -> runPressAction(v, this::commitSelectedMove));

        int size = Math.max(dp(38), getBoardPieceSize() + dp(10));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        boardContainer.addView(glow, params);

        float[] point = BoardGeometry.POINTS[spotIndex];
        float x = point[0] * boardContainer.getWidth() - (size / 2f) + getBoardStackOffsetX(pieceId);
        float y = point[1] * boardContainer.getHeight() - (size / 2f) + getBoardStackOffsetY(pieceId);
        glow.setX(x);
        glow.setY(y);
        previewViews.add(glow);
    }

    private void clearMovePreviews() {
        for (View previewView : previewViews) {
            ViewGroup parent = (ViewGroup) previewView.getParent();
            if (parent != null) {
                parent.removeView(previewView);
            }
        }
        previewViews.clear();
    }

    private void clearSelectedPiece() {
        selectedPreviewTeamId = -1;
        selectedPreviewPieceId = -1;
        updatePieceSelectionStyles();
    }

    private void updatePieceSelectionStyles() {
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                TextView pieceView = pieceViews[team][id];
                boolean selected = team == selectedPreviewTeamId && id == selectedPreviewPieceId;
                pieceView.setScaleX(selected ? 1.12f : 1f);
                pieceView.setScaleY(selected ? 1.12f : 1f);
                pieceView.setElevation(dp(selected ? 10 : 7));
            }
        }
    }

    private void moveToWaitSpot(int teamId, int pieceId) {
        TextView pieceView = pieceViews[teamId][pieceId];
        FrameLayout waitSpot = waitSpots[teamId][pieceId];
        if (waitSpot == null) {
            removeFromParent(pieceView);
            return;
        }
        resetPieceViewTransform(pieceView);
        moveViewToParent(pieceView, waitSpot);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER);
        pieceView.setLayoutParams(params);
        resetPieceViewTransform(pieceView);
    }

    private void moveToFinishedArea(int teamId, int pieceId) {
        TextView pieceView = pieceViews[teamId][pieceId];
        LinearLayout finishedLayout = finishedLayouts[teamId];
        resetPieceViewTransform(pieceView);
        moveViewToParent(pieceView, finishedLayout);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(28), dp(28));
        params.setMargins(0, 0, dp(4), 0);
        params.gravity = Gravity.CENTER_VERTICAL;
        pieceView.setLayoutParams(params);
        resetPieceViewTransform(pieceView);
    }

    private void moveViewToParent(View view, ViewGroup newParent) {
        ViewGroup oldParent = (ViewGroup) view.getParent();
        if (oldParent == newParent) {
            return;
        }
        if (oldParent != null) {
            oldParent.removeView(view);
        }
        newParent.addView(view);
    }

    private GradientDrawable createPieceDrawable(int teamId) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColors(new int[]{
                getResources().getColor(getTeamBrightColorRes(teamId)),
                getResources().getColor(getTeamColorRes(teamId))
        });
        drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        drawable.setGradientRadius(dp(34));
        drawable.setStroke(dp(2), Color.WHITE);
        return drawable;
    }

    private GradientDrawable createPreviewDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xEAF8C74E);
        drawable.setStroke(dp(2), 0xFFFFFFFF);
        return drawable;
    }

    private int getBoardPieceSize() {
        int byBoard = Math.round(boardContainer.getWidth() * 0.10f);
        return Math.max(dp(32), Math.min(dp(46), byBoard));
    }

    private float getBoardStackOffsetX(int pieceId) {
        return ((pieceId % 2) - 0.5f) * dp(7);
    }

    private float getBoardStackOffsetY(int pieceId) {
        return ((pieceId / 2) - 0.5f) * dp(7);
    }

    private int getStatusColor() {
        return getResources().getColor(getTeamColorRes(game.getCurrentTeam()));
    }

    private int getTeamColor(int teamId) {
        return getResources().getColor(getTeamColorRes(teamId));
    }

    private int getTeamColorRes(int teamId) {
        switch (teamId) {
            case 0:
                return R.color.team1_dark;
            case 1:
                return R.color.team2_dark;
            case 2:
                return R.color.team3_dark;
            case 3:
                return R.color.team4_dark;
            default:
                return R.color.text_primary;
        }
    }

    private int getTeamBrightColorRes(int teamId) {
        switch (teamId) {
            case 0:
                return R.color.team1_bright;
            case 1:
                return R.color.team2_bright;
            case 2:
                return R.color.team3_dark;
            case 3:
                return R.color.team4_dark;
            default:
                return R.color.text_primary;
        }
    }

    private boolean hasLandscapeDrawers() {
        return btnToggleInfo != null && btnToggleControls != null;
    }

    private void prepareLandscapeDrawers(boolean resetToDefault) {
        if (!hasLandscapeDrawers()) {
            return;
        }
        if (resetToDefault) {
            landscapeInfoOpen = false;
            landscapeControlsOpen = true;
        }
        setLandscapeDrawerTabsVisible(true);
        topPanel.bringToFront();
        controlPanel.bringToFront();
        btnToggleInfo.bringToFront();
        btnToggleControls.bringToFront();
        setupPanel.bringToFront();
        topPanel.post(() -> applyLandscapeDrawerState(false));
        controlPanel.post(() -> applyLandscapeDrawerState(false));
    }

    private void setLandscapeDrawerTabsVisible(boolean visible) {
        if (!hasLandscapeDrawers()) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.GONE;
        btnToggleInfo.setVisibility(visibility);
        btnToggleControls.setVisibility(visibility);
    }

    private void applyLandscapeDrawerState(boolean animate) {
        if (!hasLandscapeDrawers()) {
            return;
        }

        int leftWidth = Math.max(topPanel.getWidth(), dp(226));
        int rightWidth = Math.max(controlPanel.getWidth(), dp(292));
        float infoX = landscapeInfoOpen ? 0f : -leftWidth;
        float controlsX = landscapeControlsOpen ? 0f : rightWidth;
        float infoTabX = landscapeInfoOpen ? leftWidth : 0f;
        float controlsTabX = landscapeControlsOpen ? -rightWidth : 0f;

        updateDrawerTabLabel((TextView) btnToggleInfo, landscapeInfoOpen ? "\ub2eb\uae30" : "\uc815\ubcf4");
        updateDrawerTabLabel((TextView) btnToggleControls, landscapeControlsOpen ? "\ub2eb\uae30" : "\uc785\ub825");

        if (animate) {
            topPanel.animate().translationX(infoX).setDuration(180).start();
            controlPanel.animate().translationX(controlsX).setDuration(180).start();
            btnToggleInfo.animate().translationX(infoTabX).setDuration(180).start();
            btnToggleControls.animate().translationX(controlsTabX).setDuration(180).start();
            return;
        }

        topPanel.animate().cancel();
        controlPanel.animate().cancel();
        btnToggleInfo.animate().cancel();
        btnToggleControls.animate().cancel();
        topPanel.setTranslationX(infoX);
        controlPanel.setTranslationX(controlsX);
        btnToggleInfo.setTranslationX(infoTabX);
        btnToggleControls.setTranslationX(controlsTabX);
    }

    private void updateDrawerTabLabel(TextView tab, String text) {
        tab.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void bindBackExitHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPressMillis <= BACK_EXIT_INTERVAL_MILLIS) {
                    finish();
                    return;
                }
                lastBackPressMillis = now;
                showToast("\ud55c \ubc88 \ub354 \ub204\ub974\uba74 \uac8c\uc784\uc774 \uc885\ub8cc\ub429\ub2c8\ub2e4.");
                enterImmersiveMode();
            }
        });
    }

    private void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void bindPressAction(int viewId, Runnable action) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setOnClickListener(v -> runPressAction(v, action));
    }

    private void runPressAction(View view, Runnable action) {
        if (!view.isEnabled()) {
            return;
        }

        view.animate().cancel();
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(55)
                .withEndAction(() -> {
                    action.run();
                    if (view.getParent() != null) {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    }
                })
                .start();
    }

    private void bounceArrivedPieces(int teamId, List<Integer> pieceIds, Runnable onComplete) {
        if (pieceIds.isEmpty()) {
            onComplete.run();
            return;
        }

        for (int i = 0; i < pieceIds.size(); i++) {
            TextView pieceView = pieceViews[teamId][pieceIds.get(i)];
            boolean last = i == pieceIds.size() - 1;
            pieceView.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(75)
                    .withEndAction(() -> pieceView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .withEndAction(last ? onComplete : null)
                            .start())
                    .start();
        }
    }

    private void setControlsEnabled(boolean enabled) {
        setEnabledIfPresent(R.id.btn_bdo, enabled);
        setEnabledIfPresent(R.id.btn_do, enabled);
        setEnabledIfPresent(R.id.btn_gae, enabled);
        setEnabledIfPresent(R.id.btn_geol, enabled);
        setEnabledIfPresent(R.id.btn_yut, enabled);
        setEnabledIfPresent(R.id.btn_mo, enabled);
        setEnabledIfPresent(R.id.btn_undo_roll, enabled);
        setEnabledIfPresent(R.id.btn_restart, enabled);

        for (int i = 0; i < resultLayout.getChildCount(); i++) {
            resultLayout.getChildAt(i).setEnabled(enabled);
        }
        setEnabledIfPresent(R.id.btn_end_turn, enabled);
        btnTimeStop.setEnabled(enabled && turnDurationMillis > 0L);
    }

    private void setEnabledIfPresent(int viewId, boolean enabled) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setEnabled(enabled);
        }
    }

    private void removeFromParent(View view) {
        ViewGroup oldParent = (ViewGroup) view.getParent();
        if (oldParent != null) {
            oldParent.removeView(view);
        }
    }

    private void removeAllPiecesFromScreen() {
        if (pieceViews == null) {
            return;
        }

        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                removeFromParent(pieceViews[team][id]);
            }
        }
    }

    private void resetPieceViewTransform(View view) {
        view.animate().cancel();
        view.setX(0f);
        view.setY(0f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setAlpha(1f);
    }

    private void addTurnLog(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        turnLog.add(0, message);
        while (turnLog.size() > MAX_TURN_LOG_ENTRIES) {
            turnLog.remove(turnLog.size() - 1);
        }
        updateTurnLogView();
    }

    private void restoreTurnLog(String[] entries) {
        turnLog.clear();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && !entry.isEmpty()) {
                    turnLog.add(entry);
                }
            }
        }
        while (turnLog.size() > MAX_TURN_LOG_ENTRIES) {
            turnLog.remove(turnLog.size() - 1);
        }
        updateTurnLogView();
    }

    private void updateTurnLogView() {
        updateActionLogRail();
    }

    private void updateActionLogRail() {
        if (actionLogRail == null) {
            return;
        }
        actionLogRail.removeAllViews();

        int maxVisible = Math.min(turnLog.size(), 5);
        if (maxVisible == 0) {
            ImageView empty = createActionLogItem(R.drawable.ic_log_history, getResources().getColor(R.color.text_secondary));
            empty.setContentDescription("\uae30\ub85d \uc5c6\uc74c");
            empty.setOnClickListener(v -> showTurnLogDialog());
            actionLogRail.addView(empty);
            return;
        }

        for (int i = 0; i < maxVisible; i++) {
            String entry = turnLog.get(i);
            ImageView item = createActionLogItem(getActionLogIconRes(entry), getActionLogColor(entry));
            item.setContentDescription(entry);
            final int index = i;
            item.setOnClickListener(v -> showTurnLogEntryDialog(index));
            actionLogRail.addView(item);
            if (i == 0) {
                item.setScaleX(0.86f);
                item.setScaleY(0.86f);
                item.setAlpha(0.55f);
                item.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
            }
        }
    }

    private ImageView createActionLogItem(int iconResId, int color) {
        ImageView item = new ImageView(this);
        item.setBackground(createActionLogDrawable(color));
        item.setImageResource(iconResId);
        item.setColorFilter(Color.WHITE);
        item.setScaleType(ImageView.ScaleType.CENTER);
        item.setPadding(dp(8), dp(8), dp(8), dp(8));
        item.setElevation(dp(7));
        item.setClickable(true);
        item.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
        params.setMargins(0, dp(3), 0, dp(3));
        item.setLayoutParams(params);
        return item;
    }

    private GradientDrawable createActionLogDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(dp(2), 0xFFFFFFFF);
        return drawable;
    }

    private int getActionLogIconRes(String entry) {
        if (entry == null) {
            return R.drawable.ic_log_history;
        }
        if (entry.contains("\uc2b9\ub9ac")) {
            return R.drawable.ic_log_finish;
        }
        if (entry.contains("\uc644\uc8fc")) {
            return R.drawable.ic_log_finish;
        }
        if (entry.contains("\uc7a1\uc74c")) {
            return R.drawable.ic_log_capture;
        }
        if (entry.contains("\uc774\ub3d9")) {
            return R.drawable.ic_log_move;
        }
        if (entry.contains("\ucde8\uc18c")) {
            return R.drawable.ic_log_undo;
        }
        if (entry.contains("\ud134 \uc885\ub8cc")) {
            return R.drawable.ic_log_turn_end;
        }
        if (entry.contains("\uc0c8 \uac8c\uc784")) {
            return R.drawable.ic_log_start;
        }
        String[] resultNames = {
                "\ube7d\ub3c4",
                "\ub3c4",
                "\uac1c",
                "\uac78",
                "\uc737",
                "\ubaa8"
        };
        for (String name : resultNames) {
            if (entry.contains(name + " \uc785\ub825")) {
                return R.drawable.ic_log_yut;
            }
        }
        return R.drawable.ic_log_history;
    }

    private int getActionLogColor(String entry) {
        if (entry != null) {
            int teamId = parseTeamId(entry);
            if (teamId >= 0) {
                return getTeamColor(teamId);
            }
            if (entry.contains("\uc2b9\ub9ac") || entry.contains("\uc644\uc8fc")) {
                return getResources().getColor(R.color.accent_gold);
            }
            if (entry.contains("\ucde8\uc18c")) {
                return getResources().getColor(R.color.text_secondary);
            }
        }
        return getResources().getColor(R.color.text_primary);
    }

    private int parseTeamId(String entry) {
        if (entry == null || entry.length() < 2) {
            return -1;
        }
        int teamMarker = entry.indexOf("\ud300");
        if (teamMarker <= 0) {
            return -1;
        }
        try {
            int teamNumber = Integer.parseInt(entry.substring(0, teamMarker).trim());
            int teamId = teamNumber - 1;
            return teamId >= 0 && teamId < game.getTeamCount() ? teamId : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void showTurnLogDialog() {
        beginTimerDialogHold();
        YutDialogs.showTurnLog(
                this,
                new ArrayList<>(turnLog),
                this::clearTurnLog,
                this::endTimerDialogHold);
    }

    private void showTurnLogEntryDialog(int index) {
        if (index < 0 || index >= turnLog.size()) {
            showTurnLogDialog();
            return;
        }
        beginTimerDialogHold();
        YutDialogs.showTurnLogEntry(
                this,
                index + 1,
                turnLog.get(index),
                this::showTurnLogDialog,
                this::endTimerDialogHold);
    }

    private String[] getTurnLogSnapshot() {
        return turnLog.toArray(new String[0]);
    }

    private String getSelectedStepsText() {
        List<Integer> steps = game.getSelectedSteps();
        if (steps.isEmpty()) {
            return "\uc774\ub3d9";
        }
        ArrayList<String> names = new ArrayList<>();
        for (int step : steps) {
            names.add(YutGameEngine.getResultName(step));
        }
        return String.join("+", names);
    }

    private String formatMoveLog(YutGameEngine.MoveResult result, String planText) {
        StringBuilder builder = new StringBuilder();
        builder.append(YutGameEngine.getTeamName(result.teamId))
                .append(": ")
                .append(formatPieceIds(result.usedPieceIds))
                .append(" ")
                .append(planText)
                .append(" \uc774\ub3d9");
        if (!result.caughtPieces.isEmpty()) {
            builder.append(", \uc0c1\ub300 ")
                    .append(result.caughtPieces.size())
                    .append("\uac1c \uc7a1\uc74c");
        }
        if (!result.finishedPieceIds.isEmpty()) {
            builder.append(", ")
                    .append(result.finishedPieceIds.size())
                    .append("\uac1c \uc644\uc8fc");
        }
        if (result.gameWon) {
            builder.append(", \uc2b9\ub9ac");
        }
        return builder.toString();
    }

    private String formatPieceIds(List<Integer> pieceIds) {
        if (pieceIds == null || pieceIds.isEmpty()) {
            return "\ub9d0";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pieceIds.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(pieceIds.get(i) + 1);
        }
        builder.append("\ubc88 \ub9d0");
        return builder.toString();
    }

    private String buildGameOverMessage(YutGameEngine.MoveResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("\ubaa8\ub4e0 \ub9d0\uc774 \uc644\uc8fc\ud588\uc2b5\ub2c8\ub2e4.\n\n")
                .append("\uc2b9\ub9ac \ud300: ")
                .append(YutGameEngine.getTeamName(result.teamId))
                .append('\n')
                .append("\uc644\uc8fc: ")
                .append(countFinishedPieces(result.teamId))
                .append("/")
                .append(YutGameEngine.PIECE_COUNT)
                .append('\n')
                .append("\ucd5c\uadfc \uae30\ub85d: ")
                .append(turnLog.size())
                .append("\uac1c");

        if (!turnLog.isEmpty()) {
            builder.append("\n\n\ub9c8\uc9c0\ub9c9 \uc9c4\ud589\n")
                    .append(YutDialogs.buildTurnLogText(turnLog, Math.min(4, turnLog.size())));
        }

        return builder.toString();
    }

    private int countFinishedPieces(int teamId) {
        int count = 0;
        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            if (game.getPiece(teamId, id).isFinished) {
                count++;
            }
        }
        return count;
    }

    private void loadSettings() {
        GameStateStore.Settings settings = stateStore.loadSettings();
        turnDurationMillis = settings.turnDurationMillis;
        remainingTurnMillis = turnDurationMillis;
        feedback.setEnabled(settings.soundEnabled, settings.vibrationEnabled);
    }

    private void showSettingsDialog() {
        beginTimerDialogHold();
        YutDialogs.showSettings(
                this,
                new YutDialogs.SettingsState(
                        getTurnDurationIndex(),
                        feedback.isSoundEnabled(),
                        feedback.isVibrationEnabled(),
                        turnLog.size()),
                new YutDialogs.SettingsActions() {
                    @Override
                    public void onApply(int turnIndex, boolean soundEnabled, boolean vibrationEnabled) {
                        applySettings(
                                GameStateStore.TURN_DURATION_OPTIONS_MILLIS[turnIndex],
                                soundEnabled,
                                vibrationEnabled);
                    }

                    @Override
                    public void onClearLog() {
                        clearTurnLog();
                    }

                    @Override
                    public void onShowLog() {
                        showTurnLogDialog();
                    }
                },
                this::endTimerDialogHold);
    }

    private void beginTimerDialogHold() {
        syncTimerToNow();
        timerDialogHoldCount++;
        updateTimerView();
    }

    private void endTimerDialogHold() {
        if (timerDialogHoldCount > 0) {
            timerDialogHoldCount--;
        }
        timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        persistGameState();
    }

    private void applySettings(long selectedTurnDurationMillis, boolean soundEnabled, boolean vibrationEnabled) {
        boolean durationChanged = turnDurationMillis != selectedTurnDurationMillis;
        boolean keepPaused = isTimerPaused;
        turnDurationMillis = selectedTurnDurationMillis;
        feedback.setEnabled(soundEnabled, vibrationEnabled);

        if (durationChanged) {
            if (gameStarted && setupPanel.getVisibility() != View.VISIBLE && !game.isGameOver()) {
                remainingTurnMillis = turnDurationMillis;
                timeExpiredNotified = false;
                isTimerPaused = keepPaused;
                timerCheckpointEpochMillis = System.currentTimeMillis();
                updateTimerView();
            } else {
                remainingTurnMillis = turnDurationMillis;
                updateTimerView();
            }
        } else {
            updateTimerView();
        }

        persistGameState();
        playFeedback(GameFeedback.TAP);
        showToast("\uc124\uc815\uc744 \uc800\uc7a5\ud588\uc2b5\ub2c8\ub2e4.");
    }

    private void clearTurnLog() {
        turnLog.clear();
        updateTurnLogView();
        persistGameState();
        playFeedback(GameFeedback.TAP);
        showToast("\uae30\ub85d\uc744 \ucd08\uae30\ud654\ud588\uc2b5\ub2c8\ub2e4.");
    }

    private int getTurnDurationIndex() {
        return stateStore.getTurnDurationIndex(turnDurationMillis);
    }

    private String getTurnDurationLabel() {
        return stateStore.getTurnDurationLabel(turnDurationMillis);
    }

    private boolean restorePersistedGameState() {
        GameStateStore.AppState appState = stateStore.restoreAppState(
                getResources().getColor(R.color.text_status));
        if (appState == null) {
            return false;
        }

        gameStarted = appState.gameStarted;
        game.restoreState(appState.engineState);
        remainingTurnMillis = turnDurationMillis <= 0L ? 0L : Math.max(0L, appState.remainingTurnMillis);
        isTimerPaused = appState.timerPaused;
        timeExpiredNotified = appState.timeExpiredNotified;
        timerCheckpointEpochMillis = appState.timerCheckpointEpochMillis;
        selectedPreviewTeamId = appState.selectedTeamId;
        selectedPreviewPieceId = appState.selectedPieceId;
        if (!isValidPreviewSelection(selectedPreviewTeamId, selectedPreviewPieceId)) {
            selectedPreviewTeamId = -1;
            selectedPreviewPieceId = -1;
        }
        restoredStatusMessage = appState.statusMessage;
        restoredStatusColor = appState.statusColor;
        restoreTurnLog(appState.turnLog);
        return true;
    }

    private boolean isValidPreviewSelection(int teamId, int pieceId) {
        return teamId >= 0
                && teamId < game.getTeamCount()
                && pieceId >= 0
                && pieceId < YutGameEngine.PIECE_COUNT
                && !game.getPiece(teamId, pieceId).isFinished;
    }

    private void persistGameState() {
        if (stateStore == null) {
            return;
        }
        syncTimerToNow();
        GameStateStore.Settings settings = new GameStateStore.Settings();
        settings.turnDurationMillis = turnDurationMillis;
        settings.soundEnabled = feedback != null && feedback.isSoundEnabled();
        settings.vibrationEnabled = feedback != null && feedback.isVibrationEnabled();

        GameStateStore.AppState appState = new GameStateStore.AppState();
        appState.gameStarted = gameStarted;
        appState.remainingTurnMillis = remainingTurnMillis;
        appState.timerCheckpointEpochMillis = timerCheckpointEpochMillis;
        appState.timerPaused = isTimerPaused;
        appState.timeExpiredNotified = timeExpiredNotified;
        appState.selectedTeamId = selectedPreviewTeamId;
        appState.selectedPieceId = selectedPreviewPieceId;
        appState.statusColor = getResources().getColor(R.color.text_status);
        if (textStatus != null) {
            appState.statusMessage = textStatus.getText().toString();
            appState.statusColor = textStatus.getCurrentTextColor();
        }
        appState.turnLog = getTurnLogSnapshot();
        appState.engineState = game.saveState();
        stateStore.save(settings, appState);
    }

    private void playFeedback(int type) {
        if (feedback != null) {
            feedback.play(type);
        }
    }

    private void startTurnTimer() {
        isTimerHeldForAnimation = false;
        remainingTurnMillis = turnDurationMillis;
        isTimerPaused = false;
        timeExpiredNotified = false;
        timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
    }

    private void addBonusTime() {
        if (game.isGameOver() || turnDurationMillis <= 0L) {
            return;
        }
        syncTimerToNow();
        remainingTurnMillis += BONUS_TURN_MILLIS;
        timeExpiredNotified = false;
        updateTimerView();
    }

    private void pauseTimer() {
        syncTimerToNow();
        isTimerPaused = true;
        updateTimerView();
    }

    private void toggleTimeStop() {
        if (game.isGameOver() || setupPanel.getVisibility() == View.VISIBLE || turnDurationMillis <= 0L) {
            return;
        }
        syncTimerToNow();
        isTimerPaused = !isTimerPaused;
        timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private boolean shouldTimerRun() {
        return gameStarted
                && !isTimerPaused
                && !isTimerHeldForAnimation
                && timerDialogHoldCount == 0
                && turnDurationMillis > 0L
                && !game.isGameOver();
    }

    private void syncTimerToNow() {
        long now = System.currentTimeMillis();
        if (timerCheckpointEpochMillis <= 0L) {
            timerCheckpointEpochMillis = now;
            return;
        }

        if (shouldTimerRun()) {
            long elapsed = Math.max(0L, now - timerCheckpointEpochMillis);
            remainingTurnMillis = Math.max(0L, remainingTurnMillis - elapsed);
        }
        timerCheckpointEpochMillis = now;
    }

    private void updateTimerView() {
        if (textTimer == null || btnTimeStop == null) {
            return;
        }

        if (turnDurationMillis <= 0L) {
            textTimer.setText("\ubb34\uc81c\ud55c");
            textTimer.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setText("\uc81c\ud55c \uc5c6\uc74c");
            resetPauseButtonStyle();
            btnTimeStop.setEnabled(false);
            return;
        }
        btnTimeStop.setEnabled(true);

        long seconds = (remainingTurnMillis + TIMER_TICK_MILLIS - 1L) / TIMER_TICK_MILLIS;
        long minutes = seconds / 60;
        long displaySeconds = seconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minutes, displaySeconds);

        if (isTimerPaused) {
            textTimer.setText("\uc77c\uc2dc\uc815\uc9c0  " + timeText);
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            btnTimeStop.setText("\uacc4\uc18d\ud558\uae30");
            btnTimeStop.setTextColor(getResources().getColor(R.color.ink_black));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_pause_active);
            btnTimeStop.setBackgroundTintList(null);
        } else if (timerDialogHoldCount > 0) {
            textTimer.setText("\uc124\uc815 \uc911  " + timeText);
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            btnTimeStop.setText("\uc2dc\uac04 \uc815\uc9c0");
            btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
            btnTimeStop.setBackgroundTintList(null);
        } else if (remainingTurnMillis <= 10_000L) {
            textTimer.setText(timeText);
            textTimer.setTextColor(getResources().getColor(R.color.btn_restart));
            btnTimeStop.setText("\uc2dc\uac04 \uc815\uc9c0");
            resetPauseButtonStyle();
        } else if (remainingTurnMillis <= 30_000L) {
            textTimer.setText(timeText);
            textTimer.setTextColor(getResources().getColor(R.color.preview_glow));
            btnTimeStop.setText("\uc2dc\uac04 \uc815\uc9c0");
            resetPauseButtonStyle();
        } else {
            textTimer.setText(timeText);
            textTimer.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setText("\uc2dc\uac04 \uc815\uc9c0");
            resetPauseButtonStyle();
        }
    }

    private void resetPauseButtonStyle() {
        btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
        btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
        btnTimeStop.setBackgroundTintList(null);
    }
}
