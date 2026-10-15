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
    private HistoryPresenter historyPresenter;
    private TeamStatusPanel teamStatusPanel;
    private GameScreenLayout screenLayout;
    private BoardPresentation boardPresentation;
    private boolean captureBonusStacks;
    private boolean reducedMotion;
    private boolean undoAnimating;
    private int queuedUndoCount;
    private String pendingBackup;
    private boolean skipPersistence;
    private final androidx.activity.result.ActivityResultLauncher<String> exportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
                    uri -> { if (uri != null) writeBackup(uri); else endTimerDialogHold(); });
    private final androidx.activity.result.ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) readBackup(uri); else endTimerDialogHold(); });
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
    private LinearLayout waitingArea;
    private View setupPanel;
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
    private long turnDurationMillis = GameStateStore.DEFAULT_TURN_DURATION_MILLIS;
    private final TurnClock turnClock = new TurnClock(GameStateStore.DEFAULT_TURN_DURATION_MILLIS);
    private final MatchStatistics matchStatistics = new MatchStatistics();
    private VictoryPresentation victoryPresentation;
    private boolean victoryCreditsSeen;
    private long lastBackPressMillis = 0L;
    private boolean timeExpiredNotified = false;
    private boolean gameStarted = false;
    private boolean controlsPanelOpen = true;
    private String restoredStatusMessage = "";
    private int restoredStatusColor = Color.TRANSPARENT;

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
        applyScreenDirection(getSharedPreferences("yutnori_display", MODE_PRIVATE)
                .getInt("direction", 0));
        appUpdateChecker = new AppUpdateChecker(this);
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        if (savedInstanceState != null) {
            controlsPanelOpen = savedInstanceState.getBoolean("controls_panel_open", true);
        }
        bindBackExitHandler();
        boolean restoredGame = restorePersistedGameState();
        if (restoredGame) {
            restoreGameScreen(restoredStatusMessage, restoredStatusColor);
            showToast(getString(R.string.saved_game_details, game.getTeamCount(),
                    game.getCurrentTeam() + 1, game.getPendingResults().size()));
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
        disposeScreenPresenters();
        textStatus = findViewById(R.id.text_status);
        textTimer = findViewById(R.id.text_timer);
        textSetupRuleSummary = findViewById(R.id.text_setup_rule_summary);
        actionLogRail = findViewById(R.id.action_log_rail);
        historyPresenter = new HistoryPresenter(this, actionLogRail, game,
                this::showTurnLogDialog, () -> showTurnLogEntryDialog(0));
        btnTimeStop = findViewById(R.id.btn_time_stop);
        topPanel = findViewById(R.id.top_panel);
        boardContainer = findViewById(R.id.board_container);
        boardArt = findViewById(R.id.board_art);
        boardOverlay = findViewById(R.id.board_overlay);
        controlPanel = findViewById(R.id.control_panel);
        btnToggleInfo = findViewById(R.id.btn_toggle_info);
        btnToggleControls = findViewById(R.id.btn_toggle_controls);
        resultLayout = findViewById(R.id.layout_results);
        teamStatusPanel = new TeamStatusPanel(this, findViewById(R.id.layout_finished_summary),
                findViewById(R.id.finished_summary_scroll), findViewById(R.id.current_team_piece),
                findViewById(R.id.text_title), game, this::landscapeSpacing, () -> turnDurationMillis);
        waitingArea = findViewById(R.id.waiting_area);
        setupPanel = findViewById(R.id.setup_panel);
        screenLayout = new GameScreenLayout(this, findViewById(R.id.root_layout), game,
                new GameScreenLayout.Events() {
                    public boolean isGameStarted() { return gameStarted; }
                    public boolean isAnimating() { return isAnimatingMove; }
                    public boolean areControlsOpen() { return controlsPanelOpen; }
                    public long turnDuration() { return turnDurationMillis; }
                    public int currentTeamColor() { return getTeamColor(game.getCurrentTeam()); }
                    public void clearMovePreviews() { MainActivity.this.clearMovePreviews(); }
                    public void clearPieceGuides() { MainActivity.this.clearPieceGuides(); }
                    public void updateResultButtons() { MainActivity.this.updateResultButtons(); }
                    public void rebuildCurrentWaitingArea() { MainActivity.this.rebuildCurrentWaitingArea(); }
                    public void resizeLandscapeSummaryRows() { MainActivity.this.resizeLandscapeSummaryRows(); }
                    public void updateRollHint() { MainActivity.this.updateRollHint(); }
                    public void refreshBoardAfterPanelLayout() { MainActivity.this.refreshBoardAfterPanelLayout(); }
                    public void cancelPresentationForResize() { cancelMovePresentation(false); }
                    public void restoreAfterResize() {
                        restoreGameScreen(textStatus.getText().toString(), textStatus.getCurrentTextColor());
                        maybeShowVictory();
                    }
                });
        bindBoardResizeListener();

        boardPresentation = new BoardPresentation(this, findViewById(R.id.root_layout), game,
                screenLayout, new BoardPresentation.Events() {
                    public boolean isGameStarted() { return gameStarted; }
                    public boolean isAnimating() { return isAnimatingMove; }
                    public boolean reducedMotion() { return reducedMotion; }
                    public int selectedTeam() { return selectedPreviewTeamId; }
                    public int selectedPiece() { return selectedPreviewPieceId; }
                    public int color(int team) { return teamColors[team]; }
                    public int shape(int team) { return teamShapes[team]; }
                    public void selectPiece(int team, int piece) { MainActivity.this.selectPiece(team, piece); }
                    public void commitMove(View source) { runPressAction(source, MainActivity.this::commitSelectedMove); }
                    public void updateFinishedSummary() { MainActivity.this.updateFinishedSummary(); }
                    public void onFinishVisibilityChanged() {
                        if (gameStarted) applyBoardPanelConstraints(findViewById(R.id.root_layout));
                        scheduleBoardLayoutRefresh();
                    }
                    public void playCatchFeedback() { playFeedback(GameFeedback.CATCH); }
                    public void finishUndoAnimation() { MainActivity.this.finishUndoAnimation(); }
                });
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
        createUnlimitedControls();
        bindSafeAreaInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityResumed = true;
        // Leaving the app pauses a physical-board game automatically.
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
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
        dismissVictoryPresentation();
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
        outState.putBoolean("controls_panel_open", controlsPanelOpen);
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
        dismissVictoryPresentation();
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
            restoreGameScreen(statusMessage, statusColor);
        } else {
            showTeamSetup();
        }
        enterImmersiveMode();
        boardOverlay.post(this::maybeShowVictory);
    }

    @Override
    protected void onDestroy() {
        isActivityResumed = false;
        dismissVictoryPresentation();
        if (teamAppearanceDialog != null) {
            teamAppearanceDialog.setOnDismissListener(null);
            teamAppearanceDialog.dismiss();
            teamAppearanceDialog = null;
        }
        cancelMovePresentation(false);
        disposeScreenPresenters();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.removeCallbacks(updateCheckRunnable);
        if (feedback != null) {
            feedback.release();
        }
        super.onDestroy();
    }

    private void disposeScreenPresenters() {
        if (screenLayout != null) screenLayout.dispose();
        if (boardPresentation != null) boardPresentation.dispose();
        if (historyPresenter != null) historyPresenter.dispose();
        if (teamStatusPanel != null) teamStatusPanel.dispose();
    }

    private void createPieceViews() {
        boardPresentation.createPieceViews();
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
        bindPressAction(R.id.btn_exit_game, this::requestExitGame);
        ((TextView) findViewById(R.id.btn_restart)).setText(R.string.restart_game);

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
        if (DialogPresentation.twoColumns(this)) {
            View setupContent = ((android.view.ViewGroup) setupPanel).getChildAt(0);
            android.widget.FrameLayout.LayoutParams contentParams = new android.widget.FrameLayout.LayoutParams(
                    dp(Math.min(720, Math.round(getResources().getConfiguration().screenWidthDp * 0.82f))),
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER);
            setupContent.setLayoutParams(contentParams);
            findViewById(R.id.text_setup_hint).setVisibility(View.GONE);
            LinearLayout choices = (LinearLayout) findViewById(R.id.btn_team_2).getParent();
            choices.setOrientation(LinearLayout.HORIZONTAL);
            choices.setPadding(dp(8), dp(8), dp(8), dp(8));
            for (int id : new int[]{R.id.btn_team_2, R.id.btn_team_3, R.id.btn_team_4}) {
                Button button = findViewById(id);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(dp(4), 0, dp(4), 0);
                button.setMinHeight(dp(100));
                int icon = id == R.id.btn_team_2 ? R.drawable.ic_teams_2
                        : id == R.id.btn_team_3 ? R.drawable.ic_teams_3 : R.drawable.ic_teams_4;
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(0, icon, 0, 0);
                button.setLayoutParams(params);
            }
            LinearLayout footer = findViewById(R.id.setup_footer);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            for (int id : new int[]{R.id.btn_setup_help, R.id.btn_setup_version}) {
                TextView button = findViewById(id);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(dp(4), dp(8), dp(4), 0);
                button.setMinHeight(dp(48));
                button.setLayoutParams(params);
                button.setPadding(dp(8), dp(6), dp(8), dp(6));
            }
        }
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
                new RelativeSizeSpan(0.80f),
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
                || turnClock.timerDialogHoldCount > 0
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
        int[] lightButtonIds = {R.id.btn_end_turn, R.id.btn_restart, R.id.btn_exit_game};

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
        cancelMovePresentation(true);
        gameStarted = false;
        victoryPending = false;
        savedGameChoicePending = false;
        moveUndoState = null;
        isAnimatingMove = false;
        turnClock.isTimerHeldForAnimation = false;
        pauseTimer();
        clearSelectedPiece();
        clearMovePreviews();
        resultLayout.removeAllViews();
        teamStatusPanel.clear();
        waitingArea.removeAllViews();
        removeAllPiecesFromScreen();
        topPanel.setVisibility(View.GONE);
        boardContainer.setVisibility(View.INVISIBLE);
        controlPanel.setVisibility(View.GONE);
        screenLayout.hideColumns();
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
        if (!gameStarted) {
            showTeamSetup();
            return;
        }

        beginTimerDialogHold();
        YutDialogs.showNewGameConfirmation(
                this,
                () -> {
                    cancelMovePresentation(true);
                    captureBonusStacks = game.isCaptureBonusStacks();
                    startGame(game.getTeamCount());
                },
                this::endTimerDialogHold);
    }

    private void requestExitGame() {
        if (!gameStarted) return;
        beginTimerDialogHold();
        YutDialogs.showExitGameConfirmation(this, this::showTeamSetup, this::endTimerDialogHold);
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
                getString(R.string.saved_game_details, game.getTeamCount(), game.getCurrentTeam() + 1,
                        game.getPendingResults().size()),
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
                    turnClock.timerDialogHoldCount = 0;
                    showTeamSetup();
                    maybeShowPendingUpdate();
                });
    }

    boolean isSavedGameChoiceVisible() {
        return savedGameChoiceVisible;
    }

    private void showTeamAppearanceSetup(int teamCount) {
        showTeamAppearanceSetup(teamCount, stateStore.lastTeamColors(teamCount), stateStore.lastTeamShapes());
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
        boardPresentation.applyTeamAppearance();
    }

    private void startGame(int teamCount) {
        game.setCaptureBonusStacks(captureBonusStacks);
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
        turnClock.isTimerHeldForAnimation = false;
        setupPanel.setVisibility(View.GONE);
        boardContainer.setVisibility(View.VISIBLE);
        setEdgePanelTabsVisible(true);
        resultLayout.removeAllViews();
        buildTeamAreas();

        boardPresentation.postRestore(() -> {
            for (int team = 0; team < YutGameEngine.MAX_TEAM_COUNT; team++) {
                for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                    Piece piece = game.getPiece(team, id);
                    if (team >= game.getTeamCount()) {
                        boardPresentation.removePiece(team, id);
                    } else if (piece.isFinished) {
                        moveToFinishedArea(team, id);
                    } else if (piece.position == BoardPath.START_NODE) {
                        if (team == game.getCurrentTeam()) {
                            moveToWaitSpot(team, id);
                        } else {
                            boardPresentation.removePiece(team, id);
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
            findViewById(R.id.btn_exit_game).setEnabled(true);
        }
        prepareEdgePanels(false);
    }

    private void resetGame() {
        dismissVictoryPresentation();
        matchStatistics.reset();
        victoryCreditsSeen = false;
        bonusTimeBaselineResultId = 0;
        victoryPending = false;
        isAnimatingMove = false;
        turnClock.isTimerHeldForAnimation = false;
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
                    boardPresentation.removePiece(team, id);
                }
            }
        }
    }

    private void buildTeamAreas() {
        waitingArea.removeAllViews();
        waitingArea.setOrientation(LinearLayout.VERTICAL);
        teamStatusPanel.rebuild(teamColors, teamShapes, usesCompactWaitingTray());
        rebuildCurrentWaitingArea();
    }

    private void updateFinishedSummary() {
        teamStatusPanel.render(teamColors, teamShapes);
    }

    private void createWaitingTeamRow(int team) {
        boardPresentation.createWaitingTeamRow(team);
    }

    private void rebuildCurrentWaitingArea() {
        boardPresentation.rebuildCurrentWaitingArea();
    }

    private void syncWaitingPiecesForCurrentTurn() {
        boardPresentation.syncWaitingPiecesForCurrentTurn();
    }

    private void handleYutInput(int steps) {
        if (isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (expireTimedTurnBeforeAction()) {
            return;
        }

        GameStateStore.MoveUndoState undoCandidate = captureMoveUndoState();
        undoCandidate.actionKind = GameStateStore.MoveUndoState.ROLL;
        YutGameEngine.ActionResult result = game.addRoll(steps);
        if (!result.success) {
            showToast(result.message);
            return;
        }

        pushUndoState(undoCandidate);
        selectSinglePendingResult();
        matchStatistics.recordRoll(game.getCurrentTeam(), steps);
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
        if (undoAnimating) {
            int remaining = 0;
            for (GameStateStore.MoveUndoState state = moveUndoState; state != null; state = state.previous) remaining++;
            queuedUndoCount = Math.min(remaining, queuedUndoCount + 1);
            return;
        }
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
            turnClock.remainingTurnMillis = Math.max(0L, turnClock.remainingTurnMillis - BONUS_TURN_MILLIS);
            updateTimerView();
        }
        matchStatistics.undoRoll(game.getCurrentTeam(), removedSteps);
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
        YutGameEngine.SavedState before = game.saveState();
        moveUndoState = state.previous;
        game.restoreState(state.engineState);
        matchStatistics.restore(state.statistics);
        victoryCreditsSeen = false;
        victoryPending = false;
        bonusTimeBaselineResultId = state.bonusTimeBaselineResultId;
        turnClock.remainingTurnMillis = turnDurationMillis <= 0L
                ? 0L
                : Math.max(0L, state.remainingTurnMillis);
        turnClock.isTimerPaused = state.timerPaused;
        timeExpiredNotified = state.timeExpiredNotified;
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        selectedPreviewTeamId = state.selectedTeamId;
        selectedPreviewPieceId = state.selectedPieceId;
        if (!isValidPreviewSelection(selectedPreviewTeamId, selectedPreviewPieceId)) {
            selectedPreviewTeamId = -1;
            selectedPreviewPieceId = -1;
        }
        restoreTurnLog(state.turnLog);
        restoreGameScreen(state.statusMessage, state.statusColor);
        playFeedback(GameFeedback.TAP);
        persistGameState();
        if (state.actionKind == GameStateStore.MoveUndoState.MOVE && !reducedMotion) {
            animateUndo(before, state);
        }
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
        GameStateStore.MoveUndoState undoCandidate = captureMoveUndoState();
        undoCandidate.actionKind = GameStateStore.MoveUndoState.TURN;
        YutGameEngine.ActionResult result = game.endTurn();
        if (!result.success) {
            showToast(result.message);
            return;
        }

        pushUndoState(undoCandidate);
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

        persistGameState();
    }

    private void updateResultButtons() {
        resultLayout.removeAllViews();
        updateRollHint();
        List<YutGameEngine.MoveChoice> moveChoices = game.getMoveChoices();

        for (int i = 0; i < moveChoices.size(); i++) {
            YutGameEngine.MoveChoice choice = moveChoices.get(i);
            boolean vertical = usesLandscapeControls();
            Button button = new Button(this);
            button.setText(choice.label);
            button.setAllCaps(false);
            button.setTextColor(getResources().getColor(R.color.text_primary));
            button.setTextSize(vertical ? 18 : moveChoices.size() > 1 ? 13 : 15);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setBackgroundResource(R.drawable.shape_result_button);
            button.setBackgroundTintList(null);
            button.setStateListAnimator(null);
            button.setSelected(choice.selectionOrder > 0);
            button.setEnabled(!isAnimatingMove);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    vertical ? ViewGroup.LayoutParams.MATCH_PARENT : dp(moveChoices.size() > 1 ? 86 : 72), dp(vertical ? 48 : 44));
            params.setMargins(0, 0, vertical ? 0 : dp(6), vertical ? dp(4) : 0);
            if (vertical) {
                button.setMinWidth(0);
                button.setPadding(dp(2), 0, dp(2), 0);
                button.setMaxLines(2);
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(button, 14, 18, 1, TypedValue.COMPLEX_UNIT_SP);
            }
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

        recordReversePaths(undoCandidate, result);
        pushUndoState(undoCandidate);
        applyMoveResult(result, planText);
    }

    private void applyMoveResult(YutGameEngine.MoveResult result, String planText) {
        // Commit all game effects before starting disposable presentation work.
        matchStatistics.recordMove(result);
        updateStatusAfterMove(result);
        addTurnLog(formatMoveLog(result, planText));
        if (result.turnChanged) {
            startTurnTimer();
        } else if (result.caught) {
            for (int i = 0; i < result.grantedCaptureRolls; i++) addBonusTime();
        }
        victoryPending = result.gameWon;
        isAnimatingMove = true;
        turnClock.isTimerHeldForAnimation = true;
        setControlsEnabled(false);
        clearSelectedPiece();
        clearMovePreviews(false);
        persistGameState();

        animateMoveResult(result, () -> {
            for (YutGameEngine.PieceRef caughtPiece : result.caughtPieces) {
                moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
            }

            for (int id : result.finishedPieceIds) {
                moveToFinishedArea(result.teamId, id);
            }

            isAnimatingMove = false;
            setFinishDestinationVisible(false);
            turnClock.isTimerHeldForAnimation = false;
            renderBoardPiecesNow();
            updateFinishedSummary();
            rebuildCurrentWaitingArea();
            updateResultButtons();
            setControlsEnabled(true);
            if (result.gameWon) {
                playFeedback(GameFeedback.WIN);
                setControlsEnabled(false);
                findViewById(R.id.btn_restart).setEnabled(true);
                findViewById(R.id.btn_exit_game).setEnabled(true);
                updateKeepScreenOn();
                persistGameState();
                maybeShowVictory();
                return;
            }
            if (!result.caught) {
                playFeedback(GameFeedback.MOVE);
            }
            turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
            updatePieceSelectionStyles(true);
            updateMovePreviews();

            persistGameState();
            maybeShowPendingUpdate();
        });
    }

    private void cancelMovePresentation(boolean redraw) {
        if (!isAnimatingMove) return;
        isAnimatingMove = false;
        undoAnimating = false;
        queuedUndoCount = 0;
        boardPresentation.cancel();
        turnClock.isTimerHeldForAnimation = false;
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        if (redraw && boardOverlay != null && gameStarted) {
            restoreGameScreen(textStatus.getText().toString(), textStatus.getCurrentTextColor());
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
        state.statistics = matchStatistics.snapshot();
        state.bonusTimeBaselineResultId = bonusTimeBaselineResultId;
        state.remainingTurnMillis = turnClock.remainingTurnMillis;
        state.timerPaused = turnClock.isTimerPaused;
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

    private void pushUndoState(GameStateStore.MoveUndoState state) {
        state.previous = moveUndoState;
        moveUndoState = state;
        for (int count = 1; state.previous != null; count++) {
            if (count >= GameStateStore.MAX_UNDO_ACTIONS) {
                state.previous = null;
                break;
            }
            state = state.previous;
        }
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
        victoryPresentation = new VictoryPresentation(
                this, game, matchStatistics, teamColors, teamShapes,
                reducedMotion || victoryCreditsSeen,
                this::showTeamSetup,
                this::showTurnLogDialog,
                () -> {
                    gameOverDialogVisible = false;
                    victoryPending = false;
                    victoryPresentation = null;
                    persistGameState();
                }, () -> {
                    victoryCreditsSeen = true;
                    persistGameState();
                });
        victoryPresentation.show();
    }

    private void dismissVictoryPresentation() {
        if (victoryPresentation == null) return;
        victoryCreditsSeen = true;
        victoryPresentation.dispose();
        victoryPresentation = null;
        gameOverDialogVisible = false;
    }

    private void animateMoveResult(YutGameEngine.MoveResult result, Runnable onComplete) {
        boardPresentation.animateMoveResult(result, onComplete);
    }

    private void animateMoveSegment(YutGameEngine.MoveResult result, int segmentIndex, Runnable onComplete, int animationGeneration) {
        boardPresentation.animateMoveSegment(result, segmentIndex, onComplete, animationGeneration);
    }

    private void animatePathStep(
            int teamId,
            int pieceId,
            YutGameEngine.MoveAnimation segment,
            List<Integer> path,
            int index,
            Runnable onComplete,
            int animationGeneration) {
        boardPresentation.animatePathStep(teamId, pieceId, segment, path, index, onComplete, animationGeneration);
    }

    private void animateFinishEntry(int teamId, int pieceId, int count, Runnable onComplete,
            int animationGeneration) {
        boardPresentation.animateFinishEntry(teamId, pieceId, count, onComplete, animationGeneration);
    }

    private void clearFinishAnimation() {
        boardPresentation.clearFinishAnimation();
    }

    private int prepareAnimatedBoardGroup(int teamId, List<Integer> pieceIds, int logicalNode) {
        return boardPresentation.prepareAnimatedBoardGroup(teamId, pieceIds, logicalNode);
    }

    private void animateArrivedBoardGroup(
            int teamId,
            int movingPieceId,
            YutGameEngine.MoveAnimation segment,
            int logicalNode,
            Runnable onComplete,
            int animationGeneration) {
        boardPresentation.animateArrivedBoardGroup(teamId, movingPieceId, segment, logicalNode, onComplete, animationGeneration);
    }

    private void animateCapturedPieces(
            List<YutGameEngine.PieceRef> caughtPieces,
            Runnable onComplete,
            int animationGeneration) {
        boardPresentation.animateCapturedPieces(caughtPieces, onComplete, animationGeneration);
    }

    private void animateStackLayout(
            PieceStackView view,
            float targetProgress,
            long duration,
            Runnable onComplete) {
        boardPresentation.animateStackLayout(view, targetProgress, duration, onComplete);
    }

    private void renderBoardPiecesNow() {
        boardPresentation.renderBoardPiecesNow();
    }

    private ArrayList<Integer> getBoardGroupIds(int teamId, int logicalNode) {
        return boardPresentation.getBoardGroupIds(teamId, logicalNode);
    }

    private int getRepresentativePieceId(List<Integer> pieceIds) {
        return boardPresentation.getRepresentativePieceId(pieceIds);
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
        boardPresentation.configureBoardGroupAtNode(view, teamId, pieceId, groupCount, logicalNode);
    }

    private void configureBoardGroupAppearance(PieceStackView view, int teamId, int pieceId, int groupCount) {
        boardPresentation.configureBoardGroupAppearance(view, teamId, pieceId, groupCount);
    }

    private void restorePieceIdentity(PieceStackView view, int teamId, int pieceId) {
        boardPresentation.restorePieceIdentity(view, teamId, pieceId);
    }

    private void placePieceOnBoardNode(View pieceView, int logicalNode, int size) {
        boardPresentation.placePieceOnBoardNode(pieceView, logicalNode, size);
    }

    private float[] getBoardNodePosition(int logicalNode, int size) {
        return boardPresentation.getBoardNodePosition(logicalNode, size);
    }

    private float[] getBoardSpotCenter(int spotIndex) {
        return boardPresentation.getBoardSpotCenter(spotIndex);
    }

    private void createFinishDestination() {
        boardPresentation.createFinishDestination();
    }

    private void setFinishDestinationVisible(boolean visible) {
        boardPresentation.setFinishDestinationVisible(visible);
    }

    private void updateMovePreviews() {
        boardPresentation.updateMovePreviews();
    }

    private void addPreviewGlow(int spotIndex) {
        boardPresentation.addPreviewGlow(spotIndex);
    }

    private void placeOverlayAtSpot(View view, int spotIndex, int size) {
        boardPresentation.placeOverlayAtSpot(view, spotIndex, size);
    }

    private void positionBoardChild(View view, float left, float top) {
        boardPresentation.positionBoardChild(view, left, top);
    }

    private void clearMovePreviews() {
        boardPresentation.clearMovePreviews();
    }

    private void clearMovePreviews(boolean hideFinish) {
        boardPresentation.clearMovePreviews(hideFinish);
    }

    private void clearSelectedPiece() {
        selectedPreviewTeamId = -1;
        selectedPreviewPieceId = -1;
        updatePieceSelectionStyles();
    }

    private void updatePieceSelectionStyles() {
        boardPresentation.updatePieceSelectionStyles();
    }

    private void updatePieceSelectionStyles(boolean animateGuidance) {
        boardPresentation.updatePieceSelectionStyles(animateGuidance);
    }

    private boolean shouldShowPieceGuidance() {
        return boardPresentation.shouldShowPieceGuidance();
    }

    private boolean isPieceEligibleForGuidance(int teamId, int pieceId) {
        return boardPresentation.isPieceEligibleForGuidance(teamId, pieceId);
    }

    private void addPieceGuides(boolean animateGuidance) {
        boardPresentation.addPieceGuides(animateGuidance);
    }

    private void placePieceGuideOnBoard(View guide, int logicalNode, int size) {
        boardPresentation.placePieceGuideOnBoard(guide, logicalNode, size);
    }

    private void animatePieceGuide(View guide, View pieceView, boolean animateGuidance) {
        boardPresentation.animatePieceGuide(guide, pieceView, animateGuidance);
    }

    private void clearPieceGuides() {
        boardPresentation.clearPieceGuides();
    }

    private void moveToWaitSpot(int teamId, int pieceId) {
        boardPresentation.moveToWaitSpot(teamId, pieceId);
    }

    private void moveToFinishedArea(int teamId, int pieceId) {
        boardPresentation.moveToFinishedArea(teamId, pieceId);
    }

    private void moveViewToParent(View view, ViewGroup newParent) {
        boardPresentation.moveViewToParent(view, newParent);
    }

    private GradientDrawable createPreviewDrawable() {
        return boardPresentation.createPreviewDrawable();
    }

    private GradientDrawable createPreviewRingDrawable() {
        return boardPresentation.createPreviewRingDrawable();
    }

    private GradientDrawable createPieceGuideDrawable(int teamId) {
        return boardPresentation.createPieceGuideDrawable(teamId);
    }
    private int getBoardPieceSize() {
        return boardPresentation.getBoardPieceSize();
    }

    private int getBoardPieceViewSize() {
        return boardPresentation.getBoardPieceViewSize();
    }

    private int getStatusColor() {
        return getTeamColor(game.getCurrentTeam());
    }

    private int getTeamColor(int teamId) {
        return TeamAppearance.label(teamColors[teamId]);
    }

    private boolean hasEdgePanels() {
        return screenLayout.hasEdgePanels();
    }

    private void bindBoardResizeListener() {
        screenLayout.bindBoardResizeListener();
    }

    private void scheduleBoardLayoutRefresh() {
        screenLayout.scheduleBoardLayoutRefresh();
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
        screenLayout.setEdgePanelTabsVisible(visible);
    }

    private void applyEdgePanelState(boolean animate) {
        screenLayout.applyEdgePanelState(animate);
    }

    private void applyBoardPanelConstraints(ViewGroup root) {
        screenLayout.applyBoardPanelConstraints(root);
    }

    private void clearPanelTabConstraints(ConstraintSet constraints, int viewId) {
        screenLayout.clearPanelTabConstraints(constraints, viewId);
    }

    private void centerTabHorizontally(ConstraintSet constraints, int viewId) {
        screenLayout.centerTabHorizontally(constraints, viewId);
    }

    private void centerTabVertically(ConstraintSet constraints, int viewId) {
        screenLayout.centerTabVertically(constraints, viewId);
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
        screenLayout.updateDrawerTabLabel(tab, text);
    }
    private boolean usesCompactWaitingTray() {
        return screenLayout.usesCompactWaitingTray();
    }

    private boolean isCompactPortrait() {
        return screenLayout.isCompactPortrait();
    }
    private void applyResponsiveSizing() {
        screenLayout.applyResponsiveSizing();
    }

    private void applyCompactActionAutoSizing() {
        screenLayout.applyCompactActionAutoSizing();
    }

    private void applyFontScaleSizing() {
        screenLayout.applyFontScaleSizing();
    }

    private void setViewHeightIfPresent(int viewId, int heightDp) {
        screenLayout.setViewHeightIfPresent(viewId, heightDp);
    }

    private void setViewWidthIfPresent(int viewId, int widthDp) {
        screenLayout.setViewWidthIfPresent(viewId, widthDp);
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
        screenLayout.bindSafeAreaInsets();
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
        boardPresentation.bounceArrivedPieces(teamId, pieceIds, onComplete);
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
        setEnabledIfPresent(R.id.btn_exit_game, enabled);

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
        int label = canUndoMove && moveUndoState.actionKind == GameStateStore.MoveUndoState.MOVE
                ? R.string.undo_move : R.string.undo_last;
        undoButton.setText(label);
        String target = canUndoMove ? getString(R.string.undo_target,
                teamName(moveUndoState.engineState.currentTeam),
                getString(moveUndoState.actionKind == GameStateStore.MoveUndoState.MOVE ? R.string.undo_kind_move
                        : moveUndoState.actionKind == GameStateStore.MoveUndoState.ROLL ? R.string.undo_kind_roll : R.string.undo_kind_turn))
                : getString(label);
        undoButton.setContentDescription(target);
        ViewCompat.setTooltipText(undoButton, target);
        undoButton.setOnLongClickListener(v -> { showToast(target); return true; });
        undoButton.setEnabled(gameStarted
                && (!isAnimatingMove || undoAnimating)
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
        boardPresentation.removeAllPiecesFromScreen();
    }

    private void resetPieceViewTransform(View view) {
        boardPresentation.resetPieceViewTransform(view);
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
        historyPresenter.render(turnLog, teamColors, usesLandscapeControls() || usesCompactWaitingTray()
                || getResources().getConfiguration().fontScale >= 1.4f);
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
        return historyPresenter.formatMove(result, planText);
    }

    private String buildGameOverMessage(YutGameEngine.MoveResult result) {
        return historyPresenter.formatGameOver(result, turnLog);
    }

    private void loadSettings() {
        GameStateStore.Settings settings = stateStore.loadSettings();
        turnDurationMillis = settings.turnDurationMillis;
        turnClock.remainingTurnMillis = turnDurationMillis;
        feedback.setEnabled(settings.soundEnabled, settings.vibrationEnabled);
        captureBonusStacks = settings.captureBonusStacks;
        reducedMotion = settings.reducedMotion;
    }

    private void applyScreenDirection(int direction) {
        int requested = direction == 1
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                : direction == 2 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
        setRequestedOrientation(requested);
    }

    private void showSettingsDialog() {
        beginTimerDialogHold();
        YutDialogs.SettingsState settingsUi = new YutDialogs.SettingsState(getTurnDurationIndex(),
                feedback.isSoundEnabled(), feedback.isVibrationEnabled(), turnLog.size());
        settingsUi.gameStarted = gameStarted;
        settingsUi.captureBonusStacks = gameStarted ? game.isCaptureBonusStacks() : captureBonusStacks;
        settingsUi.reducedMotion = reducedMotion;
        settingsUi.screenDirection = getSharedPreferences("yutnori_display", MODE_PRIVATE)
                .getInt("direction", 0);
        YutDialogs.showSettings(
                this,
                settingsUi,
                new YutDialogs.SettingsActions() {
                    @Override
                    public void onScreenDirection(int direction) {
                        getSharedPreferences("yutnori_display", MODE_PRIVATE).edit()
                                .putInt("direction", direction).apply();
                        applyScreenDirection(direction);
                    }
                    @Override
                    public void onApply(int turnIndex, boolean soundEnabled, boolean vibrationEnabled, boolean motion) {
                        reducedMotion = motion;
                        if (motion) cancelMovePresentation(true);
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
                    @Override public void onExport() { exportGameBackup(); }
                    @Override public void onImport() { requestImportGameBackup(); }
                },
                this::endTimerDialogHold);
    }

    private void showGameRulesDialog() {
        beginTimerDialogHold();
        YutDialogs.showGameRules(
                this,
                getTurnDurationIndex(),
                gameStarted ? game.isCaptureBonusStacks() : captureBonusStacks,
                gameStarted,
                (turnIndex, stacking) -> {
                    captureBonusStacks = stacking;
                    applySettings(
                        GameStateStore.TURN_DURATION_OPTIONS_MILLIS[turnIndex],
                        feedback.isSoundEnabled(),
                        feedback.isVibrationEnabled());
                },
                this::endTimerDialogHold);
    }

    private void showHowToPlayDialog() {
        beginTimerDialogHold();
        YutDialogs.showHowToPlay(this, this::endTimerDialogHold);
    }

    private void beginTimerDialogHold() {
        syncTimerToNow();
        turnClock.beginDialogHold();
        updateTimerView();
    }

    private void endTimerDialogHold() {
        turnClock.endDialogHold(SystemClock.elapsedRealtime());
        updateTimerView();
        persistGameState();
        maybeShowPendingUpdate();
    }

    private void applySettings(long selectedTurnDurationMillis, boolean soundEnabled, boolean vibrationEnabled) {
        boolean durationChanged = turnDurationMillis != selectedTurnDurationMillis;
        boolean keepPaused = turnClock.isTimerPaused;
        turnDurationMillis = selectedTurnDurationMillis;
        feedback.setEnabled(soundEnabled, vibrationEnabled);

        if (durationChanged) {
            List<YutGameEngine.MoveChoice> choices = game.getMoveChoices();
            if (!choices.isEmpty()) bonusTimeBaselineResultId = choices.get(choices.size() - 1).resultId;
            moveUndoState = null;
            updateUndoActionAvailability();
            if (gameStarted && setupPanel.getVisibility() != View.VISIBLE && !game.isGameOver()) {
                turnClock.remainingTurnMillis = turnDurationMillis;
                timeExpiredNotified = false;
                turnClock.isTimerPaused = keepPaused;
                turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
                updateTimerView();
            } else {
                turnClock.remainingTurnMillis = turnDurationMillis;
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
        summary += (DialogPresentation.twoColumns(this) ? " · " : "\n")
                + getString(captureBonusStacks ? R.string.capture_rule_double : R.string.capture_rule_single);
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
            teamColors = stateStore.lastTeamColors(YutGameEngine.MAX_TEAM_COUNT);
            teamShapes = stateStore.lastTeamShapes();
            applyTeamAppearance();
            return false;
        }

        gameStarted = appState.gameStarted;
        restoredGameSavedAtEpochMillis = appState.savedAtEpochMillis;
        game.restoreState(appState.engineState);
        matchStatistics.restore(appState.statistics);
        victoryCreditsSeen = appState.victoryCreditsSeen;
        teamColors = TeamAppearance.restoreColors(appState.teamColors, game.getTeamCount());
        teamShapes = TeamAppearance.restoreShapes(appState.teamShapes);
        applyTeamAppearance();
        turnClock.remainingTurnMillis = turnDurationMillis <= 0L ? 0L : Math.max(0L, appState.remainingTurnMillis);
        turnClock.isTimerPaused = appState.timerPaused;
        timeExpiredNotified = appState.timeExpiredNotified;
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
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
        if (stateStore == null || skipPersistence) {
            return;
        }
        syncTimerToNow();
        GameStateStore.Settings settings = new GameStateStore.Settings();
        settings.turnDurationMillis = turnDurationMillis;
        settings.soundEnabled = feedback != null && feedback.isSoundEnabled();
        settings.vibrationEnabled = feedback != null && feedback.isVibrationEnabled();
        settings.captureBonusStacks = captureBonusStacks;
        settings.reducedMotion = reducedMotion;

        GameStateStore.AppState appState = new GameStateStore.AppState();
        appState.gameStarted = gameStarted;
        appState.victoryPending = victoryPending;
        appState.statistics = matchStatistics.snapshot();
        appState.victoryCreditsSeen = victoryCreditsSeen;
        appState.bonusTimeBaselineResultId = bonusTimeBaselineResultId;
        appState.savedAtEpochMillis = restoredGameSavedAtEpochMillis;
        appState.preserveSavedAtEpochMillis = savedGameChoicePending;
        appState.remainingTurnMillis = turnClock.remainingTurnMillis;
        appState.timerCheckpointEpochMillis = System.currentTimeMillis();
        appState.timerPaused = turnClock.isTimerPaused;
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

    private void exportGameBackup() {
        cancelMovePresentation(true);
        beginTimerDialogHold();
        persistGameState();
        try {
            pendingBackup = stateStore.exportBackup();
            exportLauncher.launch("Yutnori-" + BuildConfig.VERSION_NAME + "-game.json");
        } catch (Exception error) {
            endTimerDialogHold();
            showToast(getString(R.string.backup_failed));
        }
    }

    private void writeBackup(Uri uri) {
        String content;
        try { content = pendingBackup == null ? stateStore.exportBackup() : pendingBackup; }
        catch (Exception error) { endTimerDialogHold(); showToast(getString(R.string.backup_failed)); return; }
        String backup = content;
        pendingBackup = null;
        new Thread(() -> {
            boolean success = false;
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null || backup == null) throw new java.io.IOException("No destination");
                output.write(backup.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                success = true;
            } catch (Exception ignored) { }
            boolean completed = success;
            runOnUiThread(() -> {
                endTimerDialogHold();
                showToast(getString(completed ? R.string.backup_exported : R.string.backup_failed));
            });
        }, "yutnori-export").start();
    }

    private void requestImportGameBackup() {
        beginTimerDialogHold();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.import_game)
                .setMessage(R.string.backup_replace)
                .setPositiveButton(R.string.import_game, (ignored, which) -> {
                    beginTimerDialogHold();
                    importLauncher.launch(new String[]{"application/json", "text/plain"});
                }).setNegativeButton(R.string.close, null).create();
        dialog.setOnDismissListener(ignored -> endTimerDialogHold());
        dialog.show();
    }

    private void readBackup(Uri uri) {
        new Thread(() -> {
            String json = null;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri);
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                if (input == null) throw new java.io.IOException("No backup");
                byte[] chunk = new byte[8192]; int count;
                while ((count = input.read(chunk)) != -1) {
                    if (bytes.size() + count > 2_000_000) throw new java.io.IOException("Backup too large");
                    bytes.write(chunk, 0, count);
                }
                json = bytes.toString("UTF-8");
            } catch (Exception ignored) { }
            String backup = json;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                try {
                    stateStore.importBackup(backup);
                    skipPersistence = true;
                    cancelMovePresentation(false);
                    showToast(getString(R.string.backup_imported));
                    recreate();
                } catch (Exception error) {
                    endTimerDialogHold();
                    showToast(getString(R.string.backup_failed));
                }
            });
        }, "yutnori-import").start();
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
                || turnClock.isTimerHeldForAnimation
                || turnClock.timerDialogHoldCount > 0) {
            return false;
        }

        syncTimerToNow();
        if (turnClock.remainingTurnMillis > 0L) {
            return false;
        }

        int teamBeforeExpiration = game.getCurrentTeam();
        finishTurnAfterTimeout();
        return teamBeforeExpiration != game.getCurrentTeam();
    }

    private void finishTurnAfterTimeout() {
        if (!gameStarted
                || turnDurationMillis <= 0L
                || turnClock.remainingTurnMillis > 0L
                || game.isGameOver()
                || turnClock.isTimerHeldForAnimation
                || turnClock.timerDialogHoldCount > 0) {
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

        persistGameState();
    }

    private void startTurnTimer() {
        turnClock.restart(turnDurationMillis, SystemClock.elapsedRealtime());
        timeExpiredNotified = false;
        updateTimerView();
    }

    private void addBonusTime() {
        if (game.isGameOver() || turnDurationMillis <= 0L) {
            return;
        }
        syncTimerToNow();
        turnClock.remainingTurnMillis += BONUS_TURN_MILLIS;
        timeExpiredNotified = false;
        updateTimerView();
    }

    private void pauseTimer() {
        syncTimerToNow();
        turnClock.isTimerPaused = true;
        updateTimerView();
    }

    private void toggleTimeStop() {
        if (game.isGameOver() || setupPanel.getVisibility() == View.VISIBLE || turnDurationMillis <= 0L) {
            return;
        }
        syncTimerToNow();
        turnClock.isTimerPaused = !turnClock.isTimerPaused;
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        updateTimerView();
        playFeedback(GameFeedback.TAP);
        persistGameState();
    }

    private boolean shouldTimerRun() {
        return turnClock.shouldRun(isActivityResumed && gameStarted
                && turnDurationMillis > 0L && !game.isGameOver());
    }

    private void syncTimerToNow() {
        turnClock.sync(SystemClock.elapsedRealtime(), shouldTimerRun());
    }

    private void updateTimerView() {
        updateKeepScreenOn();
        if (textTimer == null || btnTimeStop == null) {
            return;
        }

        updateTimerLayout();
        if (turnDurationMillis <= 0L) {
            textTimer.setText(R.string.unlimited);
            textTimer.setTextColor(getResources().getColor(R.color.text_primary));
            textTimer.setContentDescription(getString(R.string.no_time_limit));
            btnTimeStop.setEnabled(false);
            return;
        }
        btnTimeStop.setEnabled(true);

        long seconds = (turnClock.remainingTurnMillis + TIMER_TICK_MILLIS - 1L) / TIMER_TICK_MILLIS;
        long minutes = seconds / 60;
        long displaySeconds = seconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", minutes, displaySeconds);
        textTimer.setText(timeText);

        if (turnClock.isTimerPaused) {
            if (usesLandscapeControls()) {
                textTimer.setText(timeText + "\n" + getString(R.string.timer_stopped_label));
            }
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            textTimer.setContentDescription(getString(R.string.timer_paused_description, timeText));
            btnTimeStop.setText(R.string.resume_time);
            btnTimeStop.setTextColor(getResources().getColor(R.color.ink_black));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_pause_active);
            btnTimeStop.setBackgroundTintList(null);
        } else if (turnClock.timerDialogHoldCount > 0) {
            textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            textTimer.setContentDescription(getString(R.string.timer_settings_description, timeText));
            btnTimeStop.setText(R.string.pause_time);
            btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
            btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
            btnTimeStop.setBackgroundTintList(null);
        } else {
            int timerColor = turnClock.remainingTurnMillis <= 10_000L
                    ? R.color.timer_urgent
                    : turnClock.remainingTurnMillis <= 30_000L
                            ? R.color.timer_warning
                            : R.color.text_primary;
            textTimer.setTextColor(getResources().getColor(timerColor));
            textTimer.setContentDescription(getString(R.string.timer_remaining_description, timeText));
            btnTimeStop.setText(R.string.pause_time);
            resetPauseButtonStyle();
        }
    }

    // Layout is selected by orientation; the timer setting only changes game rules.
    private boolean usesLandscapeControls() {
        return screenLayout.usesLandscapeControls();
    }

    private void recordReversePaths(GameStateStore.MoveUndoState state, YutGameEngine.MoveResult result) {
        state.reversePaths = new int[16][];
        for (int id = 0; id < 4; id++) {
            ArrayList<Integer> nodes = new ArrayList<>();
            for (YutGameEngine.MoveAnimation segment : result.animationSegments) {
                if (!segment.pieceIds.contains(id)) continue;
                if (nodes.isEmpty()) nodes.add(segment.startNode);
                nodes.addAll(segment.path);
            }
            int[] reversed = new int[nodes.size()];
            for (int i = 0; i < reversed.length; i++) reversed[i] = nodes.get(nodes.size() - 1 - i);
            state.reversePaths[result.teamId * 4 + id] = reversed;
        }
    }

    private float[] undoPoint(int node, int pieceId, ViewGroup root) {
        return boardPresentation.undoPoint(node, pieceId, root);
    }

    private void animateUndo(YutGameEngine.SavedState before, GameStateStore.MoveUndoState state) {
        undoAnimating = true;
        isAnimatingMove = true;
        turnClock.isTimerHeldForAnimation = true;
        setControlsEnabled(false);
        boardPresentation.animateUndo(before, state);
    }

    private void finishUndoAnimation() {
        undoAnimating = false;
        isAnimatingMove = false;
        turnClock.isTimerHeldForAnimation = false;
        turnClock.timerCheckpointElapsedMillis = SystemClock.elapsedRealtime();
        renderBoardPiecesNow();
        setControlsEnabled(true);
        updatePieceSelectionStyles();
        updateMovePreviews();
        persistGameState();
        drainUndoQueue();
    }

    private void drainUndoQueue() {
        if (queuedUndoCount <= 0 || moveUndoState == null) { queuedUndoCount = 0; return; }
        queuedUndoCount--;
        undoLastAction();
        if (!undoAnimating) timerHandler.post(this::drainUndoQueue);
    }

    private void createUnlimitedControls() {
        screenLayout.createUnlimitedControls();
    }

    private LinearLayout createLandscapeColumn(ConstraintLayout root, int id, int widthDp) {
        return screenLayout.createLandscapeColumn(root, id, widthDp);
    }

    private void addColumnTitle(LinearLayout column, int text) {
        screenLayout.addColumnTitle(column, text);
    }

    private int landscapeSpacing() {
        return screenLayout.landscapeSpacing();
    }

    private boolean usesVerticalWaitingTray() {
        return screenLayout.usesVerticalWaitingTray();
    }

    private void updateUnlimitedControls(boolean landscape) {
        screenLayout.updateUnlimitedControls(landscape);
    }

    private void applyUnlimitedBoardConstraints(ConstraintLayout root) {
        screenLayout.applyUnlimitedBoardConstraints(root);
    }

    private void resizeLandscapeSummaryRows() {
        teamStatusPanel.resizeRows();
    }

    private void updateTimerLayout() {
        screenLayout.updateTimerLayout();
    }

    private void updateRollHint() {
        screenLayout.setRollHint(game.getPendingResults().isEmpty() ? getString(R.string.roll_first)
                : game.getNormalRollAllowance() > 0 ? getString(R.string.extra_rolls, game.getNormalRollAllowance())
                : getString(R.string.select_results_hint));
        if (!usesLandscapeControls() && gameStarted && !game.getPendingResults().isEmpty()
                && game.getNormalRollAllowance() > 0) {
            String base = textStatus.getText().toString().split("\n", 2)[0];
            textStatus.setText(base + "\n" + getString(R.string.extra_rolls, game.getNormalRollAllowance()));
        }
    }

    private void updateKeepScreenOn() {
        boolean keepOn = isActivityResumed && gameStarted
                && setupPanel != null
                && setupPanel.getVisibility() != View.VISIBLE
                && !game.isGameOver()
                && !turnClock.isTimerPaused
                && turnClock.timerDialogHoldCount == 0;
        getWindow().getDecorView().setKeepScreenOn(keepOn);
    }

    private void resetPauseButtonStyle() {
        btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
        btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
        btnTimeStop.setBackgroundTintList(null);
    }
}
