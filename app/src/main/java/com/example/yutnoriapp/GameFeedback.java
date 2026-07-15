package com.example.yutnoriapp;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

final class GameFeedback {
    static final int TAP = 0;
    static final int MOVE = 1;
    static final int CATCH = 2;
    static final int WIN = 3;

    private final Vibrator vibrator;
    private ToneGenerator toneGenerator;
    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;

    GameFeedback(Context context) {
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 45);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
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
        return soundEnabled;
    }

    boolean isVibrationEnabled() {
        return vibrationEnabled;
    }

    void play(int type) {
        if (soundEnabled && toneGenerator != null) {
            int tone = ToneGenerator.TONE_PROP_BEEP;
            int duration = 35;
            if (type == MOVE) {
                tone = ToneGenerator.TONE_PROP_BEEP;
                duration = 55;
            } else if (type == CATCH) {
                tone = ToneGenerator.TONE_PROP_ACK;
                duration = 90;
            } else if (type == WIN) {
                tone = ToneGenerator.TONE_PROP_ACK;
                duration = 180;
            }
            toneGenerator.startTone(tone, duration);
        }

        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        long duration = 18L;
        int amplitude = 40;
        if (type == MOVE) {
            duration = 28L;
            amplitude = 55;
        } else if (type == CATCH) {
            duration = 75L;
            amplitude = 110;
        } else if (type == WIN) {
            duration = 140L;
            amplitude = 150;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
        } else {
            vibrator.vibrate(duration);
        }
    }

    void release() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
