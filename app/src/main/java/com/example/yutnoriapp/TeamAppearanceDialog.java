package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

/** Edits a draft; the game is started only after the positive button is pressed. */
final class TeamAppearanceDialog {
    private static final int[] COLOR_NAMES = {
            R.string.piece_color_red, R.string.piece_color_blue, R.string.piece_color_purple,
            R.string.piece_color_green, R.string.piece_color_forsythia, R.string.piece_color_white,
            R.string.piece_color_black, R.string.piece_color_orange, R.string.piece_color_pink,
            R.string.piece_color_teal
    };
    private static final int[] COLOR_ORDER = {4, 0, 1, 2, 3, 5, 6, 7, 8, 9};
    private static final int[] SHAPE_NAMES = {
            R.string.piece_shape_circle, R.string.piece_shape_square, R.string.piece_shape_diamond,
            R.string.piece_shape_triangle, R.string.piece_shape_star, R.string.piece_shape_hexagon
    };

    interface OnStart { void start(int[] colors, int[] shapes); }

    private final Context context;
    private final int teamCount;
    private final int[] colors;
    private final int[] shapes;
    private final LinearLayout teams;
    private final LinearLayout palette;
    private final LinearLayout shapeOptions;
    private final TextView editingLabel;
    private final TextView error;
    private int selectedTeam;

    private TeamAppearanceDialog(Context context, int teamCount, int[] colors, int[] shapes,
            LinearLayout content) {
        this.context = context;
        this.teamCount = teamCount;
        this.colors = colors;
        this.shapes = shapes;
        content.addView(label(context.getString(R.string.piece_setup_hint), false));
        teams = section(content);
        editingLabel = label("", true);
        content.addView(editingLabel);
        palette = section(content);
        content.addView(label(context.getString(R.string.piece_setup_shape), true));
        shapeOptions = section(content);
        error = label("", false);
        error.setTextColor(ContextCompat.getColor(context, R.color.timer_urgent));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(error);
        refresh();
    }

    static AlertDialog show(Context context, int teamCount, int[] draftColors, int[] draftShapes,
            OnStart onStart, Runnable onDismiss) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 16), dp(context, 4), dp(context, 16), dp(context, 8));
        scroll.addView(content);
        TeamAppearanceDialog editor = new TeamAppearanceDialog(
                context, teamCount, draftColors, draftShapes, content);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.piece_setup_title)
                .setView(scroll)
                .setPositiveButton(R.string.piece_setup_start, null)
                .setNeutralButton(R.string.piece_setup_recommended, null)
                .setNegativeButton(R.string.piece_setup_back, null)
                .create();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!TeamAppearance.distinctColors(draftColors, teamCount)) return;
            dialog.dismiss();
            onStart.start(draftColors.clone(), draftShapes.clone());
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            System.arraycopy(TeamAppearance.recommendedColors(), 0, draftColors, 0, TeamAppearance.TEAM_COUNT);
            System.arraycopy(TeamAppearance.recommendedShapes(), 0, draftShapes, 0, TeamAppearance.TEAM_COUNT);
            editor.refresh();
        });
        return dialog;
    }

    private void refresh() {
        teams.removeAllViews();
        palette.removeAllViews();
        shapeOptions.removeAllViews();
        for (int team = 0; team < teamCount; team++) {
            final int id = team;
            String teamName = context.getString(R.string.piece_setup_team, team + 1);
            String description = context.getString(R.string.piece_setup_team_description,
                    teamName, context.getString(COLOR_NAMES[colors[team]]), context.getString(SHAPE_NAMES[shapes[team]]));
            LinearLayout option = option(teamName + "\n" + context.getString(COLOR_NAMES[colors[team]]),
                    colors[team], shapes[team], team == selectedTeam, description);
            option.setTag("appearance_team_" + team);
            option.setOnClickListener(v -> { selectedTeam = id; refresh(); });
            addGridCell(teams, option, team, 2);
        }
        editingLabel.setText(context.getString(R.string.piece_setup_editing, selectedTeam + 1));
        for (int index = 0; index < COLOR_ORDER.length; index++) {
            int color = COLOR_ORDER[index];
            int owner = colorOwner(color);
            String name = context.getString(COLOR_NAMES[color]);
            String description = owner < 0 ? name
                    : context.getString(R.string.piece_setup_color_used, name, owner + 1);
            LinearLayout option = option(name, color, shapes[selectedTeam], colors[selectedTeam] == color, description);
            option.setTag("appearance_color_" + color);
            option.setAlpha(owner >= 0 && owner != selectedTeam ? 0.45f : 1f);
            option.setOnClickListener(v -> {
                int currentOwner = colorOwner(color);
                if (currentOwner >= 0 && currentOwner != selectedTeam) {
                    error.setText(context.getString(R.string.piece_setup_color_used, name, currentOwner + 1));
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                colors[selectedTeam] = color;
                refresh();
            });
            addGridCell(palette, option, index, 3);
        }
        for (int shape = 0; shape < TeamAppearance.SHAPE_COUNT; shape++) {
            final int id = shape;
            String name = context.getString(SHAPE_NAMES[shape]);
            LinearLayout option = option(name, colors[selectedTeam], shape, shapes[selectedTeam] == shape, name);
            option.setTag("appearance_shape_" + shape);
            option.setOnClickListener(v -> { shapes[selectedTeam] = id; refresh(); });
            addGridCell(shapeOptions, option, shape, 3);
        }
        error.setVisibility(View.GONE);
    }

    private int colorOwner(int color) {
        for (int team = 0; team < teamCount; team++) if (colors[team] == color) return team;
        return -1;
    }

    private LinearLayout option(String name, int color, int shape, boolean selected, String description) {
        LinearLayout option = new LinearLayout(context);
        option.setOrientation(LinearLayout.VERTICAL);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(context, 3), dp(context, 6), dp(context, 3), dp(context, 6));
        option.setMinimumHeight(dp(context, 84));
        option.setClickable(true);
        option.setFocusable(true);
        option.setSelected(selected);
        option.setContentDescription(description);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 12));
        background.setColor(ContextCompat.getColor(context,
                selected ? R.color.accent_gold_soft : R.color.surface_panel_soft));
        background.setStroke(dp(context, selected ? 2 : 1), ContextCompat.getColor(context,
                selected ? R.color.button_end : R.color.surface_panel_stroke));
        option.setBackground(background);
        PieceStackView preview = new PieceStackView(context);
        preview.configureAppearance(color, shape, 1);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        option.addView(preview, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)));
        TextView text = label((selected ? "✓ " : "") + name, false);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(12);
        text.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        option.addView(text);
        return option;
    }

    private TextView label(String value, boolean heading) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(heading ? 15 : 13);
        text.setTextColor(ContextCompat.getColor(context, heading ? R.color.text_primary : R.color.text_secondary));
        text.setPadding(0, dp(context, heading ? 12 : 4), 0, dp(context, 6));
        if (heading) text.setTypeface(Typeface.DEFAULT_BOLD);
        return text;
    }

    private LinearLayout section(LinearLayout parent) {
        LinearLayout section = new LinearLayout(context);
        section.setOrientation(LinearLayout.VERTICAL);
        parent.addView(section);
        return section;
    }

    private void addGridCell(LinearLayout grid, View cell, int index, int columns) {
        if (index % columns == 0) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(row);
            for (int i = 0; i < columns; i++) {
                LinearLayout slot = new LinearLayout(context);
                slot.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                params.setMargins(dp(context, 2), dp(context, 3), dp(context, 2), dp(context, 3));
                row.addView(slot, params);
            }
        }
        LinearLayout row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        ((LinearLayout) row.getChildAt(index % columns)).addView(cell,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
