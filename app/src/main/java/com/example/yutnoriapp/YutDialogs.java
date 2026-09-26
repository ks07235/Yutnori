package com.example.yutnoriapp;

import android.content.Context;
import android.content.DialogInterface;
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

/* JADX INFO: loaded from: classes3.dex */
final class YutDialogs {

    interface SettingsActions {
        void onApply(int i, boolean z, boolean z2);

        void onClearLog();

        void onShowLog();
    }

    private YutDialogs() {
    }

    static void showSettings(final Context context, final SettingsState state, final SettingsActions actions, final Runnable onDismiss) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(1);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 4));
        scrollView.addView(linearLayout);
        linearLayout.addView(createSettingsLabel(context, R.string.settings_turn_time));
        String[] durationLabels = context.getResources().getStringArray(R.array.turn_duration_labels);
        final RadioGroup turnGroup = new RadioGroup(context);
        turnGroup.setOrientation(1);
        for (int i = 0; i < GameStateStore.TURN_DURATION_OPTIONS_MILLIS.length; i++) {
            RadioButton option = new RadioButton(context);
            option.setId(View.generateViewId());
            option.setTag(Integer.valueOf(i));
            option.setText(durationLabels[i]);
            option.setTextColor(context.getResources().getColor(R.color.text_primary));
            option.setTextSize(15.0f);
            option.setButtonTintList(context.getResources().getColorStateList(R.color.text_status));
            option.setPadding(0, dp(context, 2), 0, dp(context, 2));
            turnGroup.addView(option);
            if (i == state.checkedTurnIndex) {
                turnGroup.check(option.getId());
            }
        }
        linearLayout.addView(turnGroup);
        TextView feedbackLabel = createSettingsLabel(context, R.string.settings_feedback);
        LinearLayout.LayoutParams feedbackLabelParams = new LinearLayout.LayoutParams(-1, -2);
        feedbackLabelParams.setMargins(0, dp(context, 14), 0, 0);
        feedbackLabel.setLayoutParams(feedbackLabelParams);
        linearLayout.addView(feedbackLabel);
        final CheckBox soundBox = createSettingsCheckBox(context, R.string.settings_sound, state.soundEnabled);
        final CheckBox vibrationBox = createSettingsCheckBox(context, R.string.settings_vibration, state.vibrationEnabled);
        linearLayout.addView(soundBox);
        linearLayout.addView(vibrationBox);
        TextView logLabel = createSettingsLabel(context, R.string.history);
        LinearLayout.LayoutParams logLabelParams = new LinearLayout.LayoutParams(-1, -2);
        logLabelParams.setMargins(0, dp(context, 14), 0, 0);
        logLabel.setLayoutParams(logLabelParams);
        linearLayout.addView(logLabel);
        TextView logDescription = new TextView(context);
        logDescription.setText(context.getString(R.string.history_saved_count, Integer.valueOf(state.logSize)));
        logDescription.setTextColor(context.getResources().getColor(R.color.text_secondary));
        logDescription.setTextSize(13.0f);
        logDescription.setPadding(0, dp(context, 5), 0, 0);
        linearLayout.addView(logDescription);
        Button logViewButton = createUtilityButton(context, R.string.history_view_all);
        LinearLayout.LayoutParams logViewButtonParams = new LinearLayout.LayoutParams(-1, dp(context, 40));
        logViewButtonParams.setMargins(0, dp(context, 8), 0, 0);
        linearLayout.addView(logViewButton, logViewButtonParams);
        TextView aboutLabel = createSettingsLabel(context, R.string.about);
        LinearLayout.LayoutParams aboutLabelParams = new LinearLayout.LayoutParams(-1, -2);
        aboutLabelParams.setMargins(0, dp(context, 14), 0, 0);
        aboutLabel.setLayoutParams(aboutLabelParams);
        linearLayout.addView(aboutLabel);
        Button privacyButton = createUtilityButton(context, R.string.privacy_policy);
        LinearLayout.LayoutParams privacyButtonParams = new LinearLayout.LayoutParams(-1, dp(context, 40));
        privacyButtonParams.setMargins(0, dp(context, 8), 0, 0);
        linearLayout.addView(privacyButton, privacyButtonParams);
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.settings).setView(scrollView).setPositiveButton(R.string.apply, (DialogInterface.OnClickListener) null).setNeutralButton(R.string.history_clear, (DialogInterface.OnClickListener) null).setNegativeButton(R.string.close, (DialogInterface.OnClickListener) null).show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda7
            @Override // android.content.DialogInterface.OnDismissListener
            public final void onDismiss(DialogInterface dialogInterface) {
                onDismiss.run();
            }
        });
        dialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda8
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.lambda$showSettings$1(turnGroup, state, actions, soundBox, vibrationBox, dialog, view);
            }
        });
        dialog.getButton(-3).setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda9
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.lambda$showSettings$2(actions, dialog, view);
            }
        });
        logViewButton.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda10
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                actions.onShowLog();
            }
        });
        privacyButton.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda11
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.showPrivacyPolicy(context);
            }
        });
    }

    static /* synthetic */ void lambda$showSettings$1(RadioGroup turnGroup, SettingsState state, SettingsActions actions, CheckBox soundBox, CheckBox vibrationBox, AlertDialog dialog, View v) {
        int checkedId = turnGroup.getCheckedRadioButtonId();
        RadioButton selected = (RadioButton) turnGroup.findViewById(checkedId);
        int selectedIndex = selected == null ? state.checkedTurnIndex : ((Integer) selected.getTag()).intValue();
        actions.onApply(selectedIndex, soundBox.isChecked(), vibrationBox.isChecked());
        dialog.dismiss();
    }

    static /* synthetic */ void lambda$showSettings$2(SettingsActions actions, AlertDialog dialog, View v) {
        actions.onClearLog();
        dialog.dismiss();
    }

    static void showNewGameConfirmation(final Context context, final Runnable onConfirm, final Runnable onDismiss) {
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.new_game_title).setMessage(R.string.new_game_message).setPositiveButton(R.string.new_game_confirm_ready, (DialogInterface.OnClickListener) null).setNegativeButton(R.string.no, (DialogInterface.OnClickListener) null).create();
        final CountDownTimer[] countdown = new CountDownTimer[1];
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda0
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                YutDialogs.lambda$showNewGameConfirmation$6(dialog, onConfirm, countdown, context, dialogInterface);
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda6
            @Override // android.content.DialogInterface.OnDismissListener
            public final void onDismiss(DialogInterface dialogInterface) {
                YutDialogs.lambda$showNewGameConfirmation$7(countdown, onDismiss, dialogInterface);
            }
        });
        dialog.show();
    }

    static /* synthetic */ void lambda$showNewGameConfirmation$6(final AlertDialog dialog, final Runnable onConfirm, CountDownTimer[] countdown, final Context context, DialogInterface ignored) {
        final Button confirmButton = dialog.getButton(-1);
        confirmButton.setEnabled(false);
        confirmButton.setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.lambda$showNewGameConfirmation$5(onConfirm, dialog, view);
            }
        });
        countdown[0] = new CountDownTimer(5000L, 1000L) { // from class: com.example.yutnoriapp.YutDialogs.1
            @Override // android.os.CountDownTimer
            public void onTick(long millisUntilFinished) {
                long seconds = Math.max(1L, (999 + millisUntilFinished) / 1000);
                confirmButton.setText(context.getString(R.string.new_game_confirm_countdown, Long.valueOf(seconds)));
            }

            @Override // android.os.CountDownTimer
            public void onFinish() {
                confirmButton.setText(R.string.new_game_confirm_ready);
                confirmButton.setEnabled(true);
            }
        };
        countdown[0].start();
    }

    static /* synthetic */ void lambda$showNewGameConfirmation$5(Runnable onConfirm, AlertDialog dialog, View v) {
        onConfirm.run();
        dialog.dismiss();
    }

    static /* synthetic */ void lambda$showNewGameConfirmation$7(CountDownTimer[] countdown, Runnable onDismiss, DialogInterface ignored) {
        if (countdown[0] != null) {
            countdown[0].cancel();
        }
        onDismiss.run();
    }

    static void showTurnLog(Context context, List<String> turnLog, final Runnable onClearLog, final Runnable onDismiss) {
        String strBuildTurnLogText;
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(1);
        TextView logView = new TextView(context);
        if (turnLog.isEmpty()) {
            strBuildTurnLogText = context.getString(R.string.history_none);
        } else {
            strBuildTurnLogText = buildTurnLogText(turnLog, turnLog.size());
        }
        logView.setText(strBuildTurnLogText);
        logView.setTextColor(context.getResources().getColor(R.color.text_primary));
        logView.setTextSize(14.0f);
        logView.setLineSpacing(dp(context, 2), 1.0f);
        logView.setPadding(dp(context, 20), dp(context, 14), dp(context, 20), dp(context, 14));
        scrollView.addView(logView);
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.history_all).setView(scrollView).setPositiveButton(R.string.close, (DialogInterface.OnClickListener) null).setNeutralButton(R.string.history_clear, (DialogInterface.OnClickListener) null).show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda14
            @Override // android.content.DialogInterface.OnDismissListener
            public final void onDismiss(DialogInterface dialogInterface) {
                onDismiss.run();
            }
        });
        dialog.getButton(-3).setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.lambda$showTurnLog$9(onClearLog, dialog, view);
            }
        });
    }

    static /* synthetic */ void lambda$showTurnLog$9(Runnable onClearLog, AlertDialog dialog, View v) {
        onClearLog.run();
        dialog.dismiss();
    }

    static void showTurnLogEntry(Context context, int index, String entry, final Runnable onShowAll, final Runnable onDismiss) {
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle(context.getString(R.string.history_entry_title, Integer.valueOf(index))).setMessage(entry).setPositiveButton(R.string.close, (DialogInterface.OnClickListener) null).setNeutralButton(R.string.history_all, (DialogInterface.OnClickListener) null).show();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda12
            @Override // android.content.DialogInterface.OnDismissListener
            public final void onDismiss(DialogInterface dialogInterface) {
                onDismiss.run();
            }
        });
        dialog.getButton(-3).setOnClickListener(new View.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                YutDialogs.lambda$showTurnLogEntry$11(dialog, onShowAll, view);
            }
        });
    }

    static /* synthetic */ void lambda$showTurnLogEntry$11(AlertDialog dialog, Runnable onShowAll, View v) {
        dialog.dismiss();
        onShowAll.run();
    }

    static void showGameOver(Context context, String title, String message, final Runnable onNewGame, final Runnable onShowLog) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message).setPositiveButton(R.string.game_over_new_game, new DialogInterface.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda3
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                onNewGame.run();
            }
        }).setNeutralButton(R.string.history_all, new DialogInterface.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda4
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                onShowLog.run();
            }
        }).setNegativeButton(R.string.game_over_view_board, new DialogInterface.OnClickListener() { // from class: com.example.yutnoriapp.YutDialogs$$ExternalSyntheticLambda5
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setCancelable(false).show();
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
            builder.append(i + 1).append(". ").append(turnLog.get(i));
        }
        return builder.toString();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void showPrivacyPolicy(Context context) {
        new AlertDialog.Builder(context).setTitle(R.string.privacy_policy).setMessage(R.string.privacy_policy_body).setPositiveButton(R.string.close, (DialogInterface.OnClickListener) null).show();
    }

    private static Button createUtilityButton(Context context, int textResId) {
        Button button = new Button(context);
        button.setText(textResId);
        button.setAllCaps(false);
        button.setTextColor(context.getResources().getColor(R.color.text_primary));
        button.setTextSize(13.0f);
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
        label.setTextSize(14.0f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private static CheckBox createSettingsCheckBox(Context context, int textResId, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(textResId);
        checkBox.setChecked(checked);
        checkBox.setTextColor(context.getResources().getColor(R.color.text_primary));
        checkBox.setTextSize(15.0f);
        checkBox.setButtonTintList(context.getResources().getColorStateList(R.color.text_status));
        checkBox.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return checkBox;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static class SettingsState {
        final int checkedTurnIndex;
        final int logSize;
        final boolean soundEnabled;
        final boolean vibrationEnabled;

        SettingsState(int checkedTurnIndex, boolean soundEnabled, boolean vibrationEnabled, int logSize) {
            this.checkedTurnIndex = checkedTurnIndex;
            this.soundEnabled = soundEnabled;
            this.vibrationEnabled = vibrationEnabled;
            this.logSize = logSize;
        }
    }
}
