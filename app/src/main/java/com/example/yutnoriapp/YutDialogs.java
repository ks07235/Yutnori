package com.example.yutnoriapp;

import android.content.Context;
import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.slider.Slider;
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

        content.addView(createSettingsLabel(context, R.string.settings_turn_time));
        TurnTimeSelector turnTimeSelector = addTurnTimeSelector(context, content, state.checkedTurnIndex);

        TextView feedbackLabel = createSettingsLabel(context, R.string.settings_feedback);
        LinearLayout.LayoutParams feedbackLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        feedbackLabelParams.setMargins(0, dp(context, 14), 0, 0);
        feedbackLabel.setLayoutParams(feedbackLabelParams);
        content.addView(feedbackLabel);

        CheckBox soundBox = createSettingsCheckBox(context, R.string.settings_sound, state.soundEnabled);
        CheckBox vibrationBox = createSettingsCheckBox(context, R.string.settings_vibration, state.vibrationEnabled);
        content.addView(soundBox);
        content.addView(vibrationBox);

        TextView logLabel = createSettingsLabel(context, R.string.history);
        LinearLayout.LayoutParams logLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        logLabelParams.setMargins(0, dp(context, 14), 0, 0);
        logLabel.setLayoutParams(logLabelParams);
        content.addView(logLabel);

        TextView logDescription = new TextView(context);
        logDescription.setText(context.getString(R.string.history_saved_count, state.logSize));
        logDescription.setTextColor(context.getResources().getColor(R.color.text_secondary));
        logDescription.setTextSize(13);
        logDescription.setPadding(0, dp(context, 5), 0, 0);
        content.addView(logDescription);

        Button logViewButton = createUtilityButton(context, R.string.history_view_all);
        LinearLayout.LayoutParams logViewButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40));
        logViewButtonParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(logViewButton, logViewButtonParams);

        Button helpButton = createUtilityButton(context, R.string.how_to_play);
        LinearLayout.LayoutParams helpButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40));
        helpButtonParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(helpButton, helpButtonParams);

        TextView aboutLabel = createSettingsLabel(context, R.string.about);
        LinearLayout.LayoutParams aboutLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        aboutLabelParams.setMargins(0, dp(context, 14), 0, 0);
        aboutLabel.setLayoutParams(aboutLabelParams);
        content.addView(aboutLabel);

        TextView versionText = new TextView(context);
        versionText.setText(context.getString(
                R.string.app_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE));
        versionText.setTextColor(context.getResources().getColor(R.color.text_secondary));
        versionText.setTextSize(13);
        versionText.setPadding(0, dp(context, 5), 0, 0);
        content.addView(versionText);

        Button updateButton = createUtilityButton(context, R.string.check_updates);
        LinearLayout.LayoutParams updateButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40));
        updateButtonParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(updateButton, updateButtonParams);

        Button privacyButton = createUtilityButton(context, R.string.privacy_policy);
        LinearLayout.LayoutParams privacyButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 40));
        privacyButtonParams.setMargins(0, dp(context, 8), 0, 0);
        content.addView(privacyButton, privacyButtonParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.settings)
                .setView(scrollView)
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.history_clear, null)
                .setNegativeButton(R.string.close, null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            actions.onApply(turnTimeSelector.selectedIndex(), soundBox.isChecked(), vibrationBox.isChecked());
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            actions.onClearLog();
            dialog.dismiss();
        });
        logViewButton.setOnClickListener(v -> actions.onShowLog());
        helpButton.setOnClickListener(v -> actions.onShowHelp());
        updateButton.setOnClickListener(v -> actions.onCheckUpdates());
        privacyButton.setOnClickListener(v -> showPrivacyPolicy(context));
    }

    static void showGameRules(
            Context context,
            int checkedTurnIndex,
            GameRulesActions actions,
            Runnable onDismiss) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 2));

        TextView description = new TextView(context);
        description.setText(R.string.game_rules_description);
        description.setTextColor(context.getResources().getColor(R.color.text_secondary));
        description.setTextSize(14);
        content.addView(description);

        TurnTimeSelector selector = addTurnTimeSelector(context, content, checkedTurnIndex);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.game_rules)
                .setView(content)
                .setPositiveButton(R.string.apply, null)
                .setNegativeButton(R.string.close, null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            actions.onApply(selector.selectedIndex());
            dialog.dismiss();
        });
    }

    static void showUpdateAvailable(Context context, Runnable onUpdate, Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.update_available_title)
                .setMessage(R.string.update_available_message)
                .setPositiveButton(R.string.update_now, (ignored, which) -> onUpdate.run())
                .setNegativeButton(R.string.update_later, null)
                .create();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.show();
    }

    static void showSavedGameChoice(Context context, Runnable onContinue, Runnable onNewGame) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.saved_game_title)
                .setMessage(R.string.saved_game_message)
                .setPositiveButton(R.string.continue_game, (ignored, which) -> onContinue.run())
                .setNegativeButton(R.string.game_over_new_game, (ignored, which) -> onNewGame.run())
                .setCancelable(false)
                .show();
    }

    static void showEndTurnConfirmation(
            Context context,
            int pendingResultCount,
            Runnable onConfirm,
            Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.end_turn_pending_title)
                .setMessage(context.getResources().getQuantityString(
                        R.plurals.end_turn_pending_message,
                        pendingResultCount,
                        pendingResultCount))
                .setPositiveButton(R.string.end_turn, (ignored, which) -> onConfirm.run())
                .setNegativeButton(R.string.keep_playing, null)
                .create();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.show();
    }

    static void showHowToPlay(Context context, Runnable onDismiss) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        TextView instructions = new TextView(context);
        instructions.setText(R.string.how_to_play_body);
        instructions.setTextColor(context.getResources().getColor(R.color.text_primary));
        instructions.setTextSize(15);
        instructions.setLineSpacing(dp(context, 2), 1f);
        instructions.setPadding(dp(context, 22), dp(context, 8), dp(context, 22), dp(context, 8));
        scrollView.addView(instructions);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.how_to_play)
                .setView(scrollView)
                .setPositiveButton(R.string.close, null)
                .create();
        dialog.setOnDismissListener(ignored -> onDismiss.run());
        dialog.show();
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
        logView.setText(turnLog.isEmpty()
                ? context.getString(R.string.history_none)
                : buildTurnLogText(turnLog, turnLog.size()));
        logView.setTextColor(context.getResources().getColor(R.color.text_primary));
        logView.setTextSize(14);
        logView.setLineSpacing(dp(context, 2), 1f);
        logView.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
        scrollView.addView(logView);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.history_all)
                .setView(scrollView)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.history_clear, null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            onClearLog.run();
            dialog.dismiss();
        });
    }

    static void showTurnLogEntry(Context context, int index, String entry, Runnable onShowAll, Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.history_entry_title, index))
                .setMessage(entry)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.history_all, null)
                .show();
        dialog.setOnDismissListener(ignored -> onDismiss.run());

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            dialog.dismiss();
            onShowAll.run();
        });
    }

    static void showGameOver(Context context, String title, String message, Runnable onNewGame,
            Runnable onShowLog, Runnable onAcknowledged) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.game_over_new_game, (dialog, which) -> {
                    onAcknowledged.run();
                    onNewGame.run();
                })
                .setNeutralButton(R.string.history_all, (dialog, which) -> {
                    onAcknowledged.run();
                    onShowLog.run();
                })
                .setNegativeButton(R.string.game_over_view_board, (dialog, which) -> onAcknowledged.run())
                .setCancelable(false)
                .show();
    }

    static String buildTurnLogText(List<String> turnLog, int maxCount) {
        if (turnLog.isEmpty()) {
            return "";
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

    private static TurnTimeSelector addTurnTimeSelector(
            Context context,
            LinearLayout content,
            int checkedTurnIndex) {
        String[] labels = context.getResources().getStringArray(R.array.turn_duration_labels);
        int maxIndex = labels.length - 1;
        int initialIndex = Math.max(0, Math.min(maxIndex, checkedTurnIndex));

        TextView selectedValue = new TextView(context);
        selectedValue.setGravity(android.view.Gravity.CENTER);
        selectedValue.setTextColor(context.getResources().getColor(R.color.text_primary));
        selectedValue.setTextSize(22);
        selectedValue.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        selectedParams.setMargins(0, dp(context, 12), 0, 0);
        content.addView(selectedValue, selectedParams);

        Slider slider = new Slider(context);
        slider.setValueFrom(0f);
        slider.setValueTo(maxIndex);
        slider.setStepSize(1f);
        slider.setValue(initialIndex);
        slider.setTickVisible(true);
        slider.setLabelFormatter(value -> labels[Math.round(value)]);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sliderParams.setMargins(0, dp(context, 4), 0, 0);
        content.addView(slider, sliderParams);

        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            TextView tickLabel = new TextView(context);
            tickLabel.setText(label);
            tickLabel.setGravity(android.view.Gravity.CENTER);
            tickLabel.setTextColor(context.getResources().getColor(R.color.text_secondary));
            tickLabel.setTextSize(11);
            tickLabel.setMaxLines(1);
            labelRow.addView(tickLabel, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f));
        }
        content.addView(labelRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView unlimitedNote = new TextView(context);
        unlimitedNote.setText(R.string.game_rules_unlimited_note);
        unlimitedNote.setTextColor(context.getResources().getColor(R.color.text_secondary));
        unlimitedNote.setTextSize(12);
        unlimitedNote.setLineSpacing(dp(context, 2), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(context, 14), 0, dp(context, 4));
        content.addView(unlimitedNote, noteParams);

        TurnTimeSelector selector = new TurnTimeSelector(slider, labels);
        selector.updateSelectedValue(context, selectedValue);
        slider.addOnChangeListener((control, value, fromUser) -> {
            selector.updateSelectedValue(context, selectedValue);
            if (fromUser) {
                control.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
        });
        return selector;
    }

    private static void showPrivacyPolicy(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.privacy_policy)
                .setMessage(R.string.privacy_policy_body)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static Button createUtilityButton(Context context, int textResId) {
        Button button = new Button(context);
        button.setText(textResId);
        button.setAllCaps(false);
        button.setTextColor(context.getResources().getColor(R.color.text_primary));
        button.setTextSize(13);
        button.setBackgroundResource(R.drawable.shape_button_utility);
        button.setBackgroundTintList(null);
        button.setMinWidth(0);
        button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        return button;
    }

    private static TextView createSettingsLabel(Context context, int textResId) {
        TextView label = new TextView(context);
        label.setText(textResId);
        label.setTextColor(context.getResources().getColor(R.color.text_primary));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private static CheckBox createSettingsCheckBox(Context context, int textResId, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(textResId);
        checkBox.setChecked(checked);
        checkBox.setTextColor(context.getResources().getColor(R.color.text_primary));
        checkBox.setTextSize(15);
        checkBox.setButtonTintList(ContextCompat.getColorStateList(context, R.color.text_status));
        checkBox.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return checkBox;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class TurnTimeSelector {
        private final Slider slider;
        private final String[] labels;

        TurnTimeSelector(Slider slider, String[] labels) {
            this.slider = slider;
            this.labels = labels;
        }

        int selectedIndex() {
            return Math.max(0, Math.min(labels.length - 1, Math.round(slider.getValue())));
        }

        void updateSelectedValue(Context context, TextView selectedValue) {
            String label = labels[selectedIndex()];
            selectedValue.setText(label);
            slider.setContentDescription(context.getString(R.string.game_rules_summary, label));
        }
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

        void onShowHelp();

        void onCheckUpdates();
    }

    interface GameRulesActions {
        void onApply(int turnIndex);
    }
}
