package com.example.yutnoriapp;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import java.util.Arrays;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Owns status icons, which are separate from the movable board pieces. */
final class TeamStatusPanel {
    private final Context context;
    private final LinearLayout summary;
    private final View viewport;
    private final PieceStackView currentPiece;
    private final TextView heading;
    private final YutGameEngine game;
    private final IntSupplier spacing;
    private final LongSupplier turnDuration;
    private final LinearLayout[] teamProgressRows = new LinearLayout[YutGameEngine.MAX_TEAM_COUNT];
    private final PieceStackView[][] teamProgressPieces = new PieceStackView[YutGameEngine.MAX_TEAM_COUNT][YutGameEngine.PIECE_COUNT];
    private final Runnable resizeTask = this::resizeRows;
    private final View.OnLayoutChangeListener resizeListener;
    private boolean disposed;

    TeamStatusPanel(Context context, LinearLayout summary, View viewport,
            PieceStackView currentPiece, TextView heading, YutGameEngine game,
            IntSupplier spacing, LongSupplier turnDuration) {
        this.context = context;
        this.summary = summary;
        this.viewport = viewport;
        this.currentPiece = currentPiece;
        this.heading = heading;
        this.game = game;
        this.spacing = spacing;
        this.turnDuration = turnDuration;
        resizeListener = (v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (!disposed && b - t != oldB - oldT) v.post(resizeTask);
        };
        if (viewport != null) viewport.addOnLayoutChangeListener(resizeListener);
    }

    void rebuild(int[] colors, int[] shapes, boolean compact) {
        if (disposed) return;
        clear();
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setGravity(landscape() ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        LinearLayout row = null;
        for (int team = 0; team < game.getTeamCount(); team++) {
            if (landscape() || team % 2 == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(landscape() ? rowHeight() : 30));
                if (landscape() && team > 0) params.topMargin = dp(spacing.getAsInt());
                summary.addView(row, params);
            }
            createTeam(team, row, colors, shapes, compact);
        }
        render(colors, shapes);
    }

    void render(int[] colors, int[] shapes) {
        if (disposed) return;
        int current = game.getCurrentTeam();
        currentPiece.configureAppearance(colors[current], shapes[current], current + 1);
        currentPiece.setFinishedIndicator(false);
        heading.setText(context.getString(R.string.current_turn_heading, game.getLocalizedTeamName(current)));
        heading.setTextColor(TeamAppearance.label(colors[current]));
        for (int team = 0; team < game.getTeamCount(); team++) {
            LinearLayout row = teamProgressRows[team];
            if (row == null) continue;
            int finished = 0;
            for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
                boolean done = game.getPiece(team, id).isFinished;
                if (done) finished++;
                teamProgressPieces[team][id].setFinishedIndicator(done);
            }
            row.setBackgroundColor(team == current
                    ? context.getResources().getColor(R.color.accent_gold_soft) : Color.TRANSPARENT);
            row.setContentDescription(context.getString(R.string.finished_summary_description,
                    game.getLocalizedTeamName(team), finished, YutGameEngine.PIECE_COUNT));
        }
    }

    void resizeRows() {
        if (disposed || !landscape()) return;
        for (int i = 0; i < summary.getChildCount(); i++) {
            View row = summary.getChildAt(i);
            ViewGroup.LayoutParams params = row.getLayoutParams();
            int height = dp(rowHeight());
            if (params.height != height) {
                params.height = height;
                row.setLayoutParams(params);
            }
        }
    }

    void clear() {
        summary.removeAllViews();
        Arrays.fill(teamProgressRows, null);
        for (PieceStackView[] pieces : teamProgressPieces) Arrays.fill(pieces, null);
    }

    void dispose() {
        disposed = true;
        if (viewport != null) {
            viewport.removeOnLayoutChangeListener(resizeListener);
            viewport.removeCallbacks(resizeTask);
        }
    }

    private void createTeam(int team, LinearLayout parent, int[] colors, int[] shapes, boolean compact) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(3), dp(1), dp(3), dp(1));
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        teamProgressRows[team] = row;
        parent.addView(row, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        TextView label = new TextView(context);
        label.setText(game.getLocalizedTeamName(team));
        label.setTextColor(TeamAppearance.label(colors[team]));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        row.addView(label, new LinearLayout.LayoutParams(dp(landscape() && compact ? 28 : 38),
                LinearLayout.LayoutParams.MATCH_PARENT));
        for (int piece = 0; piece < YutGameEngine.PIECE_COUNT; piece++) {
            PieceStackView icon = new PieceStackView(context);
            icon.configureAppearance(colors[team], shapes[team], piece + 1);
            icon.setFinishedIndicator(false);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(icon, new LinearLayout.LayoutParams(0, dp(26), 1f));
            teamProgressPieces[team][piece] = icon;
        }
    }

    private int rowHeight() {
        int height = context.getResources().getConfiguration().screenHeightDp;
        if (viewport != null && viewport.getHeight() > 0) {
            int teams = game.getTeamCount();
            int available = viewport.getHeight() - dp(spacing.getAsInt()) * (teams - 1);
            return Math.max(26, Math.min(40,
                    Math.round(available / (teams * context.getResources().getDisplayMetrics().density))));
        }
        if (turnDuration.getAsLong() <= 0L) return height <= 320 ? 40 : height >= 400 ? 52 : 46;
        return height <= 320 ? 26 : height >= 400 ? 36 : 34;
    }

    private boolean landscape() {
        return context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
