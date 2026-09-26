package com.example.yutnoriapp;

import android.content.Context;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/* JADX INFO: loaded from: classes3.dex */
final class GameFeedback {
    static final int CATCH = 2;
    static final int MOVE = 1;
    static final int TAP = 0;
    static final int WIN = 3;
    private final Vibrator vibrator;
    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;
    private ToneGenerator toneGenerator = new ToneGenerator(3, 45);

    GameFeedback(Context context) {
        this.vibrator = (Vibrator) context.getSystemService("vibrator");
    }

    void setEnabled(boolean soundEnabled, boolean vibrationEnabled) {
        this.soundEnabled = soundEnabled;
        this.vibrationEnabled = vibrationEnabled;
    }

    void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    void setVibrationEnabled(boolean vibrationEnabled) {
        this.vibrationEnabled = vibrationEnabled;
    }

    boolean isSoundEnabled() {
        return this.soundEnabled;
    }

    boolean isVibrationEnabled() {
        return this.vibrationEnabled;
    }

    void play(int type) {
        if (this.soundEnabled && this.toneGenerator != null) {
            int tone = 24;
            int duration = 35;
            if (type == 1) {
                tone = 24;
                duration = 55;
            } else if (type == 2) {
                tone = 25;
                duration = 90;
            } else if (type == 3) {
                tone = 25;
                duration = 180;
            }
            this.toneGenerator.startTone(tone, duration);
        }
        if (!this.vibrationEnabled || this.vibrator == null || !this.vibrator.hasVibrator()) {
            return;
        }
        long duration2 = 18;
        int amplitude = 40;
        if (type == 1) {
            duration2 = 28;
            amplitude = 55;
        } else if (type == 2) {
            duration2 = 75;
            amplitude = 110;
        } else if (type == 3) {
            duration2 = 140;
            amplitude = 150;
        }
        int i = Build.VERSION.SDK_INT;
        Vibrator vibrator = this.vibrator;
        if (i >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration2, amplitude));
        } else {
            vibrator.vibrate(duration2);
        }
    }

    void release() {
        if (this.toneGenerator != null) {
            this.toneGenerator.release();
            this.toneGenerator = null;
        }
    }
}
