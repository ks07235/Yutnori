package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import java.util.List;

/** Displays history without owning or changing the stored entries. */
final class HistoryPresenter {
    private final Context context;
    private final LinearLayout rail;
    private final YutGameEngine game;
    private final Runnable openHistory;
    private final Runnable openLatestEntry;
    private boolean disposed;

    HistoryPresenter(Context context, LinearLayout rail, YutGameEngine game,
            Runnable openHistory, Runnable openLatestEntry) {
        this.context = context;
        this.rail = rail;
        this.game = game;
        this.openHistory = openHistory;
        this.openLatestEntry = openLatestEntry;
        if (rail != null) {
            rail.setOnClickListener(v -> { if (!disposed) openHistory.run(); });
            rail.setClickable(true);
            rail.setFocusable(true);
        }
    }

    void render(List<String> entries, int[] teamColors, boolean compact) {
        if (rail == null || disposed) return;
        rail.removeAllViews();
        boolean hasEntry = !entries.isEmpty();
        String entry = hasEntry ? entries.get(0) : null;
        ImageView item = createItem(hasEntry ? iconFor(entry) : R.drawable.ic_log_history,
                hasEntry ? colorFor(entry, teamColors) : color(R.color.text_secondary));
        item.setContentDescription(hasEntry ? entry : context.getString(R.string.history_none));
        item.setOnClickListener(v -> {
            if (disposed) return;
            if (hasEntry) openLatestEntry.run();
            else openHistory.run();
        });
        rail.addView(item);

        TextView label = new TextView(context);
        label.setText(R.string.history);
        label.setTextColor(color(R.color.text_primary));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(label, 9, 11, 1, TypedValue.COMPLEX_UNIT_SP);
        if (compact) {
            label.setVisibility(View.GONE);
            rail.setGravity(Gravity.CENTER);
        }
        rail.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        rail.setContentDescription(context.getString(hasEntry
                ? R.string.history_recent_description : R.string.history_none));
        item.setScaleX(0.86f);
        item.setScaleY(0.86f);
        item.setAlpha(0.55f);
        item.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(160).start();
    }

    String formatMove(YutGameEngine.MoveResult result, String planText) {
        StringBuilder builder = new StringBuilder(context.getString(R.string.log_move_base,
                game.getLocalizedTeamName(result.teamId), formatPieceIds(result.usedPieceIds), planText));
        builder.append(context.getString(R.string.log_location_suffix,
                boardLocationName(result.startNode), boardLocationName(result.targetNode)));
        if (!result.caughtPieces.isEmpty()) {
            builder.append(context.getString(R.string.log_capture_suffix, result.caughtPieces.size()));
        }
        if (!result.finishedPieceIds.isEmpty()) {
            builder.append(context.getString(R.string.log_finish_suffix, result.finishedPieceIds.size()));
        }
        if (result.gameWon) builder.append(context.getString(R.string.log_victory_suffix));
        return builder.toString();
    }

    String formatGameOver(YutGameEngine.MoveResult result, List<String> entries) {
        int finished = 0;
        for (int id = 0; id < YutGameEngine.PIECE_COUNT; id++) {
            if (game.getPiece(result.teamId, id).isFinished) finished++;
        }
        String message = context.getString(R.string.game_over_summary,
                game.getLocalizedTeamName(result.teamId), finished, YutGameEngine.PIECE_COUNT, entries.size());
        if (!entries.isEmpty()) {
            message += context.getString(R.string.game_over_last_actions,
                    YutDialogs.buildTurnLogText(entries, Math.min(4, entries.size())));
        }
        return message;
    }

    void dispose() {
        disposed = true;
        if (rail == null) return;
        rail.setOnClickListener(null);
        for (int i = 0; i < rail.getChildCount(); i++) {
            View child = rail.getChildAt(i);
            child.animate().cancel();
            child.setOnClickListener(null);
        }
    }

    private ImageView createItem(int iconResId, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        background.setStroke(dp(2), 0xFFFFFFFF);
        ImageView item = new ImageView(context);
        item.setBackground(background);
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

    private int iconFor(String entry) {
        if (entry == null) return R.drawable.ic_log_history;
        if (entry.contains(context.getString(R.string.log_keyword_undo))) return R.drawable.ic_log_undo;
        if (entry.contains(context.getString(R.string.log_keyword_victory))
                || entry.contains(context.getString(R.string.log_keyword_finished))) return R.drawable.ic_log_finish;
        if (entry.contains(context.getString(R.string.log_keyword_captured))) return R.drawable.ic_log_capture;
        if (entry.contains(context.getString(R.string.log_keyword_moved))) return R.drawable.ic_log_move;
        if (entry.contains(context.getString(R.string.log_keyword_turn_end))) return R.drawable.ic_log_turn_end;
        if (entry.contains(context.getString(R.string.log_keyword_new_game))) return R.drawable.ic_log_start;
        if (entry.contains(context.getString(R.string.log_keyword_roll))) return R.drawable.ic_log_yut;
        return R.drawable.ic_log_history;
    }

    private int colorFor(String entry, int[] teamColors) {
        if (entry != null) {
            for (int team = 0; team < game.getTeamCount(); team++) {
                if (entry.startsWith(game.getLocalizedTeamName(team) + ":")) return TeamAppearance.label(teamColors[team]);
            }
            if (entry.contains(context.getString(R.string.log_keyword_victory))
                    || entry.contains(context.getString(R.string.log_keyword_finished))) return color(R.color.accent_gold);
            if (entry.contains(context.getString(R.string.log_keyword_undo))) return color(R.color.text_secondary);
        }
        return color(R.color.text_primary);
    }

    private String boardLocationName(int node) {
        if (node == BoardPath.START_NODE) return context.getString(R.string.location_waiting);
        if (node == BoardPath.END_NODE) return context.getString(R.string.location_finished);
        int spot = game.visualSpotFor(node);
        String[] names = context.getResources().getStringArray(R.array.board_spot_names);
        return spot >= 0 && spot < names.length ? names[spot] : context.getString(R.string.location_board);
    }

    private String formatPieceIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return context.getString(R.string.piece_ids_fallback);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) names.append(',');
            names.append(ids.get(i) + 1);
        }
        return context.getString(R.string.piece_ids, names.toString());
    }

    private int color(int resource) { return context.getResources().getColor(resource); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
