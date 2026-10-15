package com.example.yutnoriapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

/** Disposable credits presentation. Match totals are never changed by animation callbacks. */
final class VictoryPresentation {
    private final Context context;
    private final AlertDialog dialog;
    private final ScrollView scroll;
    private final Button skip;
    private final Runnable onSummary;
    private final boolean staticOnly;
    private ValueAnimator animator;
    private boolean disposed, summary;

    VictoryPresentation(Context context, YutGameEngine game, MatchStatistics statistics,
            int[] colors, int[] shapes, boolean reducedMotion, Runnable onNewGame,
            Runnable onHistory, Runnable onAcknowledged, Runnable onSummary) {
        this.context = context;
        this.onSummary = onSummary;
        AccessibilityManager accessibility = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        staticOnly = reducedMotion || (accessibility != null && accessibility.isTouchExplorationEnabled())
                || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled());
        LinearLayout content = DialogPresentation.column(context);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        content.setBackgroundColor(context.getColor(R.color.surface_panel_soft));
        scroll = new ScrollView(context);
        scroll.setId(R.id.victory_scroll);
        scroll.setClipToPadding(false);
        LinearLayout credits = DialogPresentation.column(context);
        credits.setPadding(dp(4), dp(8), dp(4), dp(16));
        int winner = game.getCurrentTeam();
        addTeamHeading(credits, context.getString(R.string.victory_title,
                game.getLocalizedTeamName(winner)), winner, colors, shapes, 26);
        if (!statistics.isComplete()) addText(credits, context.getString(R.string.credits_partial), 15, false);
        for (int order = 0; order < game.getTeamCount(); order++) {
            int team = (winner + order) % game.getTeamCount();
            if (order > 0) {
                View rule = new View(context);
                rule.setBackgroundColor(context.getColor(R.color.board_line));
                LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(1));
                ruleParams.setMargins(0, dp(20), 0, dp(14));
                credits.addView(rule, ruleParams);
                addTeamHeading(credits, game.getLocalizedTeamName(team), team, colors, shapes, 22);
            }
            int finished = 0;
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) if (game.getPiece(team, id).isFinished) finished++;
            addText(credits, context.getString(R.string.credits_totals, statistics.captures(team),
                    finished, statistics.maxStack(team), statistics.moves(team)), 18, false);
            int maximum = 0;
            for (int i = 0; i < 6; i++) maximum = Math.max(maximum, statistics.rolls(team, i));
            StringBuilder most = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                if (maximum > 0 && statistics.rolls(team, i) == maximum) {
                    if (most.length() > 0) most.append(" · ");
                    most.append(game.getLocalizedResultName(MatchStatistics.STEPS[i]));
                }
            }
            if (maximum > 0) addText(credits,
                    context.getString(R.string.credits_most, most.toString(), maximum), 18, true);
            // Two short rows stay readable on narrow phones and with large system text.
            for (int row = 0; row < 2; row++) {
                StringBuilder counts = new StringBuilder();
                for (int i = row * 3; i < row * 3 + 3; i++) {
                    if (i % 3 != 0) counts.append("   ·   ");
                    counts.append(context.getString(R.string.credits_roll_count,
                            game.getLocalizedResultName(MatchStatistics.STEPS[i]), statistics.rolls(team, i)));
                }
                addText(credits, counts.toString(), 17, false);
            }
        }
        addText(credits, context.getString(R.string.credits_thanks), 22, true).setPadding(0, dp(28), 0, dp(16));
        scroll.addView(credits);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        skip = new Button(context);
        skip.setId(R.id.victory_skip);
        skip.setText(R.string.credits_skip);
        skip.setTextSize(16);
        skip.setMinHeight(dp(48));
        skip.setOnClickListener(v -> showSummary());
        content.addView(skip, new LinearLayout.LayoutParams(-1, -2));
        scroll.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !summary) showSummary();
            return false;
        });
        dialog = new AlertDialog.Builder(context)
                .setCustomTitle(DialogPresentation.title(context, R.string.credits_title, true))
                .setView(content)
                .setPositiveButton(R.string.game_over_new_game, (d, which) -> {
                    stop(); onAcknowledged.run(); onNewGame.run();
                })
                .setNeutralButton(R.string.history_all, (d, which) -> {
                    stop(); onAcknowledged.run(); onHistory.run();
                })
                .setNegativeButton(R.string.game_over_view_board, (d, which) -> {
                    stop(); onAcknowledged.run();
                })
                .setCancelable(false).create();
        dialog.setOnDismissListener(d -> stop());
        dialog.setOnShowListener(d -> {
            int width = context.getResources().getConfiguration().screenWidthDp;
            int height = context.getResources().getConfiguration().screenHeightDp;
            // A bounded, weighted viewport leaves both skip and footer reachable in landscape.
            content.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1,
                    dp(Math.max(88, height - 190))));
            if (dialog.getWindow() != null) dialog.getWindow().setLayout(dp(Math.min(640, width - 32)), -2);
            if (staticOnly) showSummary();
            else scroll.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override public void onGlobalLayout() {
                            if (scroll.getHeight() <= 0) return;
                            scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (!disposed) startCredits();
                        }
                    });
        });
    }

    void show() { dialog.show(); }

    private void startCredits() {
        if (disposed || summary) return;
        int travel = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
        if (travel == 0) { showSummary(); return; }
        animator = ValueAnimator.ofInt(0, travel);
        animator.setStartDelay(1800L);
        animator.setDuration(Math.max(12000L, Math.round(travel / context.getResources().getDisplayMetrics().density * 45)));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> scroll.scrollTo(0, (int) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (!disposed && !summary) showSummary();
            }
        });
        animator.start();
    }

    void showSummary() {
        if (disposed || summary) return;
        summary = true;
        if (animator != null) animator.cancel();
        skip.setVisibility(View.GONE);
        scroll.scrollTo(0, 0);
        onSummary.run();
    }

    void dispose() { stop(); dialog.dismiss(); }
    private void stop() {
        disposed = true;
        if (animator != null) animator.cancel();
    }

    private void addTeamHeading(LinearLayout parent, String title, int team,
            int[] colors, int[] shapes, int size) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        PieceStackView piece = new PieceStackView(context);
        piece.configureAppearance(colors[team], shapes[team], team + 1);
        piece.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(piece, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView label = text(title, size, true);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView addText(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = text(value, size, bold);
        view.setPadding(0, dp(8), 0, 0);
        parent.addView(view, new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        return view;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(context.getColor(R.color.text_primary));
        view.setLineSpacing(dp(3), 1f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) { return DialogPresentation.dp(context, value); }
}
