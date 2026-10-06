package com.example.yutnoriapp;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

/** Shared sizing and restrained ink-and-paper styling for editor dialogs. */
final class DialogPresentation {
    private DialogPresentation() { }

    static boolean twoColumns(Context context) {
        Configuration config = context.getResources().getConfiguration();
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && config.screenWidthDp >= 600 && config.fontScale <= 1.4f;
    }

    static void decorate(Context context, AlertDialog dialog, int title) {
        if (dialog.getWindow() == null) return;
        int width = context.getResources().getConfiguration().screenWidthDp;
        android.widget.ScrollView scroll = findScroll(dialog.getWindow().getDecorView());
        if (scroll != null) {
            Configuration config = context.getResources().getConfiguration();
            int reserved = config.fontScale > 1.4f ? 240
                    : title == R.string.piece_setup_title && twoColumns(context) ? 140 : 180;
            android.view.ViewGroup.LayoutParams params = scroll.getLayoutParams();
            params.height = dp(context, Math.max(64, config.screenHeightDp - reserved));
            scroll.setLayoutParams(params);
        }
        int editorWidth = title == R.string.piece_setup_title
                ? Math.min(720, Math.round(width * 0.88f)) : 720;
        dialog.getWindow().setLayout(dp(context, Math.min(width - 32,
                twoColumns(context) ? editorWidth : 520)), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static android.widget.ScrollView findScroll(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof android.widget.ScrollView) return (android.widget.ScrollView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.widget.ScrollView found = findScroll(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    static View title(Context context, int resource) {
        return title(context, resource, false);
    }

    static View title(Context context, int resource, boolean compact) {
        LinearLayout header = column(context);
        header.setPadding(dp(context, 22), dp(context, compact ? 8 : 16), dp(context, 22), dp(context, compact ? 6 : 10));
        TextView title = new TextView(context);
        title.setText(resource);
        title.setTextSize(compact ? 20 : 22);
        title.setTextColor(ContextCompat.getColor(context, R.color.board_line));
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        title.setGravity(Gravity.START);
        header.addView(title);
        View rule = new View(context);
        rule.setBackgroundColor(ContextCompat.getColor(context, R.color.board_line));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 2));
        params.topMargin = dp(context, compact ? 4 : 10);
        header.addView(rule, params);
        return header;
    }

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static void addColumn(LinearLayout row, LinearLayout column, boolean wide) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                wide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, wide ? 1f : 0f);
        if (wide) {
            params.setMargins(dp(row.getContext(), 8), 0, dp(row.getContext(), 8), 0);
        }
        row.addView(column, params);
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
