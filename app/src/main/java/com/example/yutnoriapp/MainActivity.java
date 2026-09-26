package com.example.yutnoriapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Configuration;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.TextViewCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final long BONUS_TURN_MILLIS = 30_000L;
    private static final long TIMER_TICK_MILLIS = 1_000L;
    private static final long BACK_EXIT_INTERVAL_MILLIS = 1_800L;
    private static final int MAX_TURN_LOG_ENTRIES = 200;
    private static final long UPDATE_CHECK_DELAY_MILLIS = 900L;
    private static final long UPDATE_RECHECK_INTERVAL_MILLIS = 10L * 60L * 1_000L;


    private final YutGameEngine game = new YutGameEngine(isKoreanLanguage());
    private GameStateStore stateStore;
    private GameFeedback feedback;

    private AppUpdateChecker appUpdateChecker;
    private PieceStackView[][] pieceViews;
    private FrameLayout[][] waitSpots;
    private LinearLayout[] teamProgressRows;
    private PieceStackView[][] teamProgressPieces;
    private TextView finishDestination;
    private FrameLayout boardContainer;
    private BoardOverlayLayout boardOverlay;
    private View boardArt;
    private View topPanel;
    private View controlPanel;
    private View btnToggleInfo;
    private View btnToggleControls;
    private TextView textStatus;
    private TextView textTimer;
    private TextView textSetupRuleSummary;
    private LinearLayout actionLogRail;
    private TextView btnTimeStop;
    private LinearLayout resultLayout;
    private LinearLayout finishedSummaryLayout;
    private LinearLayout waitingArea;
    private View setupPanel;
    private final ArrayList<View> previewViews = new ArrayList<>();
    private final ArrayList<View> pieceGuideViews = new ArrayList<>();
    private final ArrayList<String> turnLog = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (shouldTimerRun()) {
                syncTimerToNow();
                updateTimerView();
                finishTurnAfterTimeout();
            }
            timerHandler.postDelayed(this, TIMER_TICK_MILLIS);
        }
    };
    private int selectedPreviewTeamId = -1;
    private final Runnable updateCheckRunnable = this::checkForAppUpdate;
    private int selectedPreviewPieceId = -1;
    private boolean isAnimatingMove = false;
    private boolean isActivityResumed = false;
    private boolean gameOverDialogVisible = false;
    private boolean victoryPending = false;
    private int bonusTimeBaselineResultId = 0;
    private final ArrayList<Animator> moveAnimators = new ArrayList<>();
    private long turnDurationMillis = GameStateStore.DEFAULT_TURN_DURATION_MILLIS;
    private long remainingTurnMillis = GameStateStore.DEFAULT_TURN_DURATION_MILLIS;
    private long lastBackPressMillis = 0L;
    private boolean isTimerPaused = false;
    private boolean isTimerHeldForAnimation = false;
    private int timerDialogHoldCount = 0;
    private boolean timeExpiredNotified = false;
    private long timerCheckpointElapsedMillis = 0L;
    private boolean gameStarted = false;
    private boolean controlsPanelOpen = true;
    private boolean edgePanelTransitionRunning = false;
    private int edgePanelTransitionGeneration = 0;
    private String restoredStatusMessage = "";
    private int restoredStatusColor = Color.TRANSPARENT;
    private int layoutGeneration = 0;
    private int boardLayoutRefreshGeneration = 0;
    private int movePreviewGeneration = 0;
    private int pieceGuideGeneration = 0;

    private int pendingUpdateVersionCode = -1;
    private boolean updateDialogVisible = false;
    private boolean savedGameChoiceVisible = false;
    private boolean savedGameChoicePending = false;
    private long lastUpdateCheckElapsedMillis = Long.MIN_VALUE;
    private long restoredGameSavedAtEpochMillis = 0L;
    private GameStateStore.MoveUndoState moveUndoState;
    private int[] teamColors = TeamAppearance.legacyColors();
    private int[] teamShapes = TeamAppearance.legacyShapes();
    private AlertDialog teamAppearanceDialog;
    private int pendingTeamCount;
    private int[] draftTeamColors;
    private int[] draftTeamShapes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        stateStore = new GameStateStore(this);
        feedback = new GameFeedback(this);
        loadSettings();
        appUpdateChecker = new AppUpdateChecker(this);
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        bindBackExitHandler();
        boolean restoredGame = restorePersistedGameState();
        if (restoredGame) {
            restoreGameScreen(restoredStatusMessage, restoredStatusColor);
        } else {
            showTeamSetup();
            if (savedInstanceState != null) {
                int count = savedInstanceState.getInt("appearance_team_count", 0);
                if (count >= 2 && count <= YutGameEngine.MAX_TEAM_COUNT) {
                    showTeamAppearanceSetup(count,
                            TeamAppearance.restoreColors(savedInstanceState.getIntArray("appearance_colors"), count),
                            TeamAppearance.restoreShapes(savedInstanceState.getIntArray("appearance_shapes")));
                }
            }
        }
        savedGameChoicePending = restoredGame && GameStateStore.shouldConfirmSavedGame(
                restoredGameSavedAtEpochMillis,
                System.currentTimeMillis());
        if (savedGameChoicePending) {
            timerHandler.post(this::showSavedGameChoice);
        }
        timerHandler.post(timerTick);
        enterImmersiveMode();
    }

    private void bindViewsAndActions() {
        layoutGeneration++;
        boardLayoutRefreshGeneration++;
        movePreviewGeneration++;
        edgePanelTransitionGeneration++;
        edgePanelTransitionRunning = false;
        textStatus = findViewById(R.id.text_status);
        textTimer = findViewById(R.id.text_timer);
        textSetupRuleSummary = findViewById(R.id.text_setup_rule_summary);
        actionLogRail = findViewById(R.id.action_log_rail);
        if (actionLogRail != null) {
            actionLogRail.setOnClickListener(v -> showTurnLogDialog());
            actionLogRail.setClickable(true);
            actionLogRail.setFocusable(true);
        }
        btnTimeStop = findViewById(R.id.btn_time_stop);
        topPanel = findViewById(R.id.top_panel);
        boardContainer = findViewById(R.id.board_container);
        boardArt = findViewById(R.id.board_art);
        boardOverlay = findViewById(R.id.board_overlay);
        bindBoardResizeListener();
        controlPanel = findViewById(R.id.control_panel);
        btnToggleInfo = findViewById(R.id.btn_toggle_info);
        btnToggleControls = findViewById(R.id.btn_toggle_controls);
        resultLayout = findViewById(R.id.layout_results);
        finishedSummaryLayout = findViewById(R.id.layout_finished_summary);
        waitingArea = findViewById(R.id.waiting_area);
        setupPanel = findViewById(R.id.setup_panel);

        pieceViews = new PieceStackView[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
        waitSpots = new FrameLayout[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
        teamProgressRows = new LinearLayout[YutGameEngine.MAX_TEAM_COUNT];
        teamProgressPieces = new PieceStackView[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
        createFinishDestination();

        createPieceViews();
        bindYutButtons();
        bindTeamSetupButtons();
        updateSetupRuleSummary();
        bindEdgePanelButtons();
        applyButtonContrast();
        applyResponsiveSizing();
        applyCompactActionAutoSizing();
        applyFontScaleSizing();
        bindSafeAreaInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        // Leaving the app pauses a physical-board game automatically.
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        updateTimerView();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.post(timerTick);
        enterImmersiveMode();
        maybeShowPendingUpdate();
        timerHandler.removeCallbacks(updateCheckRunnable);
        timerHandler.postDelayed(updateCheckRunnable, UPDATE_CHECK_DELAY_MILLIS);
        maybeShowVictory();
    }

    @Override
    protected void onPause() {
        syncTimerToNow();
        isActivityResumed = false;
        cancelMovePresentation(true);
        persistGameState();
        getWindow().getDecorView().setKeepScreenOn(false);
        timerHandler.removeCallbacks(timerTick);
        timerHandler.removeCallbacks(updateCheckRunnable);
        super.onPause();
    }

    @Override
    protected void onStop() {
        persistGameState();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (teamAppearanceDialog != null && teamAppearanceDialog.isShowing()) {
            outState.putInt("appearance_team_count", pendingTeamCount);
            outState.putIntArray("appearance_colors", draftTeamColors.clone());
            outState.putIntArray("appearance_shapes", draftTeamShapes.clone());
        }
        persistGameState();
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
            maybeShowPendingUpdate();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        syncTimerToNow();
        boolean wasAnimatingMove = isAnimatingMove;
        cancelMovePresentation(false);
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
            restoreGameScreen(statusMessage, statusColor, wasAnimatingMove);
        } else {
            showTeamSetup();
        }
        enterImmersiveMode();
        boardOverlay.post(this::maybeShowVictory);
    }

    @Override
    protected void onDestroy() {
        isActivityResumed = false;
        if (teamAppearanceDialog != null) {
            teamAppearanceDialog.setOnDismissListener(null);
            teamAppearanceDialog.dismiss();
            teamAppearanceDialog = null;
        }
        cancelMovePresentation(false);
        timerHandler.removeCallbacks(timerTick);
        timerHandler.removeCallbacks(updateCheckRunnable);
        if (feedback != null) {
            feedback.release();
        }
        super.onDestroy();
    }

    private void createPieceViews() {
        for (int team = 0; team < YutGameEngine.TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                PieceStackView view = new PieceStackView(this);
                view.configureAppearance(teamColors[team], teamShapes[team], id + 1);
                view.setElevation(dp(7));
                view.setContentDescription(getString(R.string.piece_description, teamName(team), id + 1));

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
        bindPressAction(R.id.btn_undo_roll, this::undoLastAction);
        bindPressAction(R.id.btn_end_turn, this::requestEndCurrentTurn);
        bindPressAction(R.id.btn_time_stop, this::toggleTimeStop);
        bindPressAction(R.id.btn_settings, this::showSettingsDialog);
        bindPressAction(R.id.btn_setup_rules, this::showGameRulesDialog);
        bindPressAction(R.id.btn_setup_help, this::showHowToPlayDialog);
        bindPressAction(R.id.btn_restart, this::requestNewGame);

        setContentDescriptionIfPresent(R.id.btn_bdo, getString(R.string.yut_backdo_description));
        setContentDescriptionIfPresent(R.id.btn_do, getString(R.string.yut_do_description));
        setContentDescriptionIfPresent(R.id.btn_gae, getString(R.string.yut_gae_description));
        setContentDescriptionIfPresent(R.id.btn_geol, getString(R.string.yut_geol_description));
        setContentDescriptionIfPresent(R.id.btn_yut, getString(R.string.yut_yut_description));
        setContentDescriptionIfPresent(R.id.btn_mo, getString(R.string.yut_mo_description));
    }

    private void bindTeamSetupButtons() {
        bindPressAction(R.id.btn_team_2, () -> showTeamAppearanceSetup(2));
        bindPressAction(R.id.btn_team_3, () -> showTeamAppearanceSetup(3));
        bindPressAction(R.id.btn_team_4, () -> showTeamAppearanceSetup(4));
        configureTeamSetupButton(R.id.btn_team_2, R.string.team_two_detail, R.drawable.ic_teams_2);
        configureTeamSetupButton(R.id.btn_team_3, R.string.team_three_detail, R.drawable.ic_teams_3);
        configureTeamSetupButton(R.id.btn_team_4, R.string.team_four_detail, R.drawable.ic_teams_4);
        TextView versionButton = findViewById(R.id.btn_setup_version);
        if (versionButton != null) {
            versionButton.setText(getString(
                    R.string.app_version_update_format,
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE));
            bindPressAction(R.id.btn_setup_version, this::openPlayStoreListing);
        }
    }

    private void configureTeamSetupButton(int buttonId, int detailResId, int iconResId) {
        Button button = findViewById(buttonId);
        if (button == null) {
            return;
        }

        String title = button.getText().toString();
        String detail = getString(detailResId);
        SpannableStringBuilder label = new SpannableStringBuilder(title)
                .append('\n')
                .append(detail);
        int detailStart = title.length() + 1;
        label.setSpan(
                new RelativeSizeSpan(0.74f),
                detailStart,
                label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(
                new ForegroundColorSpan(getResources().getColor(R.color.text_secondary)),
                detailStart,
                label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        label.setSpan(
                new StyleSpan(Typeface.NORMAL),
                detailStart,
                label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(label);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(iconResId, 0, 0, 0);
        button.setContentDescription(title + ". " + detail);
    }
    private void checkForAppUpdate() {
        if (appUpdateChecker == null || isFinishing() || isDestroyed()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastUpdateCheckElapsedMillis != Long.MIN_VALUE
                && now - lastUpdateCheckElapsedMillis < UPDATE_RECHECK_INTERVAL_MILLIS) {
            return;
        }
        lastUpdateCheckElapsedMillis = now;
        try {
            appUpdateChecker.check(availableVersionCode -> {
                pendingUpdateVersionCode = availableVersionCode;
                maybeShowPendingUpdate();
            });
        } catch (RuntimeException ignored) {
            // Update checks must never block local play on devices without Google Play.
        }
    }

    private void maybeShowPendingUpdate() {
        if (pendingUpdateVersionCode <= BuildConfig.VERSION_CODE
                || updateDialogVisible
                || timerDialogHoldCount > 0
                || isAnimatingMove
                || !hasWindowFocus()
                || isFinishing()
                || isDestroyed()) {
            return;
        }

        updateDialogVisible = true;
        beginTimerDialogHold();
        try {
            YutDialogs.showUpdateAvailable(
                    this,
                    this::openPlayStoreListing,
                    () -> {
                        pendingUpdateVersionCode = -1;
                        updateDialogVisible = false;
                        endTimerDialogHold();
                    });
        } catch (RuntimeException ignored) {
            pendingUpdateVersionCode = -1;
            updateDialogVisible = false;
            endTimerDialogHold();
        }
    }

    private void openPlayStoreListing() {
        String packageName = getPackageName();
        Intent marketIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageName));
        marketIntent.setPackage("com.android.vending");
        try {
            startActivity(marketIntent);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Fall back to the web listing when the Play Store app is unavailable.
        }

        Intent webIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
        try {
            startActivity(webIntent);
        } catch (ActivityNotFoundException ignored) {
            showToast(getString(R.string.play_store_unavailable));
        }
    }

    private void bindEdgePanelButtons() {
        if (!hasEdgePanels()) {
            return;
        }
        btnToggleInfo.setVisibility(View.GONE);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                (TextView) btnToggleControls, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        bindPressAction(R.id.btn_toggle_controls, () -> {
            if (isAnimatingMove) {
                showToast(getString(R.string.piece_moving));
                return;
            }
            boolean opening = !controlsPanelOpen;
            controlsPanelOpen = opening;
            applyEdgePanelState(true);
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
        victoryPending = false;
        savedGameChoicePending = false;
        moveUndoState = null;
        isAnimatingMove = false;
        isTimerHeldForAnimation = false;
        pauseTimer();
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        finishedSummaryLayout.removeAllViews();
        waitingArea.removeAllViews();
        removeAllPiecesFromScreen();
        topPanel.setVisibility(View.GONE);
        boardContainer.setVisibility(View.INVISIBLE);
        controlPanel.setVisibility(View.GONE);
        setEdgePanelTabsVisible(false);
        setupPanel.setVisibility(View.VISIBLE);
        setupPanel.setElevation(dp(24));
        setupPanel.bringToFront();
        updateSetupRuleSummary();
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

    private void showSavedGameChoice() {
        if (!gameStarted || isFinishing() || isDestroyed()) {
            savedGameChoicePending = false;
            return;
        }
        savedGameChoiceVisible = true;
        beginTimerDialogHold();
        YutDialogs.showSavedGameChoice(
                this,
                () -> {
                    savedGameChoiceVisible = false;
                    savedGameChoicePending = false;
                    restoredGameSavedAtEpochMillis = 0L;
                    endTimerDialogHold();
                },
                () -> {
                    savedGameChoiceVisible = false;
                    savedGameChoicePending = false;
                    restoredGameSavedAtEpochMillis = 0L;
                    timerDialogHoldCount = 0;
                    showTeamSetup();
                    maybeShowPendingUpdate();
                });
    }

    boolean isSavedGameChoiceVisible() {
        return savedGameChoiceVisible;
    }

    private void showTeamAppearanceSetup(int teamCount) {
        showTeamAppearanceSetup(teamCount, TeamAppearance.recommendedColors(), TeamAppearance.recommendedShapes());
    }

    private void showTeamAppearanceSetup(int teamCount, int[] colors, int[] shapes) {
        if (teamAppearanceDialog != null) return;
        pendingTeamCount = teamCount;
        draftTeamColors = colors;
        draftTeamShapes = shapes;
        beginTimerDialogHold();
        teamAppearanceDialog = TeamAppearanceDialog.show(this, teamCount, colors, shapes,
                (selectedColors, selectedShapes) -> {
                    teamColors = selectedColors;
                    teamShapes = selectedShapes;
                    applyTeamAppearance();
                    startGame(teamCount);
                },
                () -> {
                    teamAppearanceDialog = null;
                    pendingTeamCount = 0;
                    endTimerDialogHold();
                });
    }

    private void applyTeamAppearance() {
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                pieceViews[team][id].configureAppearance(teamColors[team], teamShapes[team], id + 1);
            }
        }
    }

    private void startGame(int teamCount) {
        YutGameEngine.ActionResult result = game.setTeamCount(teamCount);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        gameStarted = true;
        moveUndoState = null;
        setupPanel.setVisibility(View.GONE);
        boardContainer.setVisibility(View.VISIBLE);
        prepareEdgePanels(true);
        resetGame();
        persistGameState();
        enterImmersiveMode();
    }

    private void restoreGameScreen(String statusMessage, int statusColor) {
        restoreGameScreen(statusMessage, statusColor, true);
    }

    private void restoreGameScreen(
            String statusMessage,
            int statusColor,
            boolean syncPanelState) {
        isTimerHeldForAnimation = false;
        setupPanel.setVisibility(View.GONE);
        boardContainer.setVisibility(View.VISIBLE);
        setEdgePanelTabsVisible(true);
        resultLayout.removeAllViews();
        buildTeamAreas();

        boardOverlay.post(() -> {
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
                    }
                }
            }
            normalizeSelectedBoardPiece();
            renderBoardPiecesNow();
            updateFinishedSummary();
            updatePieceSelectionStyles();
            updateMovePreviews();
        });

        if (statusMessage.isEmpty()) {
            textStatus.setText(getString(R.string.team_turn, teamName(game.getCurrentTeam())));
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
        prepareEdgePanels(false);
        if (syncPanelState
                && !isAnimatingMove
                && selectedPreviewPieceId == -1
                && !game.isGameOver()) {
            syncControlPanelForNextAction(false);
        }
    }

    private void resetGame() {
        bonusTimeBaselineResultId = 0;
        victoryPending = false;
        isAnimatingMove = false;
        isTimerHeldForAnimation = false;
        moveUndoState = null;
        game.reset();
        resultLayout.removeAllViews();
        buildTeamAreas();

        textStatus.setText(R.string.initial_status);
        textStatus.setTextColor(getStatusColor());
        textStatus.animate().cancel();
        textStatus.setScaleX(1f);
        textStatus.setScaleY(1f);
        clearSelectedPiece();
        clearMovePreviews();
        setControlsEnabled(true);
        startTurnTimer();
        turnLog.clear();
        addTurnLog(getString(R.string.log_new_game, teamName(0)));

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
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        waitingArea.removeAllViews();
        waitingArea.setOrientation(LinearLayout.VERTICAL);
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) teamProgressRows[team] = null;
        LinearLayout row = null;
        for (int team = 0; team < game.getTeamCount(); team++) {
            if (landscape || team % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                finishedSummaryLayout.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));
            }
            createFinishedTeamSummary(team, row);
        }
        updateFinishedSummary();
        rebuildCurrentWaitingArea();
    }

    private void createFinishedTeamSummary(int team, LinearLayout parent) {
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setPadding(dp(3), dp(1), dp(3), dp(1));
        summary.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        teamProgressRows[team] = summary;
        parent.addView(summary, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        TextView label = new TextView(this);
        label.setText(teamName(team));
        label.setTextColor(getTeamColor(team));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        summary.addView(label, new LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.MATCH_PARENT));
        for (int piece = 0; piece < YutGameEngine.PIECE_COUNT; piece++) {
            PieceStackView icon = new PieceStackView(this);
            icon.configureAppearance(teamColors[team], teamShapes[team], piece + 1);
            icon.setFinishedIndicator(false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            summary.addView(icon, new LinearLayout.LayoutParams(0, dp(26), 1f));
            teamProgressPieces[team][piece] = icon;
        }
    }

    private void updateFinishedSummary() {
        if (teamProgressRows == null) return;
        for (int team = 0; team < game.getTeamCount(); team++) {
            LinearLayout summary = teamProgressRows[team];
            if (summary == null) continue;
            int finishedCount = 0;
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                boolean finished = game.getPiece(team, id).isFinished;
                if (finished) finishedCount++;
                teamProgressPieces[team][id].setFinishedIndicator(finished);
            }
            summary.setBackgroundColor(team == game.getCurrentTeam()
                    ? getResources().getColor(R.color.accent_gold_soft) : Color.TRANSPARENT);
            summary.setContentDescription(getString(R.string.finished_summary_description,
                    teamName(team), finishedCount, YutGameEngine.PIECE_COUNT));
        }
    }

    private void createWaitingTeamRow(int team) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        waitingArea.addView(row, rowParams);

        boolean compact = usesCompactWaitingTray();
        TextView label = new TextView(this);
        label.setText(teamName(team));
        label.setTextColor(getTeamColor(team));
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                label,
                10, 13, 1,
                TypedValue.COMPLEX_UNIT_SP);
        row.addView(label, new LinearLayout.LayoutParams(
                dp(40),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout spots = new LinearLayout(this);
        spots.setGravity(Gravity.CENTER_VERTICAL);
        spots.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(spots, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            FrameLayout spot = new FrameLayout(this);
            spot.setBackgroundResource(R.drawable.shape_wait_spot_touch);
            spot.setClipChildren(false);
            spot.setClipToPadding(false);
            final int pieceId = id;
            spot.setContentDescription(getString(R.string.waiting_piece_description, teamName(team), id + 1));
            spot.setClickable(true);
            spot.setFocusable(true);
            spot.setOnClickListener(v -> selectPiece(team, pieceId));
            LinearLayout.LayoutParams spotParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            int spotMargin = 0;
            spotParams.setMargins(spotMargin, 0, spotMargin, 0);
            spots.addView(spot, spotParams);
            waitSpots[team][id] = spot;
        }
    }

    private void rebuildCurrentWaitingArea() {
        updateFinishedSummary();
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
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        YutGameEngine.ActionResult result = game.addRoll(steps);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        moveUndoState = null;
        selectSinglePendingResult();
        textStatus.setText(getRollStatusMessage(steps, result.message));
        textStatus.setTextColor(getStatusColor());
        if (steps == 4 || steps == 5) {
            addBonusTime();
        }
        addTurnLog(getString(R.string.log_roll, teamName(game.getCurrentTeam()), resultName(steps)));
        playFeedback(GameFeedback.TAP);
        updateResultButtons();
        updatePieceSelectionStyles(true);
        updateMovePreviews();
        persistGameState();
    }

    private void undoLastAction() {
        if (moveUndoState != null) {
            undoLastMove();
            return;
        }
        undoLastRoll();
    }

    private void undoLastRoll() {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        List<Integer> before = game.getPendingResults();
        int removedSteps = before.isEmpty() ? 0 : before.get(before.size() - 1);
        List<YutGameEngine.MoveChoice> choices = game.getMoveChoices();
        int removedId = choices.isEmpty() ? 0 : choices.get(choices.size() - 1).resultId;
        YutGameEngine.ActionResult result = game.undoLastRoll();
        if (!result.success) {
            showToast(result.message);
            return;
        }

        if ((removedSteps == 4 || removedSteps == 5) && turnDurationMillis > 0L
                && removedId > bonusTimeBaselineResultId) {
            syncTimerToNow();
            remainingTurnMillis = Math.max(0L, remainingTurnMillis - BONUS_TURN_MILLIS);
            updateTimerView();
        }
        selectSinglePendingResult();
        textStatus.setText(result.message);
        textStatus.setTextColor(getStatusColor());
        addTurnLog(getString(R.string.log_undo_roll, teamName(game.getCurrentTeam())));
        updateResultButtons();
        updatePieceSelectionStyles(true);
        updateMovePreviews();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private void undoLastMove() {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (moveUndoState == null) {
            return;
        }

        GameStateStore.MoveUndoState state = moveUndoState;
        moveUndoState = null;
        game.restoreState(state.engineState);
        remainingTurnMillis = turnDurationMillis <= 0L
                ? 0L
                : Math.max(0L, state.remainingTurnMillis);
        isTimerPaused = state.timerPaused;
        timeExpiredNotified = state.timeExpiredNotified;
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        selectedPreviewTeamId = state.selectedTeamId;
        selectedPreviewPieceId = state.selectedPieceId;
        if (!isValidPreviewSelection(selectedPreviewTeamId, selectedPreviewPieceId)) {
            selectedPreviewTeamId = -1;
            selectedPreviewPieceId = -1;
        }
        restoreTurnLog(state.turnLog);
        addTurnLog(getString(R.string.log_undo_move, teamName(game.getCurrentTeam())));
        restoreGameScreen(getString(R.string.move_undone), getStatusColor());
        if (selectedPreviewPieceId != -1) {
            focusBoardForDestinationSelection();
        }
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

        String stepLabel = steps > 0 ? "+" + steps : String.valueOf(steps);
        return getString(
                game.isRollAllowed() ? R.string.roll_status_bonus : R.string.roll_status_move,
                resultName(steps),
                stepLabel);
    }
    private void requestEndCurrentTurn() {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        int pendingResultCount = game.getPendingResults().size();
        if (pendingResultCount <= 0) {
            endCurrentTurn();
            return;
        }

        beginTimerDialogHold();
        YutDialogs.showEndTurnConfirmation(
                this,
                pendingResultCount,
                this::endCurrentTurn,
                this::endTimerDialogHold);
    }

    private void endCurrentTurn() {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        String endedTeamName = teamName(game.getCurrentTeam());
        YutGameEngine.ActionResult result = game.endTurn();
        if (!result.success) {
            showToast(result.message);
            return;
        }

        moveUndoState = null;
        textStatus.setText(result.message);
        textStatus.setTextColor(getStatusColor());
        addTurnLog(getString(R.string.log_turn_end, endedTeamName));
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        rebuildCurrentWaitingArea();
        updateRollInputAvailability();
        startTurnTimer();
        playFeedback(GameFeedback.TAP);
        syncControlPanelForNextAction(true);
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(moveChoices.size() > 1 ? 86 : 72), dp(44));
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
        updateRollInputAvailability();
        updateUndoActionAvailability();
    }

    private void onResultClick(int index) {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        YutGameEngine.ActionResult result = game.selectResult(index);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        boolean unavailableSelectedPiece = false;
        if (game.getSelectedSteps().isEmpty()) {
            clearSelectedPiece();
        } else if (selectedPreviewTeamId != -1
                && selectedPreviewPieceId != -1
                && !game.previewMove(selectedPreviewTeamId, selectedPreviewPieceId).available) {
            unavailableSelectedPiece = true;
            clearSelectedPiece();
        }

        textStatus.setText(unavailableSelectedPiece
                ? game.getWaitingPieceBackDoMessage()
                : result.message);
        textStatus.setTextColor(getStatusColor());
        updateResultButtons();
        updatePieceSelectionStyles(true);
        updateMovePreviews();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private void selectPiece(int teamId, int pieceId) {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        if (teamId != game.getCurrentTeam()) {
            showToast(getString(R.string.team_turn, teamName(game.getCurrentTeam())));
            return;
        }

        if (game.getSelectedSteps().isEmpty()) {
            clearSelectedPiece();
            clearMovePreviews();
            textStatus.setText(R.string.select_result_first);
            textStatus.setTextColor(getStatusColor());
            persistGameState();
            return;
        }
        YutGameEngine.MovePreview preview = game.previewMove(teamId, pieceId);
        if (!preview.available) {
            clearSelectedPiece();
            clearMovePreviews();
            textStatus.setText(game.getWaitingPieceBackDoMessage());
            textStatus.setTextColor(getStatusColor());
            playFeedback(GameFeedback.TAP);
            persistGameState();
            return;
        }


        boolean selectionChanged = selectedPreviewTeamId != teamId || selectedPreviewPieceId != pieceId;
        selectedPreviewTeamId = teamId;
        selectedPreviewPieceId = pieceId;
        updatePieceSelectionStyles();
        updateMovePreviews();
        textStatus.setText(R.string.tap_destination);
        textStatus.setTextColor(getStatusColor());
        if (selectionChanged) {
            playFeedback(GameFeedback.SELECT);
        }
        focusBoardForDestinationSelection();
        persistGameState();
    }

    private void commitSelectedMove() {
        if (isAnimatingMove
                || selectedPreviewTeamId != game.getCurrentTeam()
                || selectedPreviewPieceId == -1) {
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        String planText = getSelectedStepsText();
        GameStateStore.MoveUndoState undoCandidate = captureMoveUndoState();
        YutGameEngine.MoveResult result = game.moveSelectedPiece(selectedPreviewTeamId, selectedPreviewPieceId);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        moveUndoState = undoCandidate;
        applyMoveResult(result, planText);
    }

    private void applyMoveResult(YutGameEngine.MoveResult result, String planText) {
        // Commit all game effects before starting disposable presentation work.
        updateStatusAfterMove(result);
        addTurnLog(formatMoveLog(result, planText));
        if (result.turnChanged) {
            startTurnTimer();
        } else if (result.caught) {
            for (int i = 0; i < result.captureEventCount(); i++) addBonusTime();
        }
        victoryPending = result.gameWon;
        isAnimatingMove = true;
        isTimerHeldForAnimation = true;
        setControlsEnabled(false);
        clearSelectedPiece();
        clearMovePreviews();
        persistGameState();

        animateMoveResult(result, () -> {
            for (YutGameEngine.PieceRef caughtPiece : result.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }

            for (int id : result.finishedPieceIds) {
                moveToFinishedArea(result.teamId, id);
            }

            isAnimatingMove = false;
            isTimerHeldForAnimation = false;
            renderBoardPiecesNow();
            updateFinishedSummary();
            rebuildCurrentWaitingArea();
            updateResultButtons();
            setControlsEnabled(true);
            if (result.gameWon) {
                playFeedback(GameFeedback.WIN);
                setControlsEnabled(false);
                findViewById(R.id.btn_restart).setEnabled(true);
                updateKeepScreenOn();
                persistGameState();
                maybeShowVictory();
                return;
            }
            if (!result.caught) {
                playFeedback(GameFeedback.MOVE);
            }
            timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
            updatePieceSelectionStyles(true);
            updateMovePreviews();
            syncControlPanelForNextAction(true);
            persistGameState();
            maybeShowPendingUpdate();
        });
    }

    private void cancelMovePresentation(boolean redraw) {
        if (!isAnimatingMove) return;
        // Invalidate callbacks before cancellation can invoke any animator listener.
        layoutGeneration++;
        isAnimatingMove = false;
        for (Animator animator : new ArrayList<>(moveAnimators)) animator.cancel();
        moveAnimators.clear();
        if (pieceViews != null) {
            for (PieceStackView[] team : pieceViews) {
                for (PieceStackView piece : team) if (piece != null) piece.animate().cancel();
            }
        }
        isTimerHeldForAnimation = false;
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        if (redraw && boardOverlay != null && gameStarted) {
            restoreGameScreen(textStatus.getText().toString(), textStatus.getCurrentTextColor());
            syncControlPanelForNextAction(false);
        }
    }

    private void maybeShowVictory() {
        if (!isActivityResumed || !victoryPending || isAnimatingMove || gameOverDialogVisible
                || !gameStarted || !game.isGameOver() || isFinishing() || isDestroyed()) return;
        showGameOverDialog(YutGameEngine.MoveResult.success(game.getCurrentTeam(), 0));
    }

    private GameStateStore.MoveUndoState captureMoveUndoState() {
        syncTimerToNow();
        GameStateStore.MoveUndoState state = new GameStateStore.MoveUndoState();
        state.engineState = game.saveState();
        state.remainingTurnMillis = remainingTurnMillis;
        state.timerPaused = isTimerPaused;
        state.timeExpiredNotified = timeExpiredNotified;
        state.selectedTeamId = selectedPreviewTeamId;
        state.selectedPieceId = selectedPreviewPieceId;
        state.statusMessage = textStatus == null ? "" : textStatus.getText().toString();
        state.statusColor = textStatus == null
                ? getResources().getColor(R.color.text_status)
                : textStatus.getCurrentTextColor();
        state.turnLog = getTurnLogSnapshot();
        return state;
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
        gameOverDialogVisible = true;
        YutDialogs.showGameOver(
                this,
                getString(R.string.victory_title, teamName(result.teamId)),
                buildGameOverMessage(result),
                this::showTeamSetup,
                this::showTurnLogDialog,
                () -> {
                    gameOverDialogVisible = false;
                    victoryPending = false;
                    persistGameState();
                });
    }

    private void animateMoveResult(YutGameEngine.MoveResult result, Runnable onComplete) {
        if (result.animationSegments.isEmpty()) {
            onComplete.run();
            return;
        }

        int animationGeneration = layoutGeneration;
        boardOverlay.post(() -> {
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
            if (animationGeneration == layoutGeneration) {
                animateMoveSegment(result, segmentIndex + 1, onComplete, animationGeneration);
            }
        };
        if (segment.pieceIds.isEmpty() || boardPath.isEmpty()) {
            for (YutGameEngine.PieceRef caughtPiece : segment.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }
            nextSegment.run();
            return;
        }

        int representativeId = prepareAnimatedBoardGroup(result.teamId, segment.pieceIds, segment.startNode);
        PieceStackView movingView = pieceViews[result.teamId][representativeId];
        animateStackLayout(
                movingView,
                1f,
                segment.pieceIds.size() > 1 ? 130L : 60L,
                () -> animatePathStep(
                        result.teamId,
                        representativeId,
                        segment,
                        boardPath,
                        0,
                        nextSegment,
                        animationGeneration));
    }

    private void animatePathStep(
            int teamId,
            int pieceId,
            YutGameEngine.MoveAnimation segment,
            List<Integer> path,
            int index,
            Runnable onComplete,
            int animationGeneration) {
        if (animationGeneration != layoutGeneration) {
            return;
        }
        if (index >= path.size()) {
            int arrivalNode = path.get(path.size() - 1);
            Runnable showArrival = () -> animateArrivedBoardGroup(
                    teamId,
                    pieceId,
                    segment,
                    arrivalNode,
                    onComplete,
                    animationGeneration);
            if (segment.caughtPieces.isEmpty()) {
                showArrival.run();
            } else {
                animateCapturedPieces(segment.caughtPieces, showArrival, animationGeneration);
            }
            return;
        }

        int node = path.get(index);
        int duration = Math.max(110, 190 - Math.min(index, 4) * 12);
        Runnable nextStep = () -> animatePathStep(
                teamId,
                pieceId,
                segment,
                path,
                index + 1,
                onComplete,
                animationGeneration);
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        boardOverlay.releaseAnchor(pieceView);
        float[] target = getBoardNodePosition(node, getBoardPieceViewSize());
        pieceView.bringToFront();
        pieceView.animate()
                .x(target[0])
                .y(target[1])
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setDuration(duration)
                .withEndAction(nextStep)
                .start();
    }

    private int prepareAnimatedBoardGroup(int teamId, List<Integer> pieceIds, int logicalNode) {
        int representativeId = getRepresentativePieceId(pieceIds);
        for (int id : pieceIds) {
            if (id != representativeId) {
                removeFromParent(pieceViews[teamId][id]);
            }
        }

        PieceStackView representative = pieceViews[teamId][representativeId];
        resetPieceViewTransform(representative);
        moveViewToParent(representative, boardOverlay);
        configureBoardGroupAtNode(representative, teamId, representativeId, pieceIds.size(), logicalNode);
        int size = getBoardPieceViewSize();
        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        placePieceOnBoardNode(representative, logicalNode, size);
        representative.bringToFront();
        return representativeId;
    }

    private void animateArrivedBoardGroup(
            int teamId,
            int movingPieceId,
            YutGameEngine.MoveAnimation segment,
            int logicalNode,
            Runnable onComplete,
            int animationGeneration) {
        if (animationGeneration != layoutGeneration) {
            return;
        }
        List<Integer> arrivedIds = segment.arrivedPieceIds.isEmpty()
                ? segment.pieceIds
                : segment.arrivedPieceIds;
        int representativeId = getRepresentativePieceId(arrivedIds);
        PieceStackView representative = pieceViews[teamId][representativeId];
        for (int id : arrivedIds) {
            if (id != representativeId) {
                removeFromParent(pieceViews[teamId][id]);
            }
        }
        if (movingPieceId != representativeId) {
            removeFromParent(pieceViews[teamId][movingPieceId]);
        }

        resetPieceViewTransform(representative);
        moveViewToParent(representative, boardOverlay);
        configureBoardGroupAtNode(representative, teamId, representativeId, arrivedIds.size(), logicalNode);
        int size = getBoardPieceViewSize();
        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        placePieceOnBoardNode(representative, logicalNode, size);
        representative.setCollapseProgress(1f);
        representative.bringToFront();

        animateStackLayout(representative, 0f, arrivedIds.size() > 1 ? 160L : 70L, () -> {
            ArrayList<Integer> animatedPiece = new ArrayList<>();
            animatedPiece.add(representativeId);
            bounceArrivedPieces(teamId, animatedPiece, () -> {
                if (animationGeneration == layoutGeneration) {
                    onComplete.run();
                }
            });
        });
    }

    private void animateCapturedPieces(
            List<YutGameEngine.PieceRef> caughtPieces,
            Runnable onComplete,
            int animationGeneration) {
        int capturedTeamCount = 0;
        for (int team = 0; team < game.getTeamCount(); team++) {
            for (YutGameEngine.PieceRef caughtPiece : caughtPieces) {
                if (caughtPiece.teamId == team) {
                    capturedTeamCount++;
                    break;
                }
            }
        }
        if (capturedTeamCount == 0) {
            onComplete.run();
            return;
        }

        playFeedback(GameFeedback.CATCH);
        int[] remainingTeams = {capturedTeamCount};
        for (int team = 0; team < game.getTeamCount(); team++) {
            ArrayList<Integer> teamPieceIds = new ArrayList<>();
            for (YutGameEngine.PieceRef caughtPiece : caughtPieces) {
                if (caughtPiece.teamId == team) {
                    teamPieceIds.add(caughtPiece.pieceId);
                }
            }
            if (teamPieceIds.isEmpty()) {
                continue;
            }

            int capturedTeam = team;
            int representativeId = getRepresentativePieceId(teamPieceIds);
            PieceStackView capturedView = pieceViews[capturedTeam][representativeId];
            Runnable finishTeam = () -> {
                for (int id : teamPieceIds) {
                    moveToWaitSpot(capturedTeam, id);
                }
                remainingTeams[0]--;
                if (remainingTeams[0] == 0 && animationGeneration == layoutGeneration) {
                    onComplete.run();
                }
            };
            if (capturedView.getParent() != boardOverlay) {
                finishTeam.run();
                continue;
            }

            configureBoardGroupAppearance(
                    capturedView,
                    capturedTeam,
                    representativeId,
                    teamPieceIds.size());
            capturedView.bringToFront();
            animateStackLayout(capturedView, 1f, 100L, () -> {
                if (animationGeneration != layoutGeneration) return;
                boardOverlay.releaseAnchor(capturedView);
                float[] target = getBoardNodePosition(BoardPath.START_NODE, getBoardPieceViewSize());
                capturedView.animate()
                        .x(target[0])
                        .y(target[1])
                        .scaleX(0.62f)
                        .scaleY(0.62f)
                        .alpha(0f)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .setDuration(300L)
                        .withEndAction(finishTeam)
                        .start();
            });
        }
    }

    private void animateStackLayout(
            PieceStackView view,
            float targetProgress,
            long duration,
            Runnable onComplete) {
        float startProgress = view.getCollapseProgress();
        if (Math.abs(startProgress - targetProgress) < 0.001f) {
            onComplete.run();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(startProgress, targetProgress);
        moveAnimators.add(animator);
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(valueAnimator ->
                view.setCollapseProgress((float) valueAnimator.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                moveAnimators.remove(animator);
                if (!cancelled) {
                    view.setCollapseProgress(targetProgress);
                    onComplete.run();
                }
            }
        });
        animator.start();
    }

    private void renderBoardPiecesNow() {
        for (int team = 0; team < game.getTeamCount(); team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                Piece piece = game.getPiece(team, id);
                if (piece.isFinished
                        || piece.position == BoardPath.START_NODE
                        || piece.position == BoardPath.END_NODE) {
                    continue;
                }

                ArrayList<Integer> groupIds = getBoardGroupIds(team, piece.position);
                int representativeId = getRepresentativePieceId(groupIds);
                if (id != representativeId) {
                    removeFromParent(pieceViews[team][id]);
                    continue;
                }

                PieceStackView representative = pieceViews[team][representativeId];
                resetPieceViewTransform(representative);
                moveViewToParent(representative, boardOverlay);
                configureBoardGroupAtNode(representative, team, representativeId, groupIds.size(), piece.position);
                int size = getBoardPieceViewSize();
                representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
                placePieceOnBoardNode(representative, piece.position, size);
            }
        }
    }

    private ArrayList<Integer> getBoardGroupIds(int teamId, int logicalNode) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            Piece piece = game.getPiece(teamId, id);
            if (!piece.isFinished && game.isSameBoardSpot(piece.position, logicalNode)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private int getRepresentativePieceId(List<Integer> pieceIds) {
        int representativeId = pieceIds.get(0);
        for (int id : pieceIds) {
            representativeId = Math.min(representativeId, id);
        }
        return representativeId;
    }

    private void normalizeSelectedBoardPiece() {
        if (selectedPreviewTeamId < 0 || selectedPreviewPieceId < 0) {
            return;
        }
        Piece selected = game.getPiece(selectedPreviewTeamId, selectedPreviewPieceId);
        if (selected.isFinished || selected.position == BoardPath.START_NODE) {
            return;
        }
        selectedPreviewPieceId = getRepresentativePieceId(
                getBoardGroupIds(selectedPreviewTeamId, selected.position));
    }

    private void configureBoardGroupAtNode(
            PieceStackView view,
            int teamId,
            int pieceId,
            int groupCount,
            int logicalNode) {
        configureBoardGroupAppearance(view, teamId, pieceId, groupCount);
        int spotIndex = logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT
                : game.visualSpotFor(logicalNode);
        view.setArrangement(BoardGeometry.stackArrangementForSpot(spotIndex));
    }

    private void configureBoardGroupAppearance(PieceStackView view, int teamId, int pieceId, int groupCount) {
        view.setVisibility(View.VISIBLE);
        view.setGroupCount(groupCount);
        view.setVisualDiameterPx(getBoardPieceSize());
        view.setCollapseProgress(0f);
        view.setElevation(dp(groupCount > 1 ? 9 : 7));
        view.setContentDescription(groupCount > 1
                ? getString(R.string.grouped_piece_description, teamName(teamId), groupCount)
                : getString(R.string.piece_description, teamName(teamId), pieceId + 1));
    }

    private void restorePieceIdentity(PieceStackView view, int teamId, int pieceId) {
        view.setVisibility(View.VISIBLE);
        view.setGroupCount(1);
        view.setArrangement(PieceStackLayout.SPACIOUS);
        view.setVisualDiameterPx(dp(36));
        view.setCollapseProgress(0f);
        view.setElevation(dp(7));
        view.setContentDescription(getString(R.string.piece_description, teamName(teamId), pieceId + 1));
    }

    private void placePieceOnBoardNode(View pieceView, int logicalNode, int size) {
        boardOverlay.anchor(pieceView, logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT : game.visualSpotFor(logicalNode));
    }

    private float[] getBoardNodePosition(int logicalNode, int size) {
        int spotIndex = logicalNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT
                : game.visualSpotFor(logicalNode);
        if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
            spotIndex = BoardGeometry.START_SPOT;
        }
        float[] center = getBoardSpotCenter(spotIndex);
        return new float[]{center[0] - (size / 2f), center[1] - (size / 2f)};
    }

    private float[] getBoardSpotCenter(int spotIndex) {
        return boardOverlay.centerForSpot(spotIndex);
    }

    private void createFinishDestination() {
        finishDestination = new TextView(this);
        finishDestination.setId(R.id.finish_destination);
        finishDestination.setText(R.string.finish_destination);
        finishDestination.setContentDescription(getString(R.string.finish_destination_description));
        finishDestination.setGravity(Gravity.CENTER);
        finishDestination.setTextColor(getResources().getColor(R.color.text_primary));
        finishDestination.setTextSize(14);
        finishDestination.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable tile = new GradientDrawable();
        tile.setColor(getResources().getColor(R.color.accent_gold_soft));
        tile.setCornerRadius(dp(12));
        tile.setStroke(dp(2), getResources().getColor(R.color.board_direction));
        finishDestination.setBackground(tile);
        finishDestination.setElevation(dp(14));
        finishDestination.setVisibility(View.GONE);
        finishDestination.setClickable(true);
        finishDestination.setFocusable(true);
        finishDestination.setOnClickListener(v -> {
            if (selectedPreviewTeamId == game.getCurrentTeam() && selectedPreviewPieceId >= 0) {
                YutGameEngine.MovePreview preview = game.previewMove(selectedPreviewTeamId, selectedPreviewPieceId);
                if (preview.available && preview.finishes) runPressAction(v, this::commitSelectedMove);
            }
        });
        ConstraintLayout root = findViewById(R.id.root_layout);
        root.addView(finishDestination, new ConstraintLayout.LayoutParams(dp(64), dp(48)));
    }

    private void setFinishDestinationVisible(boolean visible) {
        if (finishDestination == null) return;
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (finishDestination.getVisibility() == visibility) return;
        finishDestination.setVisibility(visibility);
        if (gameStarted) applyBoardPanelConstraints(findViewById(R.id.root_layout));
        scheduleBoardLayoutRefresh();
    }

    private void updateMovePreviews() {
        clearMovePreviews(false);
        if (isAnimatingMove || game.getSelectedSteps().isEmpty() || game.isGameOver()
                || selectedPreviewTeamId != game.getCurrentTeam() || selectedPreviewPieceId == -1) {
            setFinishDestinationVisible(false);
            return;
        }
        YutGameEngine.MovePreview preview = game.previewMove(selectedPreviewTeamId, selectedPreviewPieceId);
        setFinishDestinationVisible(preview.available && preview.finishes);
        if (!preview.available || preview.finishes) return;
        int previewGeneration = movePreviewGeneration;
        int activeLayoutGeneration = layoutGeneration;
        int spotIndex = preview.targetNode == BoardPath.START_NODE
                ? BoardGeometry.START_SPOT : game.visualSpotFor(preview.targetNode);
        boardOverlay.post(() -> {
            if (previewGeneration != movePreviewGeneration || activeLayoutGeneration != layoutGeneration
                    || isAnimatingMove || game.isGameOver()) return;
            if (spotIndex >= 0 && spotIndex < BoardGeometry.POINTS.length) addPreviewGlow(spotIndex);
        });
    }

    private void addPreviewGlow(int spotIndex) {
        int size = Math.max(dp(48), getBoardPieceSize() + dp(10));
        int ringSize = size + dp(12);

        View ring = new View(this);
        ring.setBackground(createPreviewRingDrawable());
        ring.setElevation(dp(6));
        ring.setClickable(false);
        ring.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        boardOverlay.addView(ring, new FrameLayout.LayoutParams(ringSize, ringSize));
        placeOverlayAtSpot(ring, spotIndex, ringSize);
        previewViews.add(ring);

        ImageView glow = new ImageView(this);
        glow.setImageResource(R.drawable.ic_destination_arrow);
        glow.setColorFilter(getResources().getColor(R.color.text_primary));
        int arrowPadding = Math.max(dp(10), size / 4);
        glow.setPadding(arrowPadding, arrowPadding, arrowPadding, arrowPadding);
        glow.setBackground(createPreviewDrawable());
        glow.setElevation(dp(14));
        glow.setClickable(true);
        glow.setFocusable(true);
        glow.setContentDescription(getString(R.string.destination_description));
        glow.setOnClickListener(v -> runPressAction(v, this::commitSelectedMove));
        boardOverlay.addView(glow, new FrameLayout.LayoutParams(size, size));
        placeOverlayAtSpot(glow, spotIndex, size);
        previewViews.add(glow);

        DecelerateInterpolator interpolator = new DecelerateInterpolator();
        ring.setAlpha(0f);
        ring.setScaleX(0.62f);
        ring.setScaleY(0.62f);
        ring.animate()
                .alpha(0.72f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setInterpolator(interpolator)
                .setDuration(260)
                .withEndAction(() -> ring.animate()
                        .alpha(0.46f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180)
                        .start())
                .start();

        glow.setAlpha(0.35f);
        glow.setScaleX(0.78f);
        glow.setScaleY(0.78f);
        glow.animate()
                .alpha(1f)
                .scaleX(1.08f)
                .scaleY(1.08f)
                .setInterpolator(interpolator)
                .setDuration(190)
                .withEndAction(() -> glow.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
    }

    private void placeOverlayAtSpot(View view, int spotIndex, int size) {
        boardOverlay.anchor(view, spotIndex);
    }

    private void positionBoardChild(View view, float left, float top) {
        ViewGroup.LayoutParams currentParams = view.getLayoutParams();
        if (!(currentParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) currentParams;
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.round(left);
        params.topMargin = Math.round(top);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setLayoutParams(params);
    }

    private void clearMovePreviews() {
        clearMovePreviews(true);
    }

    private void clearMovePreviews(boolean hideFinish) {
        if (hideFinish) setFinishDestinationVisible(false);
        movePreviewGeneration++;
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
        updatePieceSelectionStyles(false);
    }

    private void updatePieceSelectionStyles(boolean animateGuidance) {
        if (pieceViews == null) {
            return;
        }

        clearPieceGuides();
        boolean showGuidance = shouldShowPieceGuidance();
        int currentTeam = game.getCurrentTeam();
        for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                PieceStackView pieceView = pieceViews[team][id];
                pieceView.animate().cancel();
                boolean selected = team == selectedPreviewTeamId && id == selectedPreviewPieceId;
                boolean legal = showGuidance
                        && team == currentTeam
                        && isPieceEligibleForGuidance(team, id);
                float scale = selected ? 1.12f : legal && !animateGuidance ? 1.03f : 1f;
                pieceView.setScaleX(scale);
                pieceView.setScaleY(scale);
                pieceView.setAlpha(showGuidance
                        && team == currentTeam
                        && pieceView.getParent() != null
                        && !legal ? 0.5f : 1f);
                pieceView.setElevation(dp(selected ? 10 : 7));
            }
        }

        if (!showGuidance) {
            return;
        }

        int guidanceGeneration = pieceGuideGeneration;
        boardOverlay.post(() -> {
            if (guidanceGeneration != pieceGuideGeneration || !shouldShowPieceGuidance()) {
                return;
            }
            addPieceGuides(animateGuidance);
        });
    }

    private boolean shouldShowPieceGuidance() {
        return gameStarted
                && !isAnimatingMove
                && !game.isGameOver()
                && !game.getSelectedSteps().isEmpty()
                && selectedPreviewTeamId == -1
                && selectedPreviewPieceId == -1;
    }

    private boolean isPieceEligibleForGuidance(int teamId, int pieceId) {
        if (teamId != game.getCurrentTeam()) {
            return false;
        }
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        if (pieceView.getParent() == null || game.getPiece(teamId, pieceId).isFinished) {
            return false;
        }
        return game.previewMove(teamId, pieceId).available;
    }

    private void addPieceGuides(boolean animateGuidance) {
        int teamId = game.getCurrentTeam();
        for (int pieceId = 0; pieceId < YutGameEngine.PIECE_COUNT; pieceId++) {
            if (!isPieceEligibleForGuidance(teamId, pieceId)) {
                continue;
            }

            PieceStackView pieceView = pieceViews[teamId][pieceId];
            ViewGroup parent = (ViewGroup) pieceView.getParent();
            View guide = new View(this);
            guide.setBackground(createPieceGuideDrawable(teamId));
            guide.setTag("piece_guide_" + teamId + "_" + pieceId);

            if (parent == boardOverlay) {
                int guideSize = Math.max(dp(52), getBoardPieceViewSize() + dp(4));
                guide.setElevation(dp(6));
                guide.setClickable(true);
                guide.setFocusable(true);
                guide.setContentDescription(getString(
                        R.string.selectable_piece_description,
                        teamName(teamId),
                        pieceId + 1));
                final int selectedPieceId = pieceId;
                guide.setOnClickListener(v -> selectPiece(teamId, selectedPieceId));
                boardOverlay.addView(guide, new FrameLayout.LayoutParams(guideSize, guideSize));
                placePieceGuideOnBoard(guide, game.getPiece(teamId, pieceId).position, guideSize);
            } else if (parent instanceof FrameLayout) {
                guide.setClickable(false);
                guide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                ((FrameLayout) parent).addView(
                        guide,
                        0,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                Gravity.CENTER));
                pieceView.bringToFront();
            } else {
                continue;
            }

            pieceGuideViews.add(guide);
            animatePieceGuide(guide, pieceView, animateGuidance);
        }
    }

    private void placePieceGuideOnBoard(View guide, int logicalNode, int size) {
        placePieceOnBoardNode(guide, logicalNode, size);
    }

    private void animatePieceGuide(View guide, View pieceView, boolean animateGuidance) {
        if (!animateGuidance) {
            guide.setAlpha(0.58f);
            guide.setScaleX(1f);
            guide.setScaleY(1f);
            pieceView.setScaleX(1.03f);
            pieceView.setScaleY(1.03f);
            return;
        }

        guide.setAlpha(0f);
        guide.setScaleX(0.72f);
        guide.setScaleY(0.72f);
        guide.animate()
                .alpha(0.66f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(230)
                .withEndAction(() -> guide.animate()
                        .alpha(0.58f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .start())
                .start();
        pieceView.setScaleX(0.96f);
        pieceView.setScaleY(0.96f);
        pieceView.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(190)
                .withEndAction(() -> pieceView.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(140)
                        .start())
                .start();
    }

    private void clearPieceGuides() {
        pieceGuideGeneration++;
        for (View guide : pieceGuideViews) {
            guide.animate().cancel();
            ViewGroup parent = (ViewGroup) guide.getParent();
            if (parent != null) {
                parent.removeView(guide);
            }
        }
        pieceGuideViews.clear();
    }

    private void moveToWaitSpot(int teamId, int pieceId) {
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        FrameLayout waitSpot = waitSpots[teamId][pieceId];
        if (waitSpot == null) {
            removeFromParent(pieceView);
            return;
        }
        resetPieceViewTransform(pieceView);
        restorePieceIdentity(pieceView, teamId, pieceId);
        moveViewToParent(pieceView, waitSpot);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER);
        pieceView.setLayoutParams(params);
        resetPieceViewTransform(pieceView);
    }

    private void moveToFinishedArea(int teamId, int pieceId) {
        PieceStackView pieceView = pieceViews[teamId][pieceId];
        resetPieceViewTransform(pieceView);
        removeFromParent(pieceView);
        updateFinishedSummary();
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

    private GradientDrawable createPreviewDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0xEAF8C74E);
        drawable.setStroke(dp(2), 0xFFFFFFFF);
        return drawable;
    }

    private GradientDrawable createPreviewRingDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x18F8C74E);
        drawable.setStroke(dp(3), 0xDDF8C74E);
        return drawable;
    }

    private GradientDrawable createPieceGuideDrawable(int teamId) {
        int teamColor = getTeamColor(teamId);
        int guideColor = Color.argb(
                220,
                Color.red(teamColor),
                Color.green(teamColor),
                Color.blue(teamColor));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x12FFFFFF);
        drawable.setStroke(dp(2), guideColor);
        return drawable;

    }
    private int getBoardPieceSize() {
        int boardWidth = boardArt == null ? 0 : boardArt.getWidth();
        int boardHeight = boardArt == null ? 0 : boardArt.getHeight();
        if (boardWidth <= 0 || boardHeight <= 0) {
            boardWidth = boardContainer.getWidth() - boardContainer.getPaddingLeft() - boardContainer.getPaddingRight();
            boardHeight = boardContainer.getHeight() - boardContainer.getPaddingTop() - boardContainer.getPaddingBottom();
        }
        int byBoard = Math.round(Math.min(boardWidth, boardHeight) * 0.105f);
        int desired = Math.max(dp(34), Math.min(dp(56), byBoard));
        return Math.max(1, Math.min(desired, Math.round(Math.min(boardWidth, boardHeight) * 0.14f)));
    }

    private int getBoardPieceViewSize() {
        return Math.max(dp(48), getBoardPieceSize() + dp(10));
    }

    private int getStatusColor() {
        return getTeamColor(game.getCurrentTeam());
    }

    private int getTeamColor(int teamId) {
        return TeamAppearance.label(teamColors[teamId]);
    }

    private boolean hasEdgePanels() {
        return btnToggleInfo != null && btnToggleControls != null;
    }

    private void bindBoardResizeListener() {
        View.OnLayoutChangeListener listener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            int oldWidth = oldRight - oldLeft;
            int oldHeight = oldBottom - oldTop;
            if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) {
                if (isAnimatingMove) {
                    cancelMovePresentation(false);
                    boardOverlay.post(() -> {
                        if (gameStarted) {
                            restoreGameScreen(textStatus.getText().toString(), textStatus.getCurrentTextColor());
                            syncControlPanelForNextAction(false);
                            maybeShowVictory();
                        }
                    });
                }
                scheduleBoardLayoutRefresh();
            }
        };
        boardArt.addOnLayoutChangeListener(listener);
        boardOverlay.addOnLayoutChangeListener(listener);
    }

    private void scheduleBoardLayoutRefresh() {
        if (boardOverlay == null) {
            return;
        }
        int refreshGeneration = ++boardLayoutRefreshGeneration;
        int activeLayoutGeneration = layoutGeneration;
        FrameLayout scheduledOverlay = boardOverlay;
        scheduledOverlay.postOnAnimation(() -> {
            if (refreshGeneration != boardLayoutRefreshGeneration
                    || activeLayoutGeneration != layoutGeneration
                    || scheduledOverlay != boardOverlay
                    || edgePanelTransitionRunning) {
                return;
            }
            refreshBoardAfterPanelLayout();
        });
    }

    private void focusBoardForDestinationSelection() {
        if (!hasEdgePanels() || !controlsPanelOpen) {
            return;
        }
        controlsPanelOpen = false;
        applyEdgePanelState(true);
    }

    private void syncControlPanelForNextAction(boolean animate) {
        if (!hasEdgePanels() || game.isGameOver()) {
            return;
        }
        boolean shouldOpen = game.isRollAllowed() || !game.getPendingResults().isEmpty();
        if (controlsPanelOpen == shouldOpen) {
            return;
        }
        controlsPanelOpen = shouldOpen;
        applyEdgePanelState(animate);
    }
    private void prepareEdgePanels(boolean resetToDefault) {
        if (!hasEdgePanels()) {
            return;
        }
        if (resetToDefault) {
            controlsPanelOpen = true;
        }
        setEdgePanelTabsVisible(true);
        applyEdgePanelState(false);
    }

    private void setEdgePanelTabsVisible(boolean visible) {
        if (!hasEdgePanels()) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.GONE;
        btnToggleInfo.setVisibility(View.GONE);
        btnToggleControls.setVisibility(visibility);
    }

    private void applyEdgePanelState(boolean animate) {
        if (!hasEdgePanels()) {
            return;
        }

        ViewGroup root = findViewById(R.id.root_layout);
        TransitionManager.endTransitions(root);
        int transitionGeneration = ++edgePanelTransitionGeneration;
        edgePanelTransitionRunning = animate;

        clearMovePreviews();
        clearPieceGuides();
        if (animate) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(220L);
            transition.setInterpolator(new AccelerateDecelerateInterpolator());
            TransitionManager.beginDelayedTransition(root, transition);
        }

        topPanel.setTranslationX(0f);
        topPanel.setTranslationY(0f);
        controlPanel.setTranslationX(0f);
        controlPanel.setTranslationY(0f);
        btnToggleInfo.setTranslationX(0f);
        btnToggleInfo.setTranslationY(0f);
        btnToggleControls.setTranslationX(0f);
        btnToggleControls.setTranslationY(0f);

        topPanel.setVisibility(View.VISIBLE);
        controlPanel.setVisibility(controlsPanelOpen ? View.VISIBLE : View.GONE);
        btnToggleInfo.setVisibility(View.GONE);
        btnToggleControls.setVisibility(View.VISIBLE);
        applyBoardPanelConstraints(root);
        updateDrawerTabLabel(
                (TextView) btnToggleControls,
                getString(controlsPanelOpen ? R.string.drawer_close : R.string.input));
        btnToggleControls.setSelected(controlsPanelOpen);

        topPanel.bringToFront();
        controlPanel.bringToFront();
        btnToggleInfo.bringToFront();
        btnToggleControls.bringToFront();
        setupPanel.bringToFront();

        long refreshDelay = animate ? 240L : 0L;
        boardContainer.postDelayed(() -> {
            if (transitionGeneration != edgePanelTransitionGeneration) {
                return;
            }
            edgePanelTransitionRunning = false;
            refreshBoardAfterPanelLayout();
        }, refreshDelay);
    }

    private void applyBoardPanelConstraints(ViewGroup root) {
        if (!(root instanceof ConstraintLayout)) return;
        ConstraintSet constraints = new ConstraintSet();
        constraints.clone((ConstraintLayout) root);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        clearPanelTabConstraints(constraints, R.id.btn_toggle_info);
        clearPanelTabConstraints(constraints, R.id.btn_toggle_controls);
        clearPanelTabConstraints(constraints, R.id.finish_destination);
        constraints.setVisibility(R.id.btn_toggle_info, View.GONE);
        constraints.setVisibility(R.id.top_panel, View.VISIBLE);
        int topId = landscape ? ConstraintSet.PARENT_ID : R.id.top_panel;
        int topSide = landscape ? ConstraintSet.TOP : ConstraintSet.BOTTOM;
        int bottomId = landscape ? ConstraintSet.PARENT_ID : R.id.btn_toggle_controls;
        int bottomSide = landscape ? ConstraintSet.BOTTOM : ConstraintSet.TOP;
        if (landscape) {
            constraints.connect(R.id.btn_toggle_controls, ConstraintSet.END, R.id.control_panel, ConstraintSet.START);
            centerTabVertically(constraints, R.id.btn_toggle_controls);
            constraints.connect(R.id.board_container, ConstraintSet.START, R.id.top_panel, ConstraintSet.END, dp(4));
            constraints.connect(R.id.board_container, ConstraintSet.END, R.id.btn_toggle_controls, ConstraintSet.START, dp(4));
        } else {
            constraints.connect(R.id.btn_toggle_controls, ConstraintSet.BOTTOM, R.id.control_panel, ConstraintSet.TOP);
            centerTabHorizontally(constraints, R.id.btn_toggle_controls);
        }
        // Keep the temporary finish tile immediately below the board, outside its touch layer.
        constraints.createVerticalChain(topId, topSide, bottomId, bottomSide,
                new int[]{R.id.board_container, R.id.finish_destination}, null, ConstraintSet.CHAIN_PACKED);
        constraints.connect(R.id.finish_destination, ConstraintSet.END, R.id.board_container, ConstraintSet.END);
        constraints.setMargin(R.id.finish_destination, ConstraintSet.TOP, dp(4));
        constraints.setGoneMargin(R.id.board_container, ConstraintSet.BOTTOM, 0);
        constraints.applyTo((ConstraintLayout) root);
        btnToggleControls.setBackgroundResource(landscape
                ? R.drawable.shape_drawer_tab_right : R.drawable.shape_drawer_tab_bottom);
    }

    private void clearPanelTabConstraints(ConstraintSet constraints, int viewId) {
        int[] anchors = {
                ConstraintSet.LEFT,
                ConstraintSet.RIGHT,
                ConstraintSet.START,
                ConstraintSet.END,
                ConstraintSet.TOP,
                ConstraintSet.BOTTOM
        };
        for (int anchor : anchors) {
            constraints.clear(viewId, anchor);
        }
    }

    private void centerTabHorizontally(ConstraintSet constraints, int viewId) {
        constraints.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        constraints.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
    }

    private void centerTabVertically(ConstraintSet constraints, int viewId) {
        constraints.connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        constraints.connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
    }

    private void refreshBoardAfterPanelLayout() {
        if (!gameStarted
                || isAnimatingMove
                || boardContainer.getVisibility() != View.VISIBLE
                || boardOverlay == null
                || boardOverlay.getWidth() <= 0
                || boardOverlay.getHeight() <= 0) {
            return;
        }
        renderBoardPiecesNow();
        updatePieceSelectionStyles(false);
        updateMovePreviews();
    }

    private void updateDrawerTabLabel(TextView tab, String text) {
        tab.setText(text);
    }
    private boolean usesCompactWaitingTray() {
        Configuration configuration = getResources().getConfiguration();
        int smallestWidth = configuration.smallestScreenWidthDp;
        if (smallestWidth > 0) {
            return smallestWidth < 600;
        }
        return Math.min(configuration.screenWidthDp, configuration.screenHeightDp) < 600;
    }

    private boolean isCompactPortrait() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                && configuration.screenWidthDp > 0
                && configuration.screenWidthDp < 600;
    }
    private void applyResponsiveSizing() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                && usesCompactWaitingTray()) {
            setViewWidthIfPresent(R.id.top_panel, 156);
            setViewWidthIfPresent(R.id.control_panel, 292);
            ((TextView) findViewById(R.id.text_title)).setTextSize(14);
            textStatus.setMaxLines(3);
            textStatus.setTextSize(12);
        }
        if (!isCompactPortrait()) {
            return;
        }

        View root = findViewById(R.id.root_layout);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        topPanel.setPadding(dp(10), dp(6), dp(10), dp(6));
        controlPanel.setPadding(dp(8), dp(6), dp(8), dp(7));
        setViewWidthIfPresent(R.id.action_log_rail, 48);

        TextView title = findViewById(R.id.text_title);
        title.setTextSize(18);
        textStatus.setMinHeight(dp(36));
        textStatus.setTextSize(14);
        textStatus.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                textStatus,
                11, 14, 1,
                TypedValue.COMPLEX_UNIT_SP);
        setViewHeightIfPresent(R.id.text_status, 52);
        textTimer.setTextSize(18);

        boolean largeText = getResources().getConfiguration().fontScale >= 1.2f;
        setViewHeightIfPresent(R.id.turn_tools, 44);
        setViewHeightIfPresent(R.id.results_row, 44);
        setViewHeightIfPresent(R.id.yut_row_top, largeText ? 52 : 48);
        setViewHeightIfPresent(R.id.yut_row_bottom, largeText ? 52 : 48);
    }

    private void applyCompactActionAutoSizing() {
        int[] buttonIds = {
                R.id.btn_time_stop,
                R.id.btn_end_turn,
                R.id.btn_undo_roll
        };
        for (int id : buttonIds) {
            TextView button = findViewById(id);
            if (button == null) {
                continue;
            }
            button.setMaxLines(1);
            button.setHorizontallyScrolling(false);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    button,
                    8, 13, 1,
                    TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private void applyFontScaleSizing() {
        float fontScale = getResources().getConfiguration().fontScale;
        if (fontScale < 1.3f) {
            return;
        }

        boolean extraLarge = fontScale >= 1.7f;
        int rulesHeight = extraLarge ? 92 : 76;
        int teamButtonHeight = extraLarge ? 92 : 78;
        int utilityHeight = extraLarge ? 54 : 48;
        setViewHeightIfPresent(R.id.btn_setup_rules, rulesHeight);
        setViewHeightIfPresent(R.id.btn_team_2, teamButtonHeight);
        setViewHeightIfPresent(R.id.btn_team_3, teamButtonHeight);
        setViewHeightIfPresent(R.id.btn_team_4, teamButtonHeight);
        setViewHeightIfPresent(R.id.btn_setup_help, utilityHeight);
        setViewHeightIfPresent(R.id.btn_setup_version, utilityHeight);
    }

    private void setViewHeightIfPresent(int viewId, int heightDp) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = dp(heightDp);
        view.setLayoutParams(params);
    }

    private void setViewWidthIfPresent(int viewId, int widthDp) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = dp(widthDp);
        view.setLayoutParams(params);
    }
    private String teamName(int teamId) {
        return game.getLocalizedTeamName(teamId);
    }

    private String resultName(int steps) {
        return game.getLocalizedResultName(steps);
    }

    private static boolean isKoreanLanguage() {
        return Locale.KOREAN.getLanguage().equals(Locale.getDefault().getLanguage());
    }
    private static String currentLanguageTag() {
        return isKoreanLanguage() ? "ko" : "en";
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
                showToast(getString(R.string.exit_confirm));
                enterImmersiveMode();
            }
        });
    }

    private void configureEdgeToEdgeWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    private void bindSafeAreaInsets() {
        View root = findViewById(R.id.root_layout);
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets mandatoryGestures = windowInsets.getInsets(
                    WindowInsetsCompat.Type.mandatorySystemGestures());
            int safeLeft = Math.max(cutout.left, mandatoryGestures.left);
            int safeTop = Math.max(cutout.top, mandatoryGestures.top);
            int safeRight = Math.max(cutout.right, mandatoryGestures.right);
            int safeBottom = Math.max(cutout.bottom, mandatoryGestures.bottom);
            int left = baseLeft + safeLeft;
            int top = baseTop + safeTop;
            int right = baseRight + safeRight;
            int bottom = baseBottom + safeBottom;
            if (view.getPaddingLeft() != left
                    || view.getPaddingTop() != top
                    || view.getPaddingRight() != right
                    || view.getPaddingBottom() != bottom) {
                view.setPadding(left, top, right, bottom);
                scheduleBoardLayoutRefresh();
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void enterImmersiveMode() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        View root = findViewById(R.id.root_layout);
        if (root != null) {
            ViewCompat.requestApplyInsets(root);
        }
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

        action.run();
        if (view.getParent() == null) return;
        view.animate().cancel();
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(55)
                .withEndAction(() -> {
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
            PieceStackView pieceView = pieceViews[teamId][pieceIds.get(i)];
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

    private void updateRollInputAvailability() {
        boolean enabled = gameStarted && !isAnimatingMove && !game.isGameOver();
        setEnabledIfPresent(R.id.btn_bdo, enabled && game.canAddRoll(-1));
        setEnabledIfPresent(R.id.btn_do, enabled && game.canAddRoll(1));
        setEnabledIfPresent(R.id.btn_gae, enabled && game.canAddRoll(2));
        setEnabledIfPresent(R.id.btn_geol, enabled && game.canAddRoll(3));
        setEnabledIfPresent(R.id.btn_yut, enabled && game.canAddRoll(4));
        setEnabledIfPresent(R.id.btn_mo, enabled && game.canAddRoll(5));
    }

    private void setControlsEnabled(boolean enabled) {
        setEnabledIfPresent(R.id.btn_bdo, enabled && game.canAddRoll(-1));
        setEnabledIfPresent(R.id.btn_do, enabled && game.canAddRoll(1));
        setEnabledIfPresent(R.id.btn_gae, enabled && game.canAddRoll(2));
        setEnabledIfPresent(R.id.btn_geol, enabled && game.canAddRoll(3));
        setEnabledIfPresent(R.id.btn_yut, enabled && game.canAddRoll(4));
        setEnabledIfPresent(R.id.btn_mo, enabled && game.canAddRoll(5));
        setEnabledIfPresent(R.id.btn_restart, enabled);

        for (int i = 0; i < resultLayout.getChildCount(); i++) {
            resultLayout.getChildAt(i).setEnabled(enabled);
        }
        setEnabledIfPresent(R.id.btn_end_turn, enabled);
        btnTimeStop.setEnabled(enabled && turnDurationMillis > 0L);
        updateUndoActionAvailability();
    }

    private void updateUndoActionAvailability() {
        TextView undoButton = findViewById(R.id.btn_undo_roll);
        if (undoButton == null) {
            return;
        }
        boolean canUndoMove = moveUndoState != null;
        boolean canUndoRoll = !game.isGameOver() && !game.getPendingResults().isEmpty();
        undoButton.setText(canUndoMove ? R.string.undo_move : R.string.undo_last);
        undoButton.setContentDescription(getString(
                canUndoMove ? R.string.undo_move : R.string.undo_last));
        undoButton.setEnabled(gameStarted
                && !isAnimatingMove
                && (canUndoMove || canUndoRoll));
    }

    private void setEnabledIfPresent(int viewId, boolean enabled) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setEnabled(enabled);
        }
    }

    private void setContentDescriptionIfPresent(int viewId, String description) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setContentDescription(description);
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
        if (view instanceof PieceStackView) {
            ((PieceStackView) view).setCollapseProgress(0f);
        }
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

        boolean hasEntry = !turnLog.isEmpty();
        ImageView item = createActionLogItem(
                hasEntry ? getActionLogIconRes(turnLog.get(0)) : R.drawable.ic_log_history,
                hasEntry ? getActionLogColor(turnLog.get(0)) : getResources().getColor(R.color.text_secondary));
        item.setContentDescription(hasEntry ? turnLog.get(0) : getString(R.string.history_none));
        item.setOnClickListener(v -> {
            if (hasEntry) {
                showTurnLogEntryDialog(0);
            } else {
                showTurnLogDialog();
            }
        });
        actionLogRail.addView(item);

        TextView label = new TextView(this);
        label.setText(R.string.history);
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                label,
                9, 11, 1,
                TypedValue.COMPLEX_UNIT_SP);
        if (usesCompactWaitingTray()
                || getResources().getConfiguration().fontScale >= 1.4f) {
            label.setVisibility(View.GONE);
            actionLogRail.setGravity(Gravity.CENTER);
        }
        actionLogRail.addView(label, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));
        actionLogRail.setContentDescription(hasEntry
                ? getString(R.string.history_recent_description)
                : getString(R.string.history_none));

        item.setScaleX(0.86f);
        item.setScaleY(0.86f);
        item.setAlpha(0.55f);
        item.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
    }

    private ImageView createActionLogItem(int iconResId, int color) {
        ImageView item = new ImageView(this);
        item.setBackground(createActionLogDrawable(color));
        item.setImageResource(iconResId);
        item.setColorFilter(Color.WHITE);
        item.setScaleType(ImageView.ScaleType.CENTER);
        item.setPadding(dp(7), dp(7), dp(7), dp(7));
        item.setElevation(dp(7));
        item.setClickable(true);
        item.setFocusable(true);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
        params.setMargins(dp(3), 0, dp(2), 0);
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
        if (entry.contains(getString(R.string.log_keyword_undo))) {
            return R.drawable.ic_log_undo;
        }
        if (entry.contains(getString(R.string.log_keyword_victory))
                || entry.contains(getString(R.string.log_keyword_finished))) {
            return R.drawable.ic_log_finish;
        }
        if (entry.contains(getString(R.string.log_keyword_captured))) {
            return R.drawable.ic_log_capture;
        }
        if (entry.contains(getString(R.string.log_keyword_moved))) {
            return R.drawable.ic_log_move;
        }
        if (entry.contains(getString(R.string.log_keyword_undo))) {
            return R.drawable.ic_log_undo;
        }
        if (entry.contains(getString(R.string.log_keyword_turn_end))) {
            return R.drawable.ic_log_turn_end;
        }
        if (entry.contains(getString(R.string.log_keyword_new_game))) {
            return R.drawable.ic_log_start;
        }
        if (entry.contains(getString(R.string.log_keyword_roll))) {
            return R.drawable.ic_log_yut;
        }
        return R.drawable.ic_log_history;
    }
    private int getActionLogColor(String entry) {
        if (entry != null) {
            int teamId = parseTeamId(entry);
            if (teamId >= 0) {
                return getTeamColor(teamId);
            }
            if (entry.contains(getString(R.string.log_keyword_victory))
                    || entry.contains(getString(R.string.log_keyword_finished))) {
                return getResources().getColor(R.color.accent_gold);
            }
            if (entry.contains(getString(R.string.log_keyword_undo))) {
                return getResources().getColor(R.color.text_secondary);
            }
        }
        return getResources().getColor(R.color.text_primary);
    }
    private int parseTeamId(String entry) {
        if (entry == null) {
            return -1;
        }
        for (int teamId = 0; teamId < game.getTeamCount(); teamId++) {
            if (entry.startsWith(teamName(teamId) + ":")) {
                return teamId;
            }
        }
        return -1;
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
            return getString(R.string.log_move_fallback);
        }
        ArrayList<String> names = new ArrayList<>();
        for (int step : steps) {
            names.add(resultName(step));
        }
        return String.join("+", names);
    }

    private String formatMoveLog(YutGameEngine.MoveResult result, String planText) {
        StringBuilder builder = new StringBuilder(getString(
                R.string.log_move_base,
                teamName(result.teamId),
                formatPieceIds(result.usedPieceIds),
                planText));
        builder.append(getString(R.string.log_location_suffix,
                boardLocationName(result.startNode), boardLocationName(result.targetNode)));
        if (!result.caughtPieces.isEmpty()) {
            builder.append(getString(R.string.log_capture_suffix, result.caughtPieces.size()));
        }
        if (!result.finishedPieceIds.isEmpty()) {
            builder.append(getString(R.string.log_finish_suffix, result.finishedPieceIds.size()));
        }
        if (result.gameWon) {
            builder.append(getString(R.string.log_victory_suffix));
        }
        return builder.toString();
    }

    private String boardLocationName(int node) {
        if (node == BoardPath.START_NODE) return getString(R.string.location_waiting);
        if (node == BoardPath.END_NODE) return getString(R.string.location_finished);
        int spot = game.visualSpotFor(node);
        String[] names = getResources().getStringArray(R.array.board_spot_names);
        return spot >= 0 && spot < names.length ? names[spot] : getString(R.string.location_board);
    }
    private String formatPieceIds(List<Integer> pieceIds) {
        if (pieceIds == null || pieceIds.isEmpty()) {
            return getString(R.string.piece_ids_fallback);
        }
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < pieceIds.size(); i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append(pieceIds.get(i) + 1);
        }
        return getString(R.string.piece_ids, ids.toString());
    }
    private String buildGameOverMessage(YutGameEngine.MoveResult result) {
        String message = getString(
                R.string.game_over_summary,
                teamName(result.teamId),
                countFinishedPieces(result.teamId),
                YutGameEngine.PIECE_COUNT,
                turnLog.size());
        if (!turnLog.isEmpty()) {
            message += getString(
                    R.string.game_over_last_actions,
                    YutDialogs.buildTurnLogText(turnLog, Math.min(4, turnLog.size())));
        }
        return message;
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

                    @Override
                    public void onShowHelp() {
                        YutDialogs.showHowToPlay(MainActivity.this, () -> {
                        });
                    }

                    @Override
                    public void onCheckUpdates() {
                        openPlayStoreListing();
                    }
                },
                this::endTimerDialogHold);
    }

    private void showGameRulesDialog() {
        beginTimerDialogHold();
        YutDialogs.showGameRules(
                this,
                getTurnDurationIndex(),
                turnIndex -> applySettings(
                        GameStateStore.TURN_DURATION_OPTIONS_MILLIS[turnIndex],
                        feedback.isSoundEnabled(),
                        feedback.isVibrationEnabled()),
                this::endTimerDialogHold);
    }

    private void showHowToPlayDialog() {
        beginTimerDialogHold();
        YutDialogs.showHowToPlay(this, this::endTimerDialogHold);
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
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        updateTimerView();
        persistGameState();
        maybeShowPendingUpdate();
    }

    private void applySettings(long selectedTurnDurationMillis, boolean soundEnabled, boolean vibrationEnabled) {
        boolean durationChanged = turnDurationMillis != selectedTurnDurationMillis;
        boolean keepPaused = isTimerPaused;
        turnDurationMillis = selectedTurnDurationMillis;
        feedback.setEnabled(soundEnabled, vibrationEnabled);

        if (durationChanged) {
            List<YutGameEngine.MoveChoice> choices = game.getMoveChoices();
            if (!choices.isEmpty()) bonusTimeBaselineResultId = choices.get(choices.size() - 1).resultId;
            moveUndoState = null;
            updateUndoActionAvailability();
            if (gameStarted && setupPanel.getVisibility() != View.VISIBLE && !game.isGameOver()) {
                remainingTurnMillis = turnDurationMillis;
                timeExpiredNotified = false;
                isTimerPaused = keepPaused;
                timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
                updateTimerView();
            } else {
                remainingTurnMillis = turnDurationMillis;
                updateTimerView();
            }
        } else {
            updateTimerView();
        }
        updateSetupRuleSummary();

        persistGameState();
        playFeedback(GameFeedback.TAP);
        showToast(getString(R.string.settings_saved));
    }

    private void clearTurnLog() {
        turnLog.clear();
        updateTurnLogView();
        persistGameState();
        playFeedback(GameFeedback.TAP);
        showToast(getString(R.string.history_cleared));
    }

    private int getTurnDurationIndex() {
        return stateStore.getTurnDurationIndex(turnDurationMillis);
    }

    private void updateSetupRuleSummary() {
        if (textSetupRuleSummary == null) {
            return;
        }
        String[] labels = getResources().getStringArray(R.array.turn_duration_labels);
        int index = Math.max(0, Math.min(labels.length - 1, getTurnDurationIndex()));
        String summary = getString(R.string.game_rules_summary, labels[index]);
        textSetupRuleSummary.setText(summary);

        View rulesButton = findViewById(R.id.btn_setup_rules);
        if (rulesButton != null) {
            rulesButton.setContentDescription(getString(R.string.change_game_rules) + ". " + summary);
        }
    }

    private boolean restorePersistedGameState() {
        GameStateStore.AppState appState = stateStore.restoreAppState(
                getResources().getColor(R.color.text_status));
        if (appState == null) {
            return false;
        }

        gameStarted = appState.gameStarted;
        restoredGameSavedAtEpochMillis = appState.savedAtEpochMillis;
        game.restoreState(appState.engineState);
        teamColors = TeamAppearance.restoreColors(appState.teamColors, game.getTeamCount());
        teamShapes = TeamAppearance.restoreShapes(appState.teamShapes);
        applyTeamAppearance();
        remainingTurnMillis = turnDurationMillis <= 0L ? 0L : Math.max(0L, appState.remainingTurnMillis);
        isTimerPaused = appState.timerPaused;
        timeExpiredNotified = appState.timeExpiredNotified;
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        selectedPreviewTeamId = appState.selectedTeamId;
        selectedPreviewPieceId = appState.selectedPieceId;
        if (!isValidPreviewSelection(selectedPreviewTeamId, selectedPreviewPieceId)) {
            selectedPreviewTeamId = -1;
            selectedPreviewPieceId = -1;
        }
        boolean languageChanged = !currentLanguageTag().equals(appState.languageTag);
        restoredStatusMessage = languageChanged ? "" : appState.statusMessage;
        restoredStatusColor = appState.statusColor;
        restoreTurnLog(languageChanged ? new String[0] : appState.turnLog);
        moveUndoState = languageChanged ? null : appState.moveUndoState;
        victoryPending = appState.victoryPending;
        bonusTimeBaselineResultId = appState.bonusTimeBaselineResultId;
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
        appState.victoryPending = victoryPending;
        appState.bonusTimeBaselineResultId = bonusTimeBaselineResultId;
        appState.savedAtEpochMillis = restoredGameSavedAtEpochMillis;
        appState.preserveSavedAtEpochMillis = savedGameChoicePending;
        appState.remainingTurnMillis = remainingTurnMillis;
        appState.timerCheckpointEpochMillis = System.currentTimeMillis();
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
        appState.languageTag = currentLanguageTag();
        appState.engineState = game.saveState();
        appState.teamColors = teamColors.clone();
        appState.teamShapes = teamShapes.clone();
        appState.moveUndoState = moveUndoState;
        stateStore.save(settings, appState);
    }

    private void playFeedback(int type) {
        if (feedback != null) {
            feedback.play(type);
        }
    }

    private boolean expireTimedTurnBeforeAction() {
        if (!gameStarted
                || turnDurationMillis <= 0L
                || game.isGameOver()
                || isTimerHeldForAnimation
                || timerDialogHoldCount > 0) {
            return false;
        }

        syncTimerToNow();
        if (remainingTurnMillis > 0L) {
            return false;
        }

        int teamBeforeExpiration = game.getCurrentTeam();
        finishTurnAfterTimeout();
        return teamBeforeExpiration != game.getCurrentTeam();
    }

    private void finishTurnAfterTimeout() {
        if (!gameStarted
                || turnDurationMillis <= 0L
                || remainingTurnMillis > 0L
                || game.isGameOver()
                || isTimerHeldForAnimation
                || timerDialogHoldCount > 0) {
            return;
        }

        String endedTeamName = teamName(game.getCurrentTeam());
        YutGameEngine.ActionResult result = game.endTurn();
        if (!result.success) {
            return;
        }

        moveUndoState = null;
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        rebuildCurrentWaitingArea();
        updateRollInputAvailability();
        startTurnTimer();
        textStatus.setText(getString(R.string.timer_turn_ended, teamName(game.getCurrentTeam())));
        textStatus.setTextColor(getStatusColor());
        addTurnLog(getString(R.string.log_turn_timeout, endedTeamName));
        playFeedback(GameFeedback.TAP);
        syncControlPanelForNextAction(true);
        persistGameState();
    }

    private void startTurnTimer() {
        isTimerHeldForAnimation = false;
        remainingTurnMillis = turnDurationMillis;
        isTimerPaused = false;
        timeExpiredNotified = false;
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
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
        timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        updateTimerView();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private boolean shouldTimerRun() {
        return isActivityResumed && gameStarted
                && !isTimerPaused
                && !isTimerHeldForAnimation
                && timerDialogHoldCount == 0
                && turnDurationMillis > 0L
                && !game.isGameOver();
    }

    private void syncTimerToNow() {
        long now = SystemClock.elapsedRealtime();
        if (timerCheckpointElapsedMillis <= 0L) {
            timerCheckpointElapsedMillis = now;
            return;
        }

        if (shouldTimerRun()) {
            long elapsed = Math.max(0L, now - timerCheckpointElapsedMillis);
            remainingTurnMillis = Math.max(0L, remainingTurnMillis - elapsed);
        }
        timerCheckpointElapsedMillis = now;
    }

    private void updateTimerView() {
        updateKeepScreenOn();
        if (textTimer == null || btnTimeStop == null) {
            return;
        }

        if (turnDurationMillis <= 0L) {
            textTimer.setText(R.string.unlimited);
            textTimer.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setText(R.string.no_time_limit);
            resetPauseButtonStyle();
            btnTimeStop.setEnabled(false);
            return;
        }
        btnTimeStop.setEnabled(true);

        long seconds = (remainingTurnMillis + TIMER_TICK_MILLIS - 1L) / TIMER_TICK_MILLIS;
        long minutes = seconds / 60;
        long displaySeconds = seconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minutes, displaySeconds);
        textTimer.setText(timeText);

        if (isTimerPaused) {
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            textTimer.setContentDescription(getString(R.string.timer_paused_description, timeText));
            btnTimeStop.setText(R.string.resume_time);
            btnTimeStop.setTextColor(getResources().getColor(R.color.ink_black));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_pause_active);
            btnTimeStop.setBackgroundTintList(null);
        } else if (timerDialogHoldCount > 0) {
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            textTimer.setContentDescription(getString(R.string.timer_settings_description, timeText));
            btnTimeStop.setText(R.string.pause_time);
            btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
            btnTimeStop.setBackgroundTintList(null);
        } else {
            int timerColor = remainingTurnMillis <= 10_000L
                    ? R.color.timer_urgent
                    : remainingTurnMillis <= 30_000L
                            ? R.color.timer_warning
                            : R.color.text_primary;
            textTimer.setTextColor(getResources().getColor(timerColor));
            textTimer.setContentDescription(getString(R.string.timer_remaining_description, timeText));
            btnTimeStop.setText(R.string.pause_time);
            resetPauseButtonStyle();
        }
    }

    private void updateKeepScreenOn() {
        boolean keepOn = isActivityResumed && gameStarted
                && setupPanel != null
                && setupPanel.getVisibility() != View.VISIBLE
                && !game.isGameOver()
                && !isTimerPaused
                && timerDialogHoldCount == 0;
        getWindow().getDecorView().setKeepScreenOn(keepOn);
    }

    private void resetPauseButtonStyle() {
        btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
        btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
        btnTimeStop.setBackgroundTintList(null);
    }
}
