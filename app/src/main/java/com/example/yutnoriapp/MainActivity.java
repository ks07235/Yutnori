package com.example.yutnoriapp;

import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.widget.TextViewCompat;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* JADX INFO: loaded from: classes3.dex */
public class MainActivity extends AppCompatActivity {
    private static final long BACK_EXIT_INTERVAL_MILLIS = 1800;
    private static final long BONUS_TURN_MILLIS = 30000;
    private static final int COMPACT_PORTRAIT_MAX_HEIGHT_DP = 700;
    private static final int MAX_TURN_LOG_ENTRIES = 12;
    private static final long TIMER_TICK_MILLIS = 1000;
    private LinearLayout actionLogRail;
    private FrameLayout boardContainer;
    private TextView btnTimeStop;
    private View btnToggleControls;
    private View btnToggleInfo;
    private View controlPanel;
    private GameFeedback feedback;
    private TextView[] finishedCountViews;
    private LinearLayout finishedSummaryLayout;
    private TextView[][] pieceViews;
    private LinearLayout resultLayout;
    private View setupPanel;
    private GameStateStore stateStore;
    private TextView textStatus;
    private TextView textTimer;
    private View topPanel;
    private FrameLayout[][] waitSpots;
    private LinearLayout waitingArea;
    private final YutGameEngine game = new YutGameEngine(isKoreanLanguage());
    private final ArrayList<View> previewViews = new ArrayList<>();
    private final ArrayList<String> turnLog = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() { // from class: com.example.yutnoriapp.MainActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.shouldTimerRun()) {
                MainActivity.this.syncTimerToNow();
                MainActivity.this.updateTimerView();
                if (MainActivity.this.remainingTurnMillis == 0 && !MainActivity.this.timeExpiredNotified) {
                    MainActivity.this.timeExpiredNotified = true;
                    MainActivity.this.textStatus.setText(R.string.timer_expired);
                    MainActivity.this.textStatus.setTextColor(MainActivity.this.getResources().getColor(R.color.btn_restart));
                    MainActivity.this.persistGameState();
                }
            }
            MainActivity.this.timerHandler.postDelayed(this, MainActivity.TIMER_TICK_MILLIS);
        }
    };
    private int selectedPreviewTeamId = -1;
    private int selectedPreviewPieceId = -1;
    private boolean isAnimatingMove = false;
    private long turnDurationMillis = 180000;
    private long remainingTurnMillis = 180000;
    private long lastBackPressMillis = 0;
    private boolean isTimerPaused = false;
    private boolean isTimerHeldForAnimation = false;
    private int timerDialogHoldCount = 0;
    private boolean timeExpiredNotified = false;
    private long timerCheckpointEpochMillis = 0;
    private boolean gameStarted = false;
    private boolean landscapeInfoOpen = false;
    private boolean landscapeControlsOpen = true;
    private String restoredStatusMessage = "";
    private int restoredStatusColor = 0;
    private int layoutGeneration = 0;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(128);
        this.stateStore = new GameStateStore(this);
        this.feedback = new GameFeedback(this);
        loadSettings();
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        bindBackExitHandler();
        if (restorePersistedGameState()) {
            restoreGameScreen(this.restoredStatusMessage, this.restoredStatusColor);
        } else {
            showTeamSetup();
        }
        this.timerHandler.post(this.timerTick);
        enterImmersiveMode();
    }

    private void bindViewsAndActions() {
        this.layoutGeneration++;
        this.textStatus = (TextView) findViewById(R.id.text_status);
        this.textTimer = (TextView) findViewById(R.id.text_timer);
        this.actionLogRail = (LinearLayout) findViewById(R.id.action_log_rail);
        if (this.actionLogRail != null) {
            this.actionLogRail.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda38
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m76lambda$bindViewsAndActions$0$comexampleyutnoriappMainActivity(view);
                }
            });
            this.actionLogRail.setClickable(true);
            this.actionLogRail.setFocusable(true);
        }
        this.btnTimeStop = (TextView) findViewById(R.id.btn_time_stop);
        this.topPanel = findViewById(R.id.top_panel);
        this.boardContainer = (FrameLayout) findViewById(R.id.board_container);
        this.controlPanel = findViewById(R.id.control_panel);
        this.btnToggleInfo = findViewById(R.id.btn_toggle_info);
        this.btnToggleControls = findViewById(R.id.btn_toggle_controls);
        this.resultLayout = (LinearLayout) findViewById(R.id.layout_results);
        this.finishedSummaryLayout = (LinearLayout) findViewById(R.id.layout_finished_summary);
        this.waitingArea = (LinearLayout) findViewById(R.id.waiting_area);
        this.setupPanel = findViewById(R.id.setup_panel);
        this.pieceViews = (TextView[][]) Array.newInstance((Class<?>) TextView.class, 4, 4);
        this.waitSpots = (FrameLayout[][]) Array.newInstance((Class<?>) FrameLayout.class, 4, 4);
        this.finishedCountViews = new TextView[4];
        createPieceViews();
        bindYutButtons();
        bindTeamSetupButtons();
        bindLandscapeDrawerButtons();
        applyButtonContrast();
        applyResponsiveSizing();
    }

    /* JADX INFO: renamed from: lambda$bindViewsAndActions$0$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m76lambda$bindViewsAndActions$0$comexampleyutnoriappMainActivity(View v) {
        showTurnLogDialog();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        this.timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        this.timerHandler.removeCallbacks(this.timerTick);
        this.timerHandler.post(this.timerTick);
        enterImmersiveMode();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onPause() {
        syncTimerToNow();
        persistGameState();
        this.timerHandler.removeCallbacks(this.timerTick);
        super.onPause();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        persistGameState();
        super.onStop();
    }

    @Override // androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onSaveInstanceState(Bundle outState) {
        persistGameState();
        super.onSaveInstanceState(outState);
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration newConfig) {
        String statusMessage;
        int statusColor;
        if (this.textStatus == null) {
            statusMessage = "";
        } else {
            statusMessage = this.textStatus.getText().toString();
        }
        if (this.textStatus == null) {
            statusColor = getResources().getColor(R.color.text_status);
        } else {
            statusColor = this.textStatus.getCurrentTextColor();
        }
        boolean wasGameStarted = this.gameStarted;
        super.onConfigurationChanged(newConfig);
        this.isAnimatingMove = false;
        setContentView(R.layout.activity_main);
        bindViewsAndActions();
        if (wasGameStarted) {
            restoreGameScreen(statusMessage, statusColor);
        } else {
            showTeamSetup();
        }
        enterImmersiveMode();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        this.timerHandler.removeCallbacks(this.timerTick);
        if (this.feedback != null) {
            this.feedback.release();
        }
        super.onDestroy();
    }

    private void createPieceViews() {
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                TextView view = new TextView(this);
                view.setGravity(17);
                view.setText(String.valueOf(id + 1));
                view.setTextColor(-1);
                view.setTextSize(14.0f);
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setBackground(createPieceDrawable(team));
                view.setElevation(dp(7));
                view.setContentDescription(getString(R.string.piece_description, new Object[]{teamName(team), Integer.valueOf(id + 1)}));
                final int teamIndex = team;
                final int pieceIndex = id;
                view.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda40
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view2) {
                        MainActivity.this.m83lambda$createPieceViews$1$comexampleyutnoriappMainActivity(teamIndex, pieceIndex, view2);
                    }
                });
                this.pieceViews[team][id] = view;
            }
        }
    }

    /* JADX INFO: renamed from: lambda$createPieceViews$1$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m83lambda$createPieceViews$1$comexampleyutnoriappMainActivity(int teamIndex, int pieceIndex, View v) {
        selectPiece(teamIndex, pieceIndex);
    }

    private void bindYutButtons() {
        bindPressAction(R.id.btn_bdo, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda41
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m77lambda$bindYutButtons$2$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_do, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m78lambda$bindYutButtons$3$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_gae, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m79lambda$bindYutButtons$4$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_geol, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda5
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m80lambda$bindYutButtons$5$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_yut, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda6
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m81lambda$bindYutButtons$6$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_mo, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda7
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m82lambda$bindYutButtons$7$comexampleyutnoriappMainActivity();
            }
        });
        bindPressAction(R.id.btn_undo_roll, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda8
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.undoLastRoll();
            }
        });
        bindPressAction(R.id.btn_end_turn, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda9
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.endCurrentTurn();
            }
        });
        bindPressAction(R.id.btn_time_stop, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda10
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.toggleTimeStop();
            }
        });
        bindPressAction(R.id.btn_settings, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.showSettingsDialog();
            }
        });
        bindPressAction(R.id.btn_setup_settings, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.showSettingsDialog();
            }
        });
        bindPressAction(R.id.btn_restart, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.requestNewGame();
            }
        });
        setContentDescriptionIfPresent(R.id.btn_bdo, getString(R.string.yut_backdo_description));
        setContentDescriptionIfPresent(R.id.btn_do, getString(R.string.yut_do_description));
        setContentDescriptionIfPresent(R.id.btn_gae, getString(R.string.yut_gae_description));
        setContentDescriptionIfPresent(R.id.btn_geol, getString(R.string.yut_geol_description));
        setContentDescriptionIfPresent(R.id.btn_yut, getString(R.string.yut_yut_description));
        setContentDescriptionIfPresent(R.id.btn_mo, getString(R.string.yut_mo_description));
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$2$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m77lambda$bindYutButtons$2$comexampleyutnoriappMainActivity() {
        handleYutInput(-1);
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$3$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m78lambda$bindYutButtons$3$comexampleyutnoriappMainActivity() {
        handleYutInput(1);
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$4$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m79lambda$bindYutButtons$4$comexampleyutnoriappMainActivity() {
        handleYutInput(2);
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$5$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m80lambda$bindYutButtons$5$comexampleyutnoriappMainActivity() {
        handleYutInput(3);
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$6$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m81lambda$bindYutButtons$6$comexampleyutnoriappMainActivity() {
        handleYutInput(4);
    }

    /* JADX INFO: renamed from: lambda$bindYutButtons$7$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m82lambda$bindYutButtons$7$comexampleyutnoriappMainActivity() {
        handleYutInput(5);
    }

    private void bindTeamSetupButtons() {
        bindPressAction(R.id.btn_team_2, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda30
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m74xae6dd920();
            }
        });
        bindPressAction(R.id.btn_team_3, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda31
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m75xc215aca1();
            }
        });
        bindPressAction(R.id.btn_team_4, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda32
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m73xe06fd979();
            }
        });
    }

    /* JADX INFO: renamed from: lambda$bindTeamSetupButtons$8$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m74xae6dd920() {
        startGame(2);
    }

    /* JADX INFO: renamed from: lambda$bindTeamSetupButtons$9$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m75xc215aca1() {
        startGame(3);
    }

    /* JADX INFO: renamed from: lambda$bindTeamSetupButtons$10$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m73xe06fd979() {
        startGame(4);
    }

    private void bindLandscapeDrawerButtons() {
        if (!hasLandscapeDrawers()) {
            return;
        }
        bindPressAction(R.id.btn_toggle_info, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda14
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m70x8867e0e6();
            }
        });
        bindPressAction(R.id.btn_toggle_controls, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda15
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m71x9c0fb467();
            }
        });
    }

    /* JADX INFO: renamed from: lambda$bindLandscapeDrawerButtons$11$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m70x8867e0e6() {
        this.landscapeInfoOpen = !this.landscapeInfoOpen;
        applyLandscapeDrawerState(true);
    }

    /* JADX INFO: renamed from: lambda$bindLandscapeDrawerButtons$12$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m71x9c0fb467() {
        this.landscapeControlsOpen = !this.landscapeControlsOpen;
        applyLandscapeDrawerState(true);
    }

    private void applyButtonContrast() {
        int darkText = getResources().getColor(R.color.ink_black);
        int lightText = getResources().getColor(R.color.btn_text_color);
        int[] darkButtonIds = {R.id.btn_bdo, R.id.btn_do, R.id.btn_gae, R.id.btn_geol, R.id.btn_yut, R.id.btn_mo, R.id.btn_time_stop, R.id.btn_undo_roll, R.id.btn_team_2, R.id.btn_team_3, R.id.btn_team_4};
        int[] lightButtonIds = {R.id.btn_end_turn, R.id.btn_restart};
        for (int id : darkButtonIds) {
            TextView button = (TextView) findViewById(id);
            if (button != null) {
                button.setTextColor(darkText);
                button.setBackgroundTintList(null);
            }
        }
        for (int id2 : lightButtonIds) {
            TextView button2 = (TextView) findViewById(id2);
            if (button2 != null) {
                button2.setTextColor(lightText);
                button2.setBackgroundTintList(null);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTeamSetup() {
        this.gameStarted = false;
        this.isAnimatingMove = false;
        this.isTimerHeldForAnimation = false;
        pauseTimer();
        clearSelectedPiece();
        clearMovePreviews();
        this.resultLayout.removeAllViews();
        this.finishedSummaryLayout.removeAllViews();
        this.waitingArea.removeAllViews();
        removeAllPiecesFromScreen();
        this.topPanel.setVisibility(4);
        this.boardContainer.setVisibility(4);
        this.controlPanel.setVisibility(4);
        setLandscapeDrawerTabsVisible(false);
        this.setupPanel.setVisibility(0);
        this.setupPanel.setElevation(dp(24));
        this.setupPanel.bringToFront();
        updateTurnLogView();
        persistGameState();
        enterImmersiveMode();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void requestNewGame() {
        if (!this.gameStarted || this.game.isGameOver()) {
            showTeamSetup();
        } else {
            beginTimerDialogHold();
            YutDialogs.showNewGameConfirmation(this, new MainActivity$$ExternalSyntheticLambda16(this), new MainActivity$$ExternalSyntheticLambda17(this));
        }
    }

    private void startGame(int teamCount) {
        YutGameEngine.ActionResult result = this.game.setTeamCount(teamCount);
        if (!result.success) {
            showToast(result.message);
            return;
        }
        this.gameStarted = true;
        this.setupPanel.setVisibility(8);
        this.topPanel.setVisibility(0);
        this.boardContainer.setVisibility(0);
        this.controlPanel.setVisibility(0);
        resetGame();
        prepareLandscapeDrawers(true);
        persistGameState();
        enterImmersiveMode();
    }

    private void restoreGameScreen(String statusMessage, int statusColor) {
        this.isTimerHeldForAnimation = false;
        this.setupPanel.setVisibility(8);
        this.topPanel.setVisibility(0);
        this.boardContainer.setVisibility(0);
        this.controlPanel.setVisibility(0);
        setLandscapeDrawerTabsVisible(true);
        this.resultLayout.removeAllViews();
        buildTeamAreas();
        this.boardContainer.post(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda13
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m87lambda$restoreGameScreen$13$comexampleyutnoriappMainActivity();
            }
        });
        boolean zIsEmpty = statusMessage.isEmpty();
        TextView textView = this.textStatus;
        if (zIsEmpty) {
            textView.setText(getString(R.string.team_turn, new Object[]{teamName(this.game.getCurrentTeam())}));
            this.textStatus.setTextColor(getStatusColor());
        } else {
            textView.setText(statusMessage);
            this.textStatus.setTextColor(statusColor);
        }
        updateResultButtons();
        updateTimerView();
        updateTurnLogView();
        setControlsEnabled(!this.game.isGameOver());
        if (this.game.isGameOver()) {
            findViewById(R.id.btn_restart).setEnabled(true);
        }
        prepareLandscapeDrawers(false);
    }

    /* JADX INFO: renamed from: lambda$restoreGameScreen$13$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m87lambda$restoreGameScreen$13$comexampleyutnoriappMainActivity() {
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                Piece piece = this.game.getPiece(team, id);
                if (team >= this.game.getTeamCount()) {
                    removeFromParent(this.pieceViews[team][id]);
                } else if (piece.isFinished) {
                    moveToFinishedArea(team, id);
                } else if (piece.position == -1) {
                    if (team == this.game.getCurrentTeam()) {
                        moveToWaitSpot(team, id);
                    } else {
                        removeFromParent(this.pieceViews[team][id]);
                    }
                }
            }
        }
        normalizeSelectedBoardPiece();
        renderBoardPiecesNow();
        updateFinishedSummary();
        updatePieceSelectionStyles();
        updateMovePreviews();
    }

    private void resetGame() {
        this.isAnimatingMove = false;
        this.isTimerHeldForAnimation = false;
        this.game.reset();
        this.resultLayout.removeAllViews();
        buildTeamAreas();
        this.textStatus.setText(R.string.initial_status);
        this.textStatus.setTextColor(getStatusColor());
        this.textStatus.animate().cancel();
        this.textStatus.setScaleX(1.0f);
        this.textStatus.setScaleY(1.0f);
        clearSelectedPiece();
        clearMovePreviews();
        setControlsEnabled(true);
        startTurnTimer();
        this.turnLog.clear();
        addTurnLog(getString(R.string.log_new_game, new Object[]{teamName(0)}));
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                if (team == this.game.getCurrentTeam()) {
                    moveToWaitSpot(team, id);
                } else {
                    removeFromParent(this.pieceViews[team][id]);
                }
            }
        }
    }

    private void buildTeamAreas() {
        this.finishedSummaryLayout.removeAllViews();
        boolean landscape = getResources().getConfiguration().orientation == 2;
        this.finishedSummaryLayout.setOrientation(landscape ? 1 : 0);
        this.waitingArea.removeAllViews();
        this.waitingArea.setOrientation(1);
        for (int team = 0; team < 4; team++) {
            this.finishedCountViews[team] = null;
        }
        for (int team2 = 0; team2 < this.game.getTeamCount(); team2++) {
            createFinishedTeamSummary(team2, landscape);
        }
        updateFinishedSummary();
        rebuildCurrentWaitingArea();
    }

    private void createFinishedTeamSummary(int team, boolean landscape) {
        LinearLayout.LayoutParams params;
        TextView summary = new TextView(this);
        summary.setGravity((landscape ? GravityCompat.START : 1) | 16);
        summary.setIncludeFontPadding(false);
        summary.setTextColor(getTeamColor(team));
        summary.setTextSize(12.0f);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setPadding(dp(4), 0, dp(4), 0);
        this.finishedCountViews[team] = summary;
        int summaryHeight = dp(isCompactPortrait() ? 24 : 28);
        if (landscape) {
            params = new LinearLayout.LayoutParams(-1, summaryHeight);
        } else {
            params = new LinearLayout.LayoutParams(0, summaryHeight, 1.0f);
        }
        this.finishedSummaryLayout.addView(summary, params);
    }

    private void updateFinishedSummary() {
        if (this.finishedCountViews == null) {
            return;
        }
        boolean landscape = getResources().getConfiguration().orientation == 2;
        for (int team = 0; team < this.game.getTeamCount(); team++) {
            TextView summary = this.finishedCountViews[team];
            if (summary != null) {
                int finishedCount = 0;
                for (int id = 0; id < 4; id++) {
                    if (this.game.getPiece(team, id).isFinished) {
                        finishedCount++;
                    }
                }
                String summaryText = getString(landscape ? R.string.finished_summary_landscape : R.string.finished_summary_portrait, new Object[]{teamName(team), Integer.valueOf(finishedCount), 4});
                summary.setText(summaryText);
                summary.setContentDescription(getString(R.string.finished_summary_description, new Object[]{teamName(team), Integer.valueOf(finishedCount), 4}));
            }
        }
    }

    private void createWaitingTeamRow(final int team) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setGravity(16);
        linearLayout.setOrientation(0);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(44));
        if (team > 0) {
            rowParams.setMargins(0, dp(5), 0, 0);
        }
        this.waitingArea.addView(linearLayout, rowParams);
        TextView label = new TextView(this);
        label.setText(teamName(team));
        label.setTextColor(getTeamColor(team));
        label.setTextSize(13.0f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        linearLayout.addView(label, new LinearLayout.LayoutParams(dp(56), -2));
        LinearLayout spots = new LinearLayout(this);
        spots.setGravity(16);
        spots.setOrientation(0);
        linearLayout.addView(spots, new LinearLayout.LayoutParams(0, -1, 1.0f));
        for (int id = 0; id < 4; id++) {
            FrameLayout spot = new FrameLayout(this);
            spot.setBackgroundResource(R.drawable.shape_wait_spot);
            spot.setClipChildren(false);
            spot.setClipToPadding(false);
            final int pieceId = id;
            spot.setContentDescription(getString(R.string.waiting_piece_description, new Object[]{teamName(team), Integer.valueOf(id + 1)}));
            spot.setClickable(true);
            spot.setFocusable(true);
            spot.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda21
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m84x5b28bc0f(team, pieceId, view);
                }
            });
            LinearLayout.LayoutParams spotParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            spots.addView(spot, spotParams);
            this.waitSpots[team][id] = spot;
        }
    }

    /* JADX INFO: renamed from: lambda$createWaitingTeamRow$14$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m84x5b28bc0f(int team, int pieceId, View v) {
        selectPiece(team, pieceId);
    }

    private void rebuildCurrentWaitingArea() {
        this.waitingArea.removeAllViews();
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                this.waitSpots[team][id] = null;
            }
        }
        if (this.game.isGameOver()) {
            return;
        }
        createWaitingTeamRow(this.game.getCurrentTeam());
        syncWaitingPiecesForCurrentTurn();
    }

    private void syncWaitingPiecesForCurrentTurn() {
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                Piece piece = this.game.getPiece(team, id);
                if (team < this.game.getTeamCount() && !piece.isFinished && piece.position == -1) {
                    if (team == this.game.getCurrentTeam()) {
                        moveToWaitSpot(team, id);
                    } else {
                        removeFromParent(this.pieceViews[team][id]);
                    }
                }
            }
        }
    }

    private void handleYutInput(int steps) {
        if (this.isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        YutGameEngine.ActionResult result = this.game.addRoll(steps);
        if (!result.success) {
            showToast(result.message);
            return;
        }
        selectSinglePendingResult();
        this.textStatus.setText(getRollStatusMessage(steps, result.message));
        this.textStatus.setTextColor(getStatusColor());
        if (this.game.isRollAllowed()) {
            addBonusTime();
        }
        addTurnLog(getString(R.string.log_roll, new Object[]{teamName(this.game.getCurrentTeam()), resultName(steps)}));
        playFeedback(0);
        updateResultButtons();
        updateMovePreviews();
        persistGameState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void undoLastRoll() {
        if (this.isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        YutGameEngine.ActionResult result = this.game.undoLastRoll();
        if (!result.success) {
            showToast(result.message);
            return;
        }
        selectSinglePendingResult();
        this.textStatus.setText(result.message);
        this.textStatus.setTextColor(getStatusColor());
        addTurnLog(getString(R.string.log_undo_roll, new Object[]{teamName(this.game.getCurrentTeam())}));
        updateResultButtons();
        updateMovePreviews();
        playFeedback(0);
        persistGameState();
    }

    private void selectSinglePendingResult() {
        if (this.game.getPendingResults().size() == 1 && this.game.getSelectedSteps().isEmpty()) {
            this.game.selectResult(0);
        }
    }

    private String getRollStatusMessage(int steps, String fallbackMessage) {
        if (this.game.getPendingResults().size() != 1 || this.game.getSelectedSteps().isEmpty()) {
            return fallbackMessage;
        }
        String stepLabel = steps > 0 ? "+" + steps : String.valueOf(steps);
        return getString(this.game.isRollAllowed() ? R.string.roll_status_bonus : R.string.roll_status_move, new Object[]{resultName(steps), stepLabel});
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void endCurrentTurn() {
        if (this.isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        String endedTeamName = teamName(this.game.getCurrentTeam());
        YutGameEngine.ActionResult result = this.game.endTurn();
        if (!result.success) {
            showToast(result.message);
            return;
        }
        this.textStatus.setText(result.message);
        this.textStatus.setTextColor(getStatusColor());
        addTurnLog(getString(R.string.log_turn_end, new Object[]{endedTeamName}));
        clearSelectedPiece();
        clearMovePreviews();
        this.resultLayout.removeAllViews();
        rebuildCurrentWaitingArea();
        startTurnTimer();
        playFeedback(0);
        persistGameState();
    }

    private void updateResultButtons() {
        this.resultLayout.removeAllViews();
        List<YutGameEngine.MoveChoice> moveChoices = this.game.getMoveChoices();
        for (int i = 0; i < moveChoices.size(); i++) {
            YutGameEngine.MoveChoice choice = moveChoices.get(i);
            Button button = new Button(this);
            button.setText(choice.label);
            button.setAllCaps(false);
            button.setTextColor(getResources().getColor(R.color.text_primary));
            button.setTextSize(moveChoices.size() > 1 ? 13.0f : 15.0f);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setBackgroundResource(R.drawable.shape_result_button);
            button.setBackgroundTintList(null);
            button.setStateListAnimator(null);
            button.setSelected(choice.selectionOrder > 0);
            button.setEnabled(!this.isAnimatingMove);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(moveChoices.size() > 1 ? 86 : 72), dp(44));
            params.setMargins(0, 0, dp(6), 0);
            button.setLayoutParams(params);
            final int index = i;
            button.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda29
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m91x808bff7c(index, view);
                }
            });
            this.resultLayout.addView(button);
            button.setScaleX(0.94f);
            button.setScaleY(0.94f);
            button.setAlpha(0.8f);
            button.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(140L).start();
        }
    }

    /* JADX INFO: renamed from: lambda$updateResultButtons$16$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m91x808bff7c(final int index, View v) {
        m72lambda$bindPressAction$27$comexampleyutnoriappMainActivity(v, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda12
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m90x6ce42bfb(index);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: onResultClick, reason: merged with bridge method [inline-methods] */
    public void m90x6ce42bfb(int index) {
        String waitingPieceBackDoMessage;
        if (this.isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        YutGameEngine.ActionResult result = this.game.selectResult(index);
        if (!result.success) {
            showToast(result.message);
            return;
        }
        boolean unavailableSelectedPiece = false;
        if (this.game.getSelectedSteps().isEmpty()) {
            clearSelectedPiece();
        } else if (this.selectedPreviewTeamId != -1 && this.selectedPreviewPieceId != -1 && !this.game.previewMove(this.selectedPreviewTeamId, this.selectedPreviewPieceId).available) {
            unavailableSelectedPiece = true;
            clearSelectedPiece();
        }
        TextView textView = this.textStatus;
        if (unavailableSelectedPiece) {
            waitingPieceBackDoMessage = this.game.getWaitingPieceBackDoMessage();
        } else {
            waitingPieceBackDoMessage = result.message;
        }
        textView.setText(waitingPieceBackDoMessage);
        this.textStatus.setTextColor(getStatusColor());
        updateResultButtons();
        updateMovePreviews();
        playFeedback(0);
        persistGameState();
    }

    private void selectPiece(int teamId, int pieceId) {
        if (this.isAnimatingMove) {
            showToast(getString(R.string.piece_moving));
            return;
        }
        if (teamId != this.game.getCurrentTeam()) {
            showToast(getString(R.string.team_turn, new Object[]{teamName(this.game.getCurrentTeam())}));
            return;
        }
        if (this.game.getSelectedSteps().isEmpty()) {
            clearSelectedPiece();
            clearMovePreviews();
            this.textStatus.setText(R.string.select_result_first);
            this.textStatus.setTextColor(getStatusColor());
            persistGameState();
            return;
        }
        YutGameEngine.MovePreview preview = this.game.previewMove(teamId, pieceId);
        if (!preview.available) {
            clearSelectedPiece();
            clearMovePreviews();
            this.textStatus.setText(this.game.getWaitingPieceBackDoMessage());
            this.textStatus.setTextColor(getStatusColor());
            playFeedback(0);
            persistGameState();
            return;
        }
        boolean selectionChanged = (this.selectedPreviewTeamId == teamId && this.selectedPreviewPieceId == pieceId) ? false : true;
        this.selectedPreviewTeamId = teamId;
        this.selectedPreviewPieceId = pieceId;
        updatePieceSelectionStyles();
        updateMovePreviews();
        this.textStatus.setText(R.string.tap_destination);
        this.textStatus.setTextColor(getStatusColor());
        if (selectionChanged) {
            playFeedback(0);
        }
        persistGameState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void commitSelectedMove() {
        if (this.isAnimatingMove || this.selectedPreviewTeamId != this.game.getCurrentTeam() || this.selectedPreviewPieceId == -1) {
            return;
        }
        String planText = getSelectedStepsText();
        YutGameEngine.MoveResult result = this.game.moveSelectedPiece(this.selectedPreviewTeamId, this.selectedPreviewPieceId);
        if (!result.success) {
            showToast(result.message);
        } else {
            applyMoveResult(result, planText);
        }
    }

    private void applyMoveResult(final YutGameEngine.MoveResult result, final String planText) {
        playFeedback(1);
        this.isAnimatingMove = true;
        this.isTimerHeldForAnimation = true;
        setControlsEnabled(false);
        clearSelectedPiece();
        clearMovePreviews();
        animateMoveResult(result, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda24
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m69lambda$applyMoveResult$17$comexampleyutnoriappMainActivity(result, planText);
            }
        });
    }

    /* JADX INFO: renamed from: lambda$applyMoveResult$17$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m69lambda$applyMoveResult$17$comexampleyutnoriappMainActivity(YutGameEngine.MoveResult result, String planText) {
        for (YutGameEngine.PieceRef caughtPiece : result.caughtPieces) {
            moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
        }
        Iterator<Integer> it = result.finishedPieceIds.iterator();
        while (it.hasNext()) {
            int id = it.next().intValue();
            moveToFinishedArea(result.teamId, id);
        }
        this.isAnimatingMove = false;
        this.isTimerHeldForAnimation = false;
        renderBoardPiecesNow();
        updateFinishedSummary();
        rebuildCurrentWaitingArea();
        updateResultButtons();
        setControlsEnabled(true);
        updateStatusAfterMove(result);
        addTurnLog(formatMoveLog(result, planText));
        if (result.gameWon) {
            playFeedback(3);
            setControlsEnabled(false);
            findViewById(R.id.btn_restart).setEnabled(true);
            persistGameState();
            showGameOverDialog(result);
            return;
        }
        if (result.turnChanged) {
            startTurnTimer();
        } else if (result.caught) {
            playFeedback(2);
            addBonusTime();
        }
        updateMovePreviews();
        persistGameState();
    }

    private void updateStatusAfterMove(YutGameEngine.MoveResult result) {
        this.textStatus.setText(result.message);
        this.textStatus.setTextColor(result.gameWon ? getResources().getColor(R.color.btn_restart) : getStatusColor());
        if (result.gameWon) {
            this.textStatus.animate().cancel();
            this.textStatus.setScaleX(1.08f);
            this.textStatus.setScaleY(1.08f);
        } else if (result.turnChanged) {
            this.textStatus.animate().scaleX(1.06f).scaleY(1.06f).setDuration(180L).withEndAction(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda36
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m92x499d7dc7();
                }
            }).start();
        }
    }

    /* JADX INFO: renamed from: lambda$updateStatusAfterMove$18$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m92x499d7dc7() {
        this.textStatus.animate().scaleX(1.0f).scaleY(1.0f).setDuration(180L).start();
    }

    private void showGameOverDialog(YutGameEngine.MoveResult result) {
        YutDialogs.showGameOver(this, getString(R.string.victory_title, new Object[]{teamName(result.teamId)}), buildGameOverMessage(result), new MainActivity$$ExternalSyntheticLambda16(this), new MainActivity$$ExternalSyntheticLambda25(this));
    }

    private void animateMoveResult(final YutGameEngine.MoveResult result, final Runnable onComplete) {
        if (result.animationSegments.isEmpty()) {
            onComplete.run();
        } else {
            final int animationGeneration = this.layoutGeneration;
            this.boardContainer.post(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda34
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m65lambda$animateMoveResult$19$comexampleyutnoriappMainActivity(animationGeneration, result, onComplete);
                }
            });
        }
    }

    /* JADX INFO: renamed from: lambda$animateMoveResult$19$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m65lambda$animateMoveResult$19$comexampleyutnoriappMainActivity(int animationGeneration, YutGameEngine.MoveResult result, Runnable onComplete) {
        if (animationGeneration == this.layoutGeneration) {
            animateMoveSegment(result, 0, onComplete, animationGeneration);
        }
    }

    private void animateMoveSegment(final YutGameEngine.MoveResult result, final int segmentIndex, final Runnable onComplete, final int animationGeneration) {
        if (animationGeneration != this.layoutGeneration) {
            return;
        }
        if (segmentIndex >= result.animationSegments.size()) {
            onComplete.run();
            return;
        }
        final YutGameEngine.MoveAnimation segment = result.animationSegments.get(segmentIndex);
        ArrayList<Integer> boardPath = new ArrayList<>();
        Iterator<Integer> it = segment.path.iterator();
        while (it.hasNext()) {
            int node = it.next().intValue();
            if (node != -1 && node != 30) {
                boardPath.add(Integer.valueOf(node));
            }
        }
        Runnable nextSegment = new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda23
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m66lambda$animateMoveSegment$20$comexampleyutnoriappMainActivity(animationGeneration, segment, result, segmentIndex, onComplete);
            }
        };
        if (segment.pieceIds.isEmpty() || boardPath.isEmpty()) {
            nextSegment.run();
        } else {
            int representativeId = prepareAnimatedBoardGroup(result.teamId, segment.pieceIds, segment.startNode);
            animatePathStep(result.teamId, representativeId, boardPath, 0, nextSegment, animationGeneration);
        }
    }

    /* JADX INFO: renamed from: lambda$animateMoveSegment$20$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m66lambda$animateMoveSegment$20$comexampleyutnoriappMainActivity(int animationGeneration, YutGameEngine.MoveAnimation segment, YutGameEngine.MoveResult result, int segmentIndex, Runnable onComplete) {
        if (animationGeneration != this.layoutGeneration) {
            return;
        }
        for (YutGameEngine.PieceRef caughtPiece : segment.caughtPieces) {
            moveToWaitSpot(caughtPiece.teamId, caughtPiece.pieceId);
        }
        animateMoveSegment(result, segmentIndex + 1, onComplete, animationGeneration);
    }

    private void animatePathStep(final int teamId, final int pieceId, final List<Integer> path, final int index, final Runnable onComplete, final int animationGeneration) {
        if (animationGeneration != this.layoutGeneration) {
            return;
        }
        if (index >= path.size()) {
            ArrayList<Integer> animatedPiece = new ArrayList<>();
            animatedPiece.add(Integer.valueOf(pieceId));
            bounceArrivedPieces(teamId, animatedPiece, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda11
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m67lambda$animatePathStep$21$comexampleyutnoriappMainActivity(animationGeneration, onComplete);
                }
            });
        } else {
            int node = path.get(index).intValue();
            int duration = Math.max(110, 190 - (Math.min(index, 4) * 12));
            Runnable nextStep = new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda22
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m68lambda$animatePathStep$22$comexampleyutnoriappMainActivity(teamId, pieceId, path, index, onComplete, animationGeneration);
                }
            };
            TextView pieceView = this.pieceViews[teamId][pieceId];
            float[] target = getBoardNodePosition(node, getBoardPieceSize());
            pieceView.animate().x(target[0]).y(target[1]).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(duration).withEndAction(nextStep).start();
        }
    }

    /* JADX INFO: renamed from: lambda$animatePathStep$21$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m67lambda$animatePathStep$21$comexampleyutnoriappMainActivity(int animationGeneration, Runnable onComplete) {
        if (animationGeneration == this.layoutGeneration) {
            onComplete.run();
        }
    }

    /* JADX INFO: renamed from: lambda$animatePathStep$22$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m68lambda$animatePathStep$22$comexampleyutnoriappMainActivity(int teamId, int pieceId, List path, int index, Runnable onComplete, int animationGeneration) {
        animatePathStep(teamId, pieceId, path, index + 1, onComplete, animationGeneration);
    }

    private int prepareAnimatedBoardGroup(int teamId, List<Integer> pieceIds, int logicalNode) {
        int representativeId = getRepresentativePieceId(pieceIds);
        Iterator<Integer> it = pieceIds.iterator();
        while (it.hasNext()) {
            int id = it.next().intValue();
            if (id != representativeId) {
                removeFromParent(this.pieceViews[teamId][id]);
            }
        }
        TextView representative = this.pieceViews[teamId][representativeId];
        resetPieceViewTransform(representative);
        moveViewToParent(representative, this.boardContainer);
        configureBoardGroupAppearance(representative, teamId, representativeId, pieceIds.size());
        int size = getBoardPieceSize();
        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        placePieceOnBoardNode(representative, logicalNode, size);
        return representativeId;
    }

    private void renderBoardPiecesNow() {
        for (int team = 0; team < this.game.getTeamCount(); team++) {
            for (int id = 0; id < 4; id++) {
                Piece piece = this.game.getPiece(team, id);
                if (!piece.isFinished && piece.position != -1 && piece.position != 30) {
                    ArrayList<Integer> groupIds = getBoardGroupIds(team, piece.position);
                    int representativeId = getRepresentativePieceId(groupIds);
                    TextView[][] textViewArr = this.pieceViews;
                    if (id != representativeId) {
                        removeFromParent(textViewArr[team][id]);
                    } else {
                        TextView representative = textViewArr[team][representativeId];
                        resetPieceViewTransform(representative);
                        moveViewToParent(representative, this.boardContainer);
                        configureBoardGroupAppearance(representative, team, representativeId, groupIds.size());
                        int size = getBoardPieceSize();
                        representative.setLayoutParams(new FrameLayout.LayoutParams(size, size));
                        placePieceOnBoardNode(representative, piece.position, size);
                    }
                }
            }
        }
    }

    private ArrayList<Integer> getBoardGroupIds(int teamId, int logicalNode) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (int id = 0; id < 4; id++) {
            Piece piece = this.game.getPiece(teamId, id);
            if (!piece.isFinished && this.game.isSameBoardSpot(piece.position, logicalNode)) {
                ids.add(Integer.valueOf(id));
            }
        }
        return ids;
    }

    private int getRepresentativePieceId(List<Integer> pieceIds) {
        int representativeId = pieceIds.get(0).intValue();
        Iterator<Integer> it = pieceIds.iterator();
        while (it.hasNext()) {
            int id = it.next().intValue();
            representativeId = Math.min(representativeId, id);
        }
        return representativeId;
    }

    private void normalizeSelectedBoardPiece() {
        if (this.selectedPreviewTeamId < 0 || this.selectedPreviewPieceId < 0) {
            return;
        }
        Piece selected = this.game.getPiece(this.selectedPreviewTeamId, this.selectedPreviewPieceId);
        if (selected.isFinished || selected.position == -1) {
            return;
        }
        this.selectedPreviewPieceId = getRepresentativePieceId(getBoardGroupIds(this.selectedPreviewTeamId, selected.position));
    }

    private void configureBoardGroupAppearance(TextView view, int teamId, int pieceId, int groupCount) {
        String string;
        view.setVisibility(0);
        view.setText(groupCount > 1 ? "×" + groupCount : String.valueOf(pieceId + 1));
        view.setTextSize(groupCount > 1 ? 15.0f : 14.0f);
        view.setElevation(dp(groupCount > 1 ? 9 : 7));
        if (groupCount > 1) {
            string = getString(R.string.grouped_piece_description, new Object[]{teamName(teamId), Integer.valueOf(groupCount)});
        } else {
            string = getString(R.string.piece_description, new Object[]{teamName(teamId), Integer.valueOf(pieceId + 1)});
        }
        view.setContentDescription(string);
    }

    private void restorePieceIdentity(TextView view, int teamId, int pieceId) {
        view.setVisibility(0);
        view.setText(String.valueOf(pieceId + 1));
        view.setTextSize(14.0f);
        view.setElevation(dp(7));
        view.setContentDescription(getString(R.string.piece_description, new Object[]{teamName(teamId), Integer.valueOf(pieceId + 1)}));
    }

    private void placePieceOnBoardNode(View pieceView, int logicalNode, int size) {
        float[] position = getBoardNodePosition(logicalNode, size);
        pieceView.setX(position[0]);
        pieceView.setY(position[1]);
    }

    private float[] getBoardNodePosition(int logicalNode, int size) {
        int spotIndex = logicalNode == -1 ? 15 : this.game.visualSpotFor(logicalNode);
        if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
            spotIndex = 15;
        }
        float[] point = BoardGeometry.POINTS[spotIndex];
        float x = (point[0] * this.boardContainer.getWidth()) - (size / 2.0f);
        float y = (point[1] * this.boardContainer.getHeight()) - (size / 2.0f);
        return new float[]{x, y};
    }

    private void updateMovePreviews() {
        clearMovePreviews();
        if (this.game.getSelectedSteps().isEmpty() || this.game.isGameOver() || this.selectedPreviewTeamId != this.game.getCurrentTeam() || this.selectedPreviewPieceId == -1) {
            return;
        }
        this.boardContainer.post(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda37
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m89lambda$updateMovePreviews$23$comexampleyutnoriappMainActivity();
            }
        });
    }

    /* JADX INFO: renamed from: lambda$updateMovePreviews$23$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m89lambda$updateMovePreviews$23$comexampleyutnoriappMainActivity() {
        YutGameEngine.MovePreview preview = this.game.previewMove(this.selectedPreviewTeamId, this.selectedPreviewPieceId);
        if (!preview.available) {
            return;
        }
        int spotIndex = 15;
        if (preview.finishes) {
            spotIndex = this.game.visualSpotFor(15);
        } else if (preview.targetNode != -1) {
            spotIndex = this.game.visualSpotFor(preview.targetNode);
        }
        if (spotIndex < 0 || spotIndex >= BoardGeometry.POINTS.length) {
            return;
        }
        addPreviewGlow(spotIndex);
    }

    private void addPreviewGlow(int spotIndex) {
        TextView glow = new TextView(this);
        glow.setText("↓");
        glow.setGravity(17);
        glow.setTextColor(getResources().getColor(R.color.text_primary));
        glow.setTextSize(17.0f);
        glow.setTypeface(Typeface.DEFAULT_BOLD);
        glow.setBackground(createPreviewDrawable());
        glow.setElevation(dp(8));
        glow.setClickable(true);
        glow.setFocusable(true);
        glow.setContentDescription(getString(R.string.destination_description));
        glow.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda26
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m64lambda$addPreviewGlow$24$comexampleyutnoriappMainActivity(view);
            }
        });
        int size = Math.max(dp(48), getBoardPieceSize() + dp(10));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        this.boardContainer.addView(glow, params);
        float[] point = BoardGeometry.POINTS[spotIndex];
        float x = (point[0] * this.boardContainer.getWidth()) - (size / 2.0f);
        float y = (point[1] * this.boardContainer.getHeight()) - (size / 2.0f);
        glow.setX(x);
        glow.setY(y);
        this.previewViews.add(glow);
    }

    /* JADX INFO: renamed from: lambda$addPreviewGlow$24$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m64lambda$addPreviewGlow$24$comexampleyutnoriappMainActivity(View v) {
        m72lambda$bindPressAction$27$comexampleyutnoriappMainActivity(v, new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda27
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.commitSelectedMove();
            }
        });
    }

    private void clearMovePreviews() {
        for (View previewView : this.previewViews) {
            ViewGroup parent = (ViewGroup) previewView.getParent();
            if (parent != null) {
                parent.removeView(previewView);
            }
        }
        this.previewViews.clear();
    }

    private void clearSelectedPiece() {
        this.selectedPreviewTeamId = -1;
        this.selectedPreviewPieceId = -1;
        updatePieceSelectionStyles();
    }

    private void updatePieceSelectionStyles() {
        int team = 0;
        while (team < 4) {
            int id = 0;
            while (id < 4) {
                TextView pieceView = this.pieceViews[team][id];
                boolean selected = team == this.selectedPreviewTeamId && id == this.selectedPreviewPieceId;
                pieceView.setScaleX(selected ? 1.12f : 1.0f);
                pieceView.setScaleY(selected ? 1.12f : 1.0f);
                pieceView.setElevation(dp(selected ? 10 : 7));
                id++;
            }
            team++;
        }
    }

    private void moveToWaitSpot(int teamId, int pieceId) {
        TextView pieceView = this.pieceViews[teamId][pieceId];
        ViewGroup waitSpot = this.waitSpots[teamId][pieceId];
        if (waitSpot == null) {
            removeFromParent(pieceView);
            return;
        }
        resetPieceViewTransform(pieceView);
        restorePieceIdentity(pieceView, teamId, pieceId);
        moveViewToParent(pieceView, waitSpot);
        ViewGroup.LayoutParams params = new FrameLayout.LayoutParams(dp(36), dp(36), 17);
        pieceView.setLayoutParams(params);
        resetPieceViewTransform(pieceView);
    }

    private void moveToFinishedArea(int teamId, int pieceId) {
        TextView pieceView = this.pieceViews[teamId][pieceId];
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

    private GradientDrawable createPieceDrawable(int teamId) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(1);
        drawable.setColors(new int[]{getResources().getColor(getTeamBrightColorRes(teamId)), getResources().getColor(getTeamColorRes(teamId))});
        drawable.setGradientType(1);
        drawable.setGradientRadius(dp(34));
        drawable.setStroke(dp(2), -1);
        return drawable;
    }

    private GradientDrawable createPreviewDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(1);
        drawable.setColor(-352794802);
        drawable.setStroke(dp(2), -1);
        return drawable;
    }

    private int getBoardPieceSize() {
        int byBoard = Math.round(this.boardContainer.getWidth() * 0.1f);
        return Math.max(dp(32), Math.min(dp(46), byBoard));
    }

    private int getStatusColor() {
        return getResources().getColor(getTeamColorRes(this.game.getCurrentTeam()));
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
        return (this.btnToggleInfo == null || this.btnToggleControls == null) ? false : true;
    }

    private void prepareLandscapeDrawers(boolean resetToDefault) {
        if (!hasLandscapeDrawers()) {
            return;
        }
        if (resetToDefault) {
            this.landscapeInfoOpen = false;
            this.landscapeControlsOpen = true;
        }
        setLandscapeDrawerTabsVisible(true);
        this.topPanel.bringToFront();
        this.controlPanel.bringToFront();
        this.btnToggleInfo.bringToFront();
        this.btnToggleControls.bringToFront();
        this.setupPanel.bringToFront();
        this.topPanel.post(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda19
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m85x231bd387();
            }
        });
        this.controlPanel.post(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda20
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m86x36c3a708();
            }
        });
    }

    /* JADX INFO: renamed from: lambda$prepareLandscapeDrawers$25$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m85x231bd387() {
        applyLandscapeDrawerState(false);
    }

    /* JADX INFO: renamed from: lambda$prepareLandscapeDrawers$26$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m86x36c3a708() {
        applyLandscapeDrawerState(false);
    }

    private void setLandscapeDrawerTabsVisible(boolean visible) {
        if (!hasLandscapeDrawers()) {
            return;
        }
        int visibility = visible ? 0 : 8;
        this.btnToggleInfo.setVisibility(visibility);
        this.btnToggleControls.setVisibility(visibility);
    }

    private void applyLandscapeDrawerState(boolean animate) {
        if (!hasLandscapeDrawers()) {
            return;
        }
        int leftWidth = Math.max(this.topPanel.getWidth(), dp(226));
        int rightWidth = Math.max(this.controlPanel.getWidth(), dp(292));
        float infoX = this.landscapeInfoOpen ? 0.0f : -leftWidth;
        float controlsX = this.landscapeControlsOpen ? 0.0f : rightWidth;
        float infoTabX = this.landscapeInfoOpen ? leftWidth : 0.0f;
        float controlsTabX = this.landscapeControlsOpen ? -rightWidth : 0.0f;
        updateDrawerTabLabel((TextView) this.btnToggleInfo, getString(this.landscapeInfoOpen ? R.string.drawer_close : R.string.info));
        updateDrawerTabLabel((TextView) this.btnToggleControls, getString(this.landscapeControlsOpen ? R.string.drawer_close : R.string.input));
        View view = this.topPanel;
        if (animate) {
            view.animate().translationX(infoX).setDuration(180L).start();
            this.controlPanel.animate().translationX(controlsX).setDuration(180L).start();
            this.btnToggleInfo.animate().translationX(infoTabX).setDuration(180L).start();
            this.btnToggleControls.animate().translationX(controlsTabX).setDuration(180L).start();
            return;
        }
        view.animate().cancel();
        this.controlPanel.animate().cancel();
        this.btnToggleInfo.animate().cancel();
        this.btnToggleControls.animate().cancel();
        this.topPanel.setTranslationX(infoX);
        this.controlPanel.setTranslationX(controlsX);
        this.btnToggleInfo.setTranslationX(infoTabX);
        this.btnToggleControls.setTranslationX(controlsTabX);
    }

    private void updateDrawerTabLabel(TextView tab, String text) {
        tab.setText(text);
    }

    private boolean isCompactPortrait() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.orientation == 1 && configuration.screenHeightDp > 0 && configuration.screenHeightDp < 700;
    }

    private void applyResponsiveSizing() {
        if (!isCompactPortrait()) {
            return;
        }
        View root = findViewById(R.id.root_layout);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        this.topPanel.setPadding(dp(10), dp(6), dp(10), dp(6));
        this.controlPanel.setPadding(dp(8), dp(6), dp(8), dp(7));
        TextView title = (TextView) findViewById(R.id.text_title);
        title.setTextSize(18.0f);
        this.textStatus.setMinHeight(dp(36));
        this.textStatus.setTextSize(14.0f);
        this.textStatus.setPadding(dp(12), dp(4), dp(12), dp(4));
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this.textStatus, 11, 14, 1, 2);
        setViewHeightIfPresent(R.id.text_status, 52);
        this.textTimer.setTextSize(18.0f);
        boolean largeText = getResources().getConfiguration().fontScale >= 1.2f;
        setViewHeightIfPresent(R.id.turn_tools, 44);
        setViewHeightIfPresent(R.id.results_row, 44);
        setViewHeightIfPresent(R.id.yut_row_top, largeText ? 52 : 48);
        setViewHeightIfPresent(R.id.yut_row_bottom, largeText ? 52 : 48);
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

    private String teamName(int teamId) {
        return this.game.getLocalizedTeamName(teamId);
    }

    private String resultName(int steps) {
        return this.game.getLocalizedResultName(steps);
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
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { // from class: com.example.yutnoriapp.MainActivity.2
            @Override // androidx.activity.OnBackPressedCallback
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                long j = now - MainActivity.this.lastBackPressMillis;
                MainActivity mainActivity = MainActivity.this;
                if (j <= MainActivity.BACK_EXIT_INTERVAL_MILLIS) {
                    mainActivity.finish();
                    return;
                }
                mainActivity.lastBackPressMillis = now;
                MainActivity.this.showToast(MainActivity.this.getString(R.string.exit_confirm));
                MainActivity.this.enterImmersiveMode();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void enterImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(4358);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showToast(String message) {
        Toast.makeText(this, message, 0).show();
    }

    private void bindPressAction(int viewId, final Runnable action) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        view.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda28
            @Override // android.view.View.OnClickListener
            public final void onClick(View view2) {
                MainActivity.this.m72lambda$bindPressAction$27$comexampleyutnoriappMainActivity(view2, action);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: runPressAction, reason: merged with bridge method [inline-methods] */
    public void m72lambda$bindPressAction$27$comexampleyutnoriappMainActivity(final View view, final Runnable action) {
        if (!view.isEnabled()) {
            return;
        }
        view.animate().cancel();
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(55L).withEndAction(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda33
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$runPressAction$28(action, view);
            }
        }).start();
    }

    static /* synthetic */ void lambda$runPressAction$28(Runnable action, View view) {
        action.run();
        if (view.getParent() != null) {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90L).start();
        }
    }

    private void bounceArrivedPieces(int teamId, List<Integer> pieceIds, final Runnable onComplete) {
        if (pieceIds.isEmpty()) {
            onComplete.run();
            return;
        }
        for (int i = 0; i < pieceIds.size(); i++) {
            final TextView pieceView = this.pieceViews[teamId][pieceIds.get(i).intValue()];
            final boolean last = i == pieceIds.size() - 1;
            pieceView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(75L).withEndAction(new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda39
                @Override // java.lang.Runnable
                public final void run() {
                    pieceView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100L).withEndAction(last ? onComplete : null).start();
                }
            }).start();
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
        for (int i = 0; i < this.resultLayout.getChildCount(); i++) {
            this.resultLayout.getChildAt(i).setEnabled(enabled);
        }
        int i2 = R.id.btn_end_turn;
        setEnabledIfPresent(i2, enabled);
        this.btnTimeStop.setEnabled(enabled && this.turnDurationMillis > 0);
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
        if (this.pieceViews == null) {
            return;
        }
        for (int team = 0; team < 4; team++) {
            for (int id = 0; id < 4; id++) {
                removeFromParent(this.pieceViews[team][id]);
            }
        }
    }

    private void resetPieceViewTransform(View view) {
        view.animate().cancel();
        view.setX(0.0f);
        view.setY(0.0f);
        view.setTranslationX(0.0f);
        view.setTranslationY(0.0f);
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.setAlpha(1.0f);
    }

    private void addTurnLog(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        this.turnLog.add(0, message);
        while (this.turnLog.size() > 12) {
            this.turnLog.remove(this.turnLog.size() - 1);
        }
        updateTurnLogView();
    }

    private void restoreTurnLog(String[] entries) {
        this.turnLog.clear();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && !entry.isEmpty()) {
                    this.turnLog.add(entry);
                }
            }
        }
        while (this.turnLog.size() > 12) {
            this.turnLog.remove(this.turnLog.size() - 1);
        }
        updateTurnLogView();
    }

    private void updateTurnLogView() {
        updateActionLogRail();
    }

    private void updateActionLogRail() {
        String string;
        if (this.actionLogRail == null) {
            return;
        }
        this.actionLogRail.removeAllViews();
        final boolean hasEntry = !this.turnLog.isEmpty();
        ImageView item = createActionLogItem(hasEntry ? getActionLogIconRes(this.turnLog.get(0)) : R.drawable.ic_log_history, hasEntry ? getActionLogColor(this.turnLog.get(0)) : getResources().getColor(R.color.text_secondary));
        item.setContentDescription(hasEntry ? this.turnLog.get(0) : getString(R.string.history_none));
        item.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m88xdc3ccaf8(hasEntry, view);
            }
        });
        this.actionLogRail.addView(item);
        TextView label = new TextView(this);
        label.setText(R.string.history);
        label.setTextColor(getResources().getColor(R.color.text_primary));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(17);
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 11, 1, 2);
        if (getResources().getConfiguration().fontScale >= 1.4f) {
            label.setVisibility(8);
            this.actionLogRail.setGravity(17);
        }
        this.actionLogRail.addView(label, new LinearLayout.LayoutParams(0, -1, 1.0f));
        LinearLayout linearLayout = this.actionLogRail;
        if (hasEntry) {
            string = getString(R.string.history_recent_description);
        } else {
            string = getString(R.string.history_none);
        }
        linearLayout.setContentDescription(string);
        item.setScaleX(0.86f);
        item.setScaleY(0.86f);
        item.setAlpha(0.55f);
        item.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(160L).start();
    }

    /* JADX INFO: renamed from: lambda$updateActionLogRail$30$com-example-yutnoriapp-MainActivity, reason: not valid java name */
    /* synthetic */ void m88xdc3ccaf8(boolean hasEntry, View v) {
        if (hasEntry) {
            showTurnLogEntryDialog(0);
        } else {
            showTurnLogDialog();
        }
    }

    private ImageView createActionLogItem(int iconResId, int color) {
        ImageView item = new ImageView(this);
        item.setBackground(createActionLogDrawable(color));
        item.setImageResource(iconResId);
        item.setColorFilter(-1);
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
        drawable.setShape(1);
        drawable.setColor(color);
        drawable.setStroke(dp(2), -1);
        return drawable;
    }

    private int getActionLogIconRes(String entry) {
        if (entry == null) {
            return R.drawable.ic_log_history;
        }
        if (entry.contains(getString(R.string.log_keyword_victory)) || entry.contains(getString(R.string.log_keyword_finished))) {
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
            if (entry.contains(getString(R.string.log_keyword_victory)) || entry.contains(getString(R.string.log_keyword_finished))) {
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
        for (int teamId = 0; teamId < this.game.getTeamCount(); teamId++) {
            if (entry.startsWith(teamName(teamId) + ":")) {
                return teamId;
            }
        }
        return -1;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTurnLogDialog() {
        beginTimerDialogHold();
        YutDialogs.showTurnLog(this, new ArrayList(this.turnLog), new Runnable() { // from class: com.example.yutnoriapp.MainActivity$$ExternalSyntheticLambda35
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.clearTurnLog();
            }
        }, new MainActivity$$ExternalSyntheticLambda17(this));
    }

    private void showTurnLogEntryDialog(int index) {
        if (index < 0 || index >= this.turnLog.size()) {
            showTurnLogDialog();
        } else {
            beginTimerDialogHold();
            YutDialogs.showTurnLogEntry(this, index + 1, this.turnLog.get(index), new MainActivity$$ExternalSyntheticLambda25(this), new MainActivity$$ExternalSyntheticLambda17(this));
        }
    }

    private String[] getTurnLogSnapshot() {
        return (String[]) this.turnLog.toArray(new String[0]);
    }

    private String getSelectedStepsText() {
        List<Integer> steps = this.game.getSelectedSteps();
        if (steps.isEmpty()) {
            return getString(R.string.log_move_fallback);
        }
        ArrayList<String> names = new ArrayList<>();
        Iterator<Integer> it = steps.iterator();
        while (it.hasNext()) {
            int step = it.next().intValue();
            names.add(resultName(step));
        }
        return MainActivity$$ExternalSyntheticBackport0.m("+", names);
    }

    private String formatMoveLog(YutGameEngine.MoveResult result, String planText) {
        StringBuilder builder = new StringBuilder(getString(R.string.log_move_base, new Object[]{teamName(result.teamId), formatPieceIds(result.usedPieceIds), planText}));
        if (!result.caughtPieces.isEmpty()) {
            builder.append(getString(R.string.log_capture_suffix, new Object[]{Integer.valueOf(result.caughtPieces.size())}));
        }
        if (!result.finishedPieceIds.isEmpty()) {
            builder.append(getString(R.string.log_finish_suffix, new Object[]{Integer.valueOf(result.finishedPieceIds.size())}));
        }
        if (result.gameWon) {
            builder.append(getString(R.string.log_victory_suffix));
        }
        return builder.toString();
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
            ids.append(pieceIds.get(i).intValue() + 1);
        }
        int i2 = R.string.piece_ids;
        return getString(i2, new Object[]{ids.toString()});
    }

    private String buildGameOverMessage(YutGameEngine.MoveResult result) {
        String message = getString(R.string.game_over_summary, new Object[]{teamName(result.teamId), Integer.valueOf(countFinishedPieces(result.teamId)), 4, Integer.valueOf(this.turnLog.size())});
        if (!this.turnLog.isEmpty()) {
            return message + getString(R.string.game_over_last_actions, new Object[]{YutDialogs.buildTurnLogText(this.turnLog, Math.min(4, this.turnLog.size()))});
        }
        return message;
    }

    private int countFinishedPieces(int teamId) {
        int count = 0;
        for (int id = 0; id < 4; id++) {
            if (this.game.getPiece(teamId, id).isFinished) {
                count++;
            }
        }
        return count;
    }

    private void loadSettings() {
        GameStateStore.Settings settings = this.stateStore.loadSettings();
        this.turnDurationMillis = settings.turnDurationMillis;
        this.remainingTurnMillis = this.turnDurationMillis;
        this.feedback.setEnabled(settings.soundEnabled, settings.vibrationEnabled);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSettingsDialog() {
        beginTimerDialogHold();
        YutDialogs.showSettings(this, new YutDialogs.SettingsState(getTurnDurationIndex(), this.feedback.isSoundEnabled(), this.feedback.isVibrationEnabled(), this.turnLog.size()), new YutDialogs.SettingsActions() { // from class: com.example.yutnoriapp.MainActivity.3
            @Override // com.example.yutnoriapp.YutDialogs.SettingsActions
            public void onApply(int turnIndex, boolean soundEnabled, boolean vibrationEnabled) {
                MainActivity.this.applySettings(GameStateStore.TURN_DURATION_OPTIONS_MILLIS[turnIndex], soundEnabled, vibrationEnabled);
            }

            @Override // com.example.yutnoriapp.YutDialogs.SettingsActions
            public void onClearLog() {
                MainActivity.this.clearTurnLog();
            }

            @Override // com.example.yutnoriapp.YutDialogs.SettingsActions
            public void onShowLog() {
                MainActivity.this.showTurnLogDialog();
            }
        }, new MainActivity$$ExternalSyntheticLambda17(this));
    }

    private void beginTimerDialogHold() {
        syncTimerToNow();
        this.timerDialogHoldCount++;
        updateTimerView();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void endTimerDialogHold() {
        if (this.timerDialogHoldCount > 0) {
            this.timerDialogHoldCount--;
        }
        this.timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        persistGameState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applySettings(long selectedTurnDurationMillis, boolean soundEnabled, boolean vibrationEnabled) {
        boolean durationChanged = this.turnDurationMillis != selectedTurnDurationMillis;
        boolean keepPaused = this.isTimerPaused;
        this.turnDurationMillis = selectedTurnDurationMillis;
        this.feedback.setEnabled(soundEnabled, vibrationEnabled);
        if (durationChanged) {
            if (this.gameStarted && this.setupPanel.getVisibility() != 0 && !this.game.isGameOver()) {
                this.remainingTurnMillis = this.turnDurationMillis;
                this.timeExpiredNotified = false;
                this.isTimerPaused = keepPaused;
                this.timerCheckpointEpochMillis = System.currentTimeMillis();
                updateTimerView();
            } else {
                this.remainingTurnMillis = this.turnDurationMillis;
                updateTimerView();
            }
        } else {
            updateTimerView();
        }
        persistGameState();
        playFeedback(0);
        showToast(getString(R.string.settings_saved));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void clearTurnLog() {
        this.turnLog.clear();
        updateTurnLogView();
        persistGameState();
        playFeedback(0);
        showToast(getString(R.string.history_cleared));
    }

    private int getTurnDurationIndex() {
        return this.stateStore.getTurnDurationIndex(this.turnDurationMillis);
    }

    private boolean restorePersistedGameState() {
        GameStateStore.AppState appState = this.stateStore.restoreAppState(getResources().getColor(R.color.text_status));
        if (appState == null) {
            return false;
        }
        this.gameStarted = appState.gameStarted;
        this.game.restoreState(appState.engineState);
        this.remainingTurnMillis = this.turnDurationMillis > 0 ? Math.max(0L, appState.remainingTurnMillis) : 0L;
        this.isTimerPaused = appState.timerPaused;
        this.timeExpiredNotified = appState.timeExpiredNotified;
        this.timerCheckpointEpochMillis = appState.timerCheckpointEpochMillis;
        this.selectedPreviewTeamId = appState.selectedTeamId;
        this.selectedPreviewPieceId = appState.selectedPieceId;
        if (!isValidPreviewSelection(this.selectedPreviewTeamId, this.selectedPreviewPieceId)) {
            this.selectedPreviewTeamId = -1;
            this.selectedPreviewPieceId = -1;
        }
        boolean languageChanged = !currentLanguageTag().equals(appState.languageTag);
        this.restoredStatusMessage = languageChanged ? "" : appState.statusMessage;
        this.restoredStatusColor = appState.statusColor;
        restoreTurnLog(languageChanged ? new String[0] : appState.turnLog);
        return true;
    }

    private boolean isValidPreviewSelection(int teamId, int pieceId) {
        return teamId >= 0 && teamId < this.game.getTeamCount() && pieceId >= 0 && pieceId < 4 && !this.game.getPiece(teamId, pieceId).isFinished;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void persistGameState() {
        if (this.stateStore == null) {
            return;
        }
        syncTimerToNow();
        GameStateStore.Settings settings = new GameStateStore.Settings();
        settings.turnDurationMillis = this.turnDurationMillis;
        settings.soundEnabled = this.feedback != null && this.feedback.isSoundEnabled();
        settings.vibrationEnabled = this.feedback != null && this.feedback.isVibrationEnabled();
        GameStateStore.AppState appState = new GameStateStore.AppState();
        appState.gameStarted = this.gameStarted;
        appState.remainingTurnMillis = this.remainingTurnMillis;
        appState.timerCheckpointEpochMillis = this.timerCheckpointEpochMillis;
        appState.timerPaused = this.isTimerPaused;
        appState.timeExpiredNotified = this.timeExpiredNotified;
        appState.selectedTeamId = this.selectedPreviewTeamId;
        appState.selectedPieceId = this.selectedPreviewPieceId;
        appState.statusColor = getResources().getColor(R.color.text_status);
        if (this.textStatus != null) {
            appState.statusMessage = this.textStatus.getText().toString();
            appState.statusColor = this.textStatus.getCurrentTextColor();
        }
        appState.turnLog = getTurnLogSnapshot();
        appState.languageTag = currentLanguageTag();
        appState.engineState = this.game.saveState();
        this.stateStore.save(settings, appState);
    }

    private void playFeedback(int type) {
        if (this.feedback != null) {
            this.feedback.play(type);
        }
    }

    private void startTurnTimer() {
        this.isTimerHeldForAnimation = false;
        this.remainingTurnMillis = this.turnDurationMillis;
        this.isTimerPaused = false;
        this.timeExpiredNotified = false;
        this.timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
    }

    private void addBonusTime() {
        if (this.game.isGameOver() || this.turnDurationMillis <= 0) {
            return;
        }
        syncTimerToNow();
        this.remainingTurnMillis += BONUS_TURN_MILLIS;
        this.timeExpiredNotified = false;
        updateTimerView();
    }

    private void pauseTimer() {
        syncTimerToNow();
        this.isTimerPaused = true;
        updateTimerView();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleTimeStop() {
        if (this.game.isGameOver() || this.setupPanel.getVisibility() == 0 || this.turnDurationMillis <= 0) {
            return;
        }
        syncTimerToNow();
        this.isTimerPaused = !this.isTimerPaused;
        this.timerCheckpointEpochMillis = System.currentTimeMillis();
        updateTimerView();
        playFeedback(0);
        persistGameState();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean shouldTimerRun() {
        return this.gameStarted && !this.isTimerPaused && !this.isTimerHeldForAnimation && this.timerDialogHoldCount == 0 && this.turnDurationMillis > 0 && !this.game.isGameOver();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void syncTimerToNow() {
        long now = System.currentTimeMillis();
        if (this.timerCheckpointEpochMillis <= 0) {
            this.timerCheckpointEpochMillis = now;
            return;
        }
        if (shouldTimerRun()) {
            long elapsed = Math.max(0L, now - this.timerCheckpointEpochMillis);
            this.remainingTurnMillis = Math.max(0L, this.remainingTurnMillis - elapsed);
        }
        this.timerCheckpointEpochMillis = now;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateTimerView() {
        int timerColor;
        if (this.textTimer == null || this.btnTimeStop == null) {
            return;
        }
        if (this.turnDurationMillis <= 0) {
            this.textTimer.setText(R.string.unlimited);
            this.textTimer.setTextColor(getResources().getColor(R.color.text_primary));
            this.btnTimeStop.setText(R.string.no_time_limit);
            resetPauseButtonStyle();
            this.btnTimeStop.setEnabled(false);
            return;
        }
        this.btnTimeStop.setEnabled(true);
        long seconds = ((this.remainingTurnMillis + TIMER_TICK_MILLIS) - 1) / TIMER_TICK_MILLIS;
        long minutes = seconds / 60;
        long displaySeconds = seconds % 60;
        String timeText = String.format(Locale.ROOT, "%02d:%02d", Long.valueOf(minutes), Long.valueOf(displaySeconds));
        this.textTimer.setText(timeText);
        if (this.isTimerPaused) {
            this.textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            this.textTimer.setContentDescription(getString(R.string.timer_paused_description, new Object[]{timeText}));
            this.btnTimeStop.setText(R.string.resume_time);
            this.btnTimeStop.setTextColor(getResources().getColor(R.color.ink_black));
            this.btnTimeStop.setBackgroundResource(R.drawable.shape_button_pause_active);
            this.btnTimeStop.setBackgroundTintList(null);
            return;
        }
        if (this.timerDialogHoldCount > 0) {
            this.textTimer.setTextColor(getResources().getColor(R.color.button_end_pressed));
            this.textTimer.setContentDescription(getString(R.string.timer_settings_description, new Object[]{timeText}));
            this.btnTimeStop.setText(R.string.pause_time);
            this.btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
            this.btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
            this.btnTimeStop.setBackgroundTintList(null);
            return;
        }
        if (this.remainingTurnMillis <= 10000) {
            timerColor = R.color.btn_restart;
        } else if (this.remainingTurnMillis <= BONUS_TURN_MILLIS) {
            timerColor = R.color.preview_glow;
        } else {
            timerColor = R.color.text_primary;
        }
        this.textTimer.setTextColor(getResources().getColor(timerColor));
        this.textTimer.setContentDescription(getString(R.string.timer_remaining_description, new Object[]{timeText}));
        this.btnTimeStop.setText(R.string.pause_time);
        resetPauseButtonStyle();
    }

    private void resetPauseButtonStyle() {
        this.btnTimeStop.setTextColor(getResources().getColor(R.color.text_primary));
        this.btnTimeStop.setBackgroundResource(R.drawable.shape_button_utility);
        this.btnTimeStop.setBackgroundTintList(null);
    }
}
