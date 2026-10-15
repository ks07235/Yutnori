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

final class GameScreenLayout {
    interface Events {
        boolean isGameStarted();
        boolean isAnimating();
        boolean areControlsOpen();
        long turnDuration();
        int currentTeamColor();
        void clearMovePreviews();
        void clearPieceGuides();
        void updateResultButtons();
        void rebuildCurrentWaitingArea();
        void resizeLandscapeSummaryRows();
        void updateRollHint();
        void refreshBoardAfterPanelLayout();
        void cancelPresentationForResize();
        void restoreAfterResize();
    }

    private final android.content.Context context;
    private final ConstraintLayout root;
    private final YutGameEngine game;
    private final Events events;
    private final View topPanel, controlPanel, btnToggleInfo, btnToggleControls, setupPanel, boardArt;
    private final FrameLayout boardContainer;
    private final BoardOverlayLayout boardOverlay;
    private final TextView textStatus, textTimer, btnTimeStop;
    private final LinearLayout resultLayout, waitingArea, actionLogRail;
    private final LinearLayout.LayoutParams timedEndTurnParams;
    private LinearLayout unlimitedRolls, unlimitedFooter, unlimitedWaiting, landscapeResults;
    private TextView rollHint;
    private boolean unlimitedControlsAttached, edgePanelTransitionRunning, disposed;
    private int summaryLayoutMode = -1;
    private int edgePanelTransitionGeneration, boardLayoutRefreshGeneration;
    private View.OnLayoutChangeListener resizeListener;

    GameScreenLayout(android.content.Context context, ConstraintLayout root,
            YutGameEngine game, Events events) {
        this.context = context;
        this.root = root;
        this.game = game;
        this.events = events;
        topPanel = findViewById(R.id.top_panel);
        controlPanel = findViewById(R.id.control_panel);
        btnToggleInfo = findViewById(R.id.btn_toggle_info);
        btnToggleControls = findViewById(R.id.btn_toggle_controls);
        setupPanel = findViewById(R.id.setup_panel);
        boardArt = findViewById(R.id.board_art);
        boardContainer = findViewById(R.id.board_container);
        boardOverlay = findViewById(R.id.board_overlay);
        textStatus = findViewById(R.id.text_status);
        textTimer = findViewById(R.id.text_timer);
        btnTimeStop = findViewById(R.id.btn_time_stop);
        resultLayout = findViewById(R.id.layout_results);
        waitingArea = findViewById(R.id.waiting_area);
        actionLogRail = findViewById(R.id.action_log_rail);
        timedEndTurnParams = new LinearLayout.LayoutParams((LinearLayout.LayoutParams)
                findViewById(R.id.btn_end_turn).getLayoutParams());
    }

    private <T extends View> T findViewById(int id) { return root.findViewById(id); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private void removeFromParent(View view) {
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    void setRollHint(CharSequence text) { if (rollHint != null) rollHint.setText(text); }

    void hideColumns() {
        for (View view : new View[]{unlimitedRolls, unlimitedFooter, unlimitedWaiting, landscapeResults}) {
            view.setVisibility(View.GONE);
        }
        findViewById(R.id.landscape_control_surface).setVisibility(View.GONE);
    }

    void dispose() {
        disposed = true;
        boardLayoutRefreshGeneration++;
        edgePanelTransitionGeneration++;
        if (resizeListener != null) {
            boardArt.removeOnLayoutChangeListener(resizeListener);
            boardOverlay.removeOnLayoutChangeListener(resizeListener);
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, null);
        TransitionManager.endTransitions(root);
    }

    boolean hasEdgePanels() {
        return btnToggleInfo != null && btnToggleControls != null;
    }

    void bindBoardResizeListener() {
        resizeListener = (view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (disposed) return;
            int width = right - left;
            int height = bottom - top;
            int oldWidth = oldRight - oldLeft;
            int oldHeight = oldBottom - oldTop;
            if (width > 0 && height > 0 && (width != oldWidth || height != oldHeight)) {
                if (events.isAnimating()) {
                    events.cancelPresentationForResize();
                    boardOverlay.post(() -> {
                        if (!disposed && events.isGameStarted()) events.restoreAfterResize();
                    });
                }
                scheduleBoardLayoutRefresh();
            }
        };
        boardArt.addOnLayoutChangeListener(resizeListener);
        boardOverlay.addOnLayoutChangeListener(resizeListener);
    }

    void scheduleBoardLayoutRefresh() {
        if (boardOverlay == null) {
            return;
        }
        int refreshGeneration = ++boardLayoutRefreshGeneration;
        FrameLayout scheduledOverlay = boardOverlay;
        scheduledOverlay.postOnAnimation(() -> {
            if (refreshGeneration != boardLayoutRefreshGeneration
                    || disposed
                    || scheduledOverlay != boardOverlay
                    || edgePanelTransitionRunning) {
                return;
            }
            events.refreshBoardAfterPanelLayout();
        });
    }

    void setEdgePanelTabsVisible(boolean visible) {
        if (!hasEdgePanels()) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.GONE;
        btnToggleInfo.setVisibility(View.GONE);
        btnToggleControls.setVisibility(usesLandscapeControls() ? View.GONE : visibility);
    }

    void applyEdgePanelState(boolean animate) {
        if (!hasEdgePanels()) {
            return;
        }

        ViewGroup root = findViewById(R.id.root_layout);
        TransitionManager.endTransitions(root);
        int transitionGeneration = ++edgePanelTransitionGeneration;
        edgePanelTransitionRunning = animate;

        events.clearMovePreviews();
        events.clearPieceGuides();
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
        controlPanel.setVisibility(!usesLandscapeControls() && events.areControlsOpen() ? View.VISIBLE : View.GONE);
        btnToggleInfo.setVisibility(View.GONE);
        btnToggleControls.setVisibility(usesLandscapeControls() ? View.GONE : View.VISIBLE);
        applyBoardPanelConstraints(root);
        updateDrawerTabLabel(
                (TextView) btnToggleControls,
                context.getString(events.areControlsOpen() ? R.string.drawer_close : R.string.input));
        btnToggleControls.setSelected(events.areControlsOpen());

        topPanel.bringToFront();
        controlPanel.bringToFront();
        btnToggleInfo.bringToFront();
        btnToggleControls.bringToFront();
        setupPanel.bringToFront();

        long refreshDelay = animate ? 240L : 0L;
        boardContainer.postDelayed(() -> {
            if (disposed || transitionGeneration != edgePanelTransitionGeneration) {
                return;
            }
            edgePanelTransitionRunning = false;
            events.refreshBoardAfterPanelLayout();
        }, refreshDelay);
    }

    void applyBoardPanelConstraints(ViewGroup root) {
        if (!(root instanceof ConstraintLayout)) return;
        if (usesLandscapeControls()) {
            applyUnlimitedBoardConstraints((ConstraintLayout) root);
            return;
        }
        ConstraintSet constraints = new ConstraintSet();
        constraints.clone((ConstraintLayout) root);
        boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
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
            constraints.connect(R.id.board_container, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraints.connect(R.id.board_container, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
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

    void clearPanelTabConstraints(ConstraintSet constraints, int viewId) {
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

    void centerTabHorizontally(ConstraintSet constraints, int viewId) {
        constraints.connect(viewId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        constraints.connect(viewId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
    }

    void centerTabVertically(ConstraintSet constraints, int viewId) {
        constraints.connect(viewId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        constraints.connect(viewId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
    }

    void updateDrawerTabLabel(TextView tab, String text) {
        tab.setText(text);
    }

    boolean usesCompactWaitingTray() {
        Configuration configuration = context.getResources().getConfiguration();
        int smallestWidth = configuration.smallestScreenWidthDp;
        if (smallestWidth > 0) {
            return smallestWidth < 600;
        }
        return Math.min(configuration.screenWidthDp, configuration.screenHeightDp) < 600;
    }

    boolean isCompactPortrait() {
        Configuration configuration = context.getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                && configuration.screenWidthDp > 0
                && configuration.screenWidthDp < 600;
    }

    void applyResponsiveSizing() {
        if (usesLandscapeControls() && !usesCompactWaitingTray()) {
            setViewWidthIfPresent(R.id.top_panel, 216);
        }
        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                && usesCompactWaitingTray()) {
            setViewWidthIfPresent(R.id.top_panel, 144);
            topPanel.setPadding(dp(6), dp(8), dp(6), dp(8));
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

        boolean largeText = context.getResources().getConfiguration().fontScale >= 1.2f;
        setViewHeightIfPresent(R.id.turn_tools, 44);
        setViewHeightIfPresent(R.id.results_row, 44);
        setViewHeightIfPresent(R.id.yut_row_top, largeText ? 52 : 48);
        setViewHeightIfPresent(R.id.yut_row_bottom, largeText ? 52 : 48);
    }

    void applyCompactActionAutoSizing() {
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

    void applyFontScaleSizing() {
        float fontScale = context.getResources().getConfiguration().fontScale;
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

    void setViewHeightIfPresent(int viewId, int heightDp) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = dp(heightDp);
        view.setLayoutParams(params);
    }

    void setViewWidthIfPresent(int viewId, int widthDp) {
        View view = findViewById(viewId);
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = dp(widthDp);
        view.setLayoutParams(params);
    }

    void bindSafeAreaInsets() {
        View root = findViewById(R.id.root_layout);
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        int panelTop = topPanel.getPaddingTop();
        int panelBottom = topPanel.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets interactionInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.mandatorySystemGestures()
                            | WindowInsetsCompat.Type.navigationBars());
            int safeLeft = Math.max(cutout.left, interactionInsets.left);
            boolean landscape = usesLandscapeControls();
            // The board may use gesture space; interactive controls must remain outside it.
            int gestureTop = landscape ? Math.min(interactionInsets.top, dp(4)) : interactionInsets.top;
            int gestureBottom = landscape ? Math.min(interactionInsets.bottom, dp(4)) : interactionInsets.bottom;
            int safeTop = Math.max(cutout.top, gestureTop);
            int safeRight = Math.max(cutout.right, interactionInsets.right);
            int safeBottom = Math.max(cutout.bottom, gestureBottom);
            if (landscape) {
                int extraTop = Math.max(0, interactionInsets.top - safeTop - baseTop);
                int extraBottom = Math.max(0, interactionInsets.bottom - safeBottom - baseBottom);
                topPanel.setPadding(topPanel.getPaddingLeft(), panelTop + extraTop,
                        topPanel.getPaddingRight(), panelBottom + extraBottom);
                unlimitedFooter.setPadding(dp(4), 0, dp(4), dp(6) + extraBottom);
                for (View column : new View[]{unlimitedRolls, unlimitedWaiting, landscapeResults}) {
                    column.setPadding(column.getPaddingLeft(), extraTop, column.getPaddingRight(), 0);
                }
            }
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

    boolean usesLandscapeControls() {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    void createUnlimitedControls() {
        unlimitedControlsAttached = false;
        ConstraintLayout root = findViewById(R.id.root_layout);
        View surface = new View(context);
        surface.setId(R.id.landscape_control_surface);
        surface.setBackgroundResource(R.drawable.shape_panel);
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        surface.setVisibility(View.GONE);
        root.addView(surface, new ConstraintLayout.LayoutParams(0, 0));
        unlimitedRolls = createLandscapeColumn(root, R.id.unlimited_rolls, 128);
        unlimitedFooter = createLandscapeColumn(root, R.id.unlimited_footer, 0);
        unlimitedFooter.setLayoutParams(new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        unlimitedFooter.setPadding(dp(4), 0, dp(4), dp(6));
        unlimitedWaiting = createLandscapeColumn(root, R.id.unlimited_waiting, 48);
        landscapeResults = createLandscapeColumn(root, R.id.landscape_results, 60);
        rollHint = new TextView(context);
        rollHint.setTag("roll_hint");
        rollHint.setTextColor(context.getResources().getColor(R.color.text_primary));
        rollHint.setGravity(Gravity.CENTER);
        rollHint.setTextSize(12);
        rollHint.setMaxLines(2);
        rollHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        findViewById(R.id.btn_bdo).setBackgroundResource(R.drawable.shape_button_backdo);
    }

    LinearLayout createLandscapeColumn(ConstraintLayout root, int id, int widthDp) {
        LinearLayout column = new LinearLayout(context);
        column.setId(id);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setVisibility(View.GONE);
        root.addView(column, new ConstraintLayout.LayoutParams(dp(widthDp), 0));
        return column;
    }

    void addColumnTitle(LinearLayout column, int text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setTextColor(context.getResources().getColor(R.color.text_primary));
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        column.addView(title, new LinearLayout.LayoutParams(-1, dp(32)));
    }

    int landscapeSpacing() {
        int height = context.getResources().getConfiguration().screenHeightDp;
        return height <= 320 ? 3 : height >= 400 ? 8 : 6;
    }

    boolean usesVerticalWaitingTray() {
        return usesLandscapeControls();
    }

    void updateUnlimitedControls(boolean landscape) {
        boolean attachedNow = landscape && !unlimitedControlsAttached;
        if (attachedNow) {
            // Configuration changes reinflate the regular XML before attaching these columns.
            unlimitedControlsAttached = true;
            LinearLayout overview = findViewById(R.id.waiting_overview);
            removeFromParent(overview);
            overview.setOrientation(LinearLayout.VERTICAL);
            unlimitedWaiting.addView(overview, new LinearLayout.LayoutParams(-1, -1));
            waitingArea.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

            // Results get their own vertical scroll area. The old horizontal viewport stays
            // in the footer, hidden, so portrait XML remains unchanged.
            View horizontalResults = (View) resultLayout.getParent();
            horizontalResults.setVisibility(View.GONE);
            removeFromParent(resultLayout);
            resultLayout.setOrientation(LinearLayout.VERTICAL);
            resultLayout.setGravity(Gravity.TOP);
            addColumnTitle(landscapeResults, R.string.pending_results_title);
            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setPadding(dp(2), 0, dp(2), 0);
            scroll.addView(resultLayout, new FrameLayout.LayoutParams(-1, -2));
            landscapeResults.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

            textStatus.setVisibility(View.GONE);
            TextView heading = findViewById(R.id.text_title);
            heading.setVisibility(View.VISIBLE);
            heading.setText(context.getString(R.string.current_turn_heading, game.getLocalizedTeamName(game.getCurrentTeam())));
            heading.setTextColor(events.currentTeamColor());
            heading.setMaxLines(2);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(heading, 12, 16, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout tools = findViewById(R.id.turn_tools);
            removeFromParent(tools);
            tools.setOrientation(LinearLayout.HORIZONTAL);
            tools.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, dp(56));
            toolsParams.topMargin = dp(landscapeSpacing());
            ((LinearLayout) topPanel).addView(tools, 1, toolsParams);
            textTimer.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 1f));
            textTimer.setMaxLines(2);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(textTimer, 12, 24, 1, TypedValue.COMPLEX_UNIT_SP);
            LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(dp(56), -1);
            pauseParams.leftMargin = dp(4);
            btnTimeStop.setLayoutParams(pauseParams);
            btnTimeStop.setMaxLines(2);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(btnTimeStop, 10, 12, 1, TypedValue.COMPLEX_UNIT_SP);
            for (int id : new int[]{R.id.results_row}) {
                View row = findViewById(id);
                removeFromParent(row);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
                params.topMargin = dp(landscapeSpacing());
                unlimitedFooter.addView(row, params);
            }
            LinearLayout actions = findViewById(R.id.results_row);
            removeFromParent(actionLogRail);
            actions.addView(actionLogRail, new LinearLayout.LayoutParams(dp(44), dp(44)));
            View undo = findViewById(R.id.btn_undo_roll);
            LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
            undoParams.setMargins(dp(2), 0, dp(2), 0);
            undo.setLayoutParams(undoParams);

            addColumnTitle(unlimitedRolls, R.string.roll_controls);
            removeFromParent(rollHint);
            unlimitedRolls.addView(rollHint, new LinearLayout.LayoutParams(-1, dp(32)));
            int[] buttons = {R.id.btn_do, R.id.btn_yut, R.id.btn_gae, R.id.btn_mo, R.id.btn_geol, R.id.btn_bdo};
            LinearLayout row = null;
            for (int i = 0; i < buttons.length; i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(context);
                    unlimitedRolls.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
                }
                View button = findViewById(buttons[i]);
                removeFromParent(button);
                button.setPadding(dp(2), dp(3), dp(2), dp(3));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
                params.setMargins(dp(2), dp(landscapeSpacing()), dp(2), dp(landscapeSpacing()));
                row.addView(button, params);
            }
            events.updateResultButtons();
            if (events.isGameStarted()) events.rebuildCurrentWaitingArea();
        }
        int visible = landscape && events.isGameStarted() ? View.VISIBLE : View.GONE;
        unlimitedRolls.setVisibility(visible);
        unlimitedFooter.setVisibility(visible);
        unlimitedWaiting.setVisibility(visible);
        landscapeResults.setVisibility(visible);
        findViewById(R.id.landscape_control_surface).setVisibility(visible);
        if (events.isGameStarted()) {
            controlPanel.setVisibility(!landscape && events.areControlsOpen() ? View.VISIBLE : View.GONE);
            btnToggleControls.setVisibility(landscape ? View.GONE : View.VISIBLE);
            if (attachedNow) scheduleBoardLayoutRefresh();
        }
    }

    void applyUnlimitedBoardConstraints(ConstraintLayout root) {
        ConstraintSet constraints = new ConstraintSet();
        constraints.clone(root);
        for (int id : new int[]{R.id.board_container, R.id.finish_destination, R.id.unlimited_rolls,
                R.id.unlimited_footer, R.id.unlimited_waiting, R.id.landscape_results,
                R.id.landscape_control_surface}) {
            clearPanelTabConstraints(constraints, id);
        }
        constraints.setVisibility(R.id.control_panel, View.GONE);
        constraints.setVisibility(R.id.btn_toggle_controls, View.GONE);
        constraints.setVisibility(R.id.btn_toggle_info, View.GONE);
        constraints.setVisibility(R.id.top_panel, View.VISIBLE);
        for (int id : new int[]{R.id.unlimited_rolls, R.id.unlimited_footer,
                R.id.unlimited_waiting, R.id.landscape_results, R.id.landscape_control_surface}) {
            constraints.setVisibility(id, View.VISIBLE);
        }
        boolean compact = usesCompactWaitingTray();
        int panelGap = dp(landscapeSpacing());
        // Account for the frame's end inset without stealing width from the board.
        constraints.constrainWidth(R.id.unlimited_rolls, dp(compact ? 110 : 146));
        constraints.constrainWidth(R.id.unlimited_waiting, dp(compact ? 48 : 60));
        constraints.constrainWidth(R.id.landscape_results, dp(compact ? 56 : 76));
        constraints.connect(R.id.unlimited_rolls, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dp(6));
        constraints.connect(R.id.unlimited_waiting, ConstraintSet.END, R.id.unlimited_rolls, ConstraintSet.START, dp(4));
        constraints.connect(R.id.landscape_results, ConstraintSet.END, R.id.unlimited_waiting, ConstraintSet.START, dp(4));
        for (int id : new int[]{R.id.unlimited_rolls, R.id.unlimited_waiting, R.id.landscape_results}) {
            constraints.connect(id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, panelGap);
            constraints.connect(id, ConstraintSet.BOTTOM, R.id.unlimited_footer, ConstraintSet.TOP, panelGap);
        }
        constraints.connect(R.id.unlimited_footer, ConstraintSet.START, R.id.landscape_results, ConstraintSet.START);
        constraints.connect(R.id.unlimited_footer, ConstraintSet.END, R.id.unlimited_rolls, ConstraintSet.END);
        constraints.connect(R.id.unlimited_footer, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
        constraints.connect(R.id.landscape_control_surface, ConstraintSet.START, R.id.landscape_results, ConstraintSet.START);
        constraints.setMargin(R.id.landscape_control_surface, ConstraintSet.START, -dp(4));
        constraints.connect(R.id.landscape_control_surface, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        constraints.connect(R.id.landscape_control_surface, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
        constraints.connect(R.id.landscape_control_surface, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

        // The footer only occupies the controls' width, leaving the board the full screen height.
        constraints.connect(R.id.board_container, ConstraintSet.START, R.id.top_panel, ConstraintSet.END, dp(4));
        constraints.connect(R.id.board_container, ConstraintSet.END, R.id.landscape_results, ConstraintSet.START, dp(4));
        constraints.createVerticalChain(ConstraintSet.PARENT_ID, ConstraintSet.TOP, ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM, new int[]{R.id.board_container, R.id.finish_destination},
                null, ConstraintSet.CHAIN_PACKED);
        constraints.connect(R.id.finish_destination, ConstraintSet.END, R.id.board_container, ConstraintSet.END);
        constraints.setMargin(R.id.finish_destination, ConstraintSet.TOP, dp(4));
        constraints.setGoneMargin(R.id.board_container, ConstraintSet.BOTTOM, 0);
        constraints.applyTo(root);
    }

    void updateTimerLayout() {
        events.updateRollHint();
        boolean timed = events.turnDuration() > 0L;
        boolean landscape = usesLandscapeControls();
        LinearLayout tools = findViewById(R.id.turn_tools);
        int mode = (landscape ? 1 : 0) | (timed ? 2 : 0) | (events.isGameStarted() ? 4 : 0);
        if (mode != summaryLayoutMode) {
            summaryLayoutMode = mode;
            if (landscape) events.resizeLandscapeSummaryRows();
        }
        LinearLayout results = findViewById(R.id.results_row);
        View endTurn = findViewById(R.id.btn_end_turn);
        LinearLayout destination = landscape ? results : tools;
        if (endTurn.getParent() != destination) {
            removeFromParent(endTurn);
            LinearLayout.LayoutParams params = landscape
                    ? new LinearLayout.LayoutParams(0, dp(44), 1f)
                    : new LinearLayout.LayoutParams(timedEndTurnParams);
            destination.addView(endTurn, params);
        }
        tools.setVisibility(timed || !landscape ? View.VISIBLE : View.GONE);
        textTimer.setVisibility(View.VISIBLE);
        btnTimeStop.setVisibility(timed ? View.VISIBLE : View.GONE);
        updateUnlimitedControls(landscape);
    }
}
