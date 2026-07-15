package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import java.util.List;

final class YutDialogs {
    private YutDialogs() {
    }

    static void showSettings(Context context, SettingsState state, SettingsActions actions, Runnable onDismiss) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 4));
        scrollView.addView(content);

        content.addView(createSettingsLabel(context, "\ud134 \uc2dc\uac04"));

        RadioGroup turnGroup = new RadioGroup(context);
        turnGroup.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < GameStateStore.TURN_DURATION_LABELS.length; i++) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setTag(i);
            option.setText(GameStateStore.TURN_DURATION_LABELS[i]);
            option.setTextColor(context.getResources().getColor(R.color.text_primary));
            option.setTextSize(15);
            option.setButtonTintList(context.getResources().getColorStateList(R.color.text_status));
            option.setPadding(0, dp(context, 2), 0, dp(context, 2));
            turnGroup.addView(option);
            if (i == state.checkedTurnIndex) {
                turnGroup.check(option.getId());
            }
        }
        content.addView(turnGroup);

        TextView feedbackLabel = createSettingsLabel(context, "\ud45c\ud604");
        LinearLayout.LayoutParams feedbackLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        feedbackLabelParams.setMargins(0, dp(context, 14), 0, 0);
        feedbackLabel.setLayoutParams(feedbackLabelParams);
        content.addView(feedbackLabel);

        CheckBox soundBox = createSettingsCheckBox(context, "\ud6a8\uacfc\uc74c", state.soundEnabled);
        CheckBox vibrationBox = createSettingsCheckBox(context, "\uc9c4\ub3d9", state.vibrationEnabled);
        content.addView(soundBox);
        content.addView(vibrationBox);

        TextView logLabel = createSettingsLabel(context, "\uae30\ub85d");
        LinearLayout.LayoutParams logLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        logLabelParams.setMargins(0, dp(context, 14), 0, 0);
        logLabel.setLayoutParams(logLabelParams);
        content.addView(logLabel);

        TextView logDescription = new TextView(context);
        logDescription.setText("\ucd5c\uadfc \uae30\ub85d " + state.logSize + "\uac1c\uac00 \uc800\uc7a5\ub418\uc5b4 \uc788\uc2b5\ub2c8\ub2e4.");
        logDescription.setTextColor(context.getResources().getColor(R.color.text_secondary));
        logDescription.setTextSize(13);
        logDescription.setPadding(0, dp(context, 5), 0, 0);
        content.addView(logDescription);

        Button logViewButton = new Button(context);
        logViewButton.setText("\uc804\uccb4 \uae30\ub85d \ubcf4\uae30");
        logViewButton.setAllCaps(false);
        logViewButton.setTextColor(context.getResources().getColor(R.color.text_primary));
        logViewButton.setTextSize(13);
        logViewButton.setBackgroundResource(R.drawable.shape_button_utility);
        logViewButton.setBackgroundTintList(null);
        logViewButton.setMinWidth(0);
        logViewButton.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        LinearLayout.LayoutParams logViewButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40));
        logViewButtonParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(logViewButton, logViewButtonParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("\uc124\uc815")
                .setView(scrollView)
                .setPositiveButton("\uc801\uc6a9", null)
                .setNeutralButton("\uae30\ub85d \ucd08\uae30\ud654", null)
                .setNegativeButton("\ub2eb\uae30", null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checkedId = turnGroup.getCheckedRadioButtonId();
            RadioButton selected = turnGroup.findViewById(checkedId);
            int selectedIndex = selected == null ? state.checkedTurnIndex : (int) selected.getTag();
            actions.onApply(selectedIndex, soundBox.isChecked(), vibrationBox.isChecked());
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            actions.onClearLog();
            dialog.dismiss();
        });
        logViewButton.setOnClickListener(v -> actions.onShowLog());
    }

    static void showNewGameConfirmation(Context context, Runnable onConfirm, Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.new_game_title)
                .setMessage(R.string.new_game_message)
                .setPositiveButton(R.string.new_game_confirm_ready, null)
                .setNegativeButton(R.string.no, null)
                .create();

        CountDownTimer[] countdown = new CountDownTimer[1];
        dialog.setOnShowListener(ignored -> {
            Button confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirmButton.setEnabled(false);
            confirmButton.setOnClickListener(v -> {
                onConfirm.run();
                dialog.dismiss();
            });

            countdown[0] = new CountDownTimer(5_000L, 1_000L) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long seconds = Math.max(1L, (millisUntilFinished + 999L) / 1_000L);
                    confirmButton.setText(context.getString(R.string.new_game_confirm_countdown, seconds));
                }

                @Override
                public void onFinish() {
                    confirmButton.setText(R.string.new_game_confirm_ready);
                    confirmButton.setEnabled(true);
                }
            };
            countdown[0].start();
        });
        dialog.setOnDismissListener(ignored -> {
            if (countdown[0] != null) {
                countdown[0].cancel();
            }
            onDismiss.run();
        });
        dialog.show();
    }

    static void showTurnLog(Context context, List<String> turnLog, Runnable onClearLog, Runnable onDismiss) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        TextView logView = new TextView(context);
        logView.setText(turnLog.isEmpty() ? "\uae30\ub85d \uc5c6\uc74c" : buildTurnLogText(turnLog, turnLog.size()));
        logView.setTextColor(context.getResources().getColor(R.color.text_primary));
        logView.setTextSize(14);
        logView.setLineSpacing(dp(context, 2), 1f);
        logView.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
        scrollView.addView(logView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("\uc804\uccb4 \uae30\ub85d")
                .setView(scrollView)
                .setPositiveButton("\ub2eb\uae30", null)
                .setNeutralButton("\uae30\ub85d \ucd08\uae30\ud654", null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            onClearLog.run();
            dialog.dismiss();
        });
    }

    static void showTurnLogEntry(Context context, int index, String entry, Runnable onShowAll, Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("\uae30\ub85d " + index)
                .setMessage(entry)
                .setPositiveButton("\ub2eb\uae30", null)
                .setNeutralButton("\uc804\uccb4 \uae30\ub85d", null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            dialog.dismiss();
            onShowAll.run();
        });
    }

    static void showGameOver(Context context, String title, String message, Runnable onNewGame, Runnable onShowLog) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("\uc0c8 \uac8c\uc784", (dialog, which) -> onNewGame.run())
                .setNeutralButton("\uc804\uccb4 \uae30\ub85d", (dialog, which) -> onShowLog.run())
                .setNegativeButton("\ud310 \ubcf4\uae30", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    static String buildTurnLogText(List<String> turnLog, int maxCount) {
        if (turnLog.isEmpty()) {
            return "\uae30\ub85d \uc5c6\uc74c";
        }
        int limit = Math.min(turnLog.size(), maxCount);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(i + 1)
                    .append(". ")
                    .append(turnLog.get(i));
        }
        return builder.toString();
    }

    private static TextView createSettingsLabel(Context context, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextColor(context.getResources().getColor(R.color.text_primary));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private static CheckBox createSettingsCheckBox(Context context, String text, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(text);
        checkBox.setChecked(checked);
        checkBox.setTextColor(context.getResources().getColor(R.color.text_primary));
        checkBox.setTextSize(15);
        checkBox.setButtonTintList(context.getResources().getColorStateList(R.color.text_status));
        checkBox.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return checkBox;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static class SettingsState {
        final int checkedTurnIndex;
        final boolean soundEnabled;
        final boolean vibrationEnabled;
        final int logSize;

        SettingsState(int checkedTurnIndex, boolean soundEnabled, boolean vibrationEnabled, int logSize) {
            this.checkedTurnIndex = checkedTurnIndex;
            this.soundEnabled = soundEnabled;
            this.vibrationEnabled = vibrationEnabled;
            this.logSize = logSize;
        }
    }

    interface SettingsActions {
        void onApply(int turnIndex, boolean soundEnabled, boolean vibrationEnabled);

        void onClearLog();

        void onShowLog();
    }
}
