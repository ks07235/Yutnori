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
    static final int SELECT = 4;

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
        playTone(type);
        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) {
            return;
        }

        if (type == CATCH) {
            vibratePattern(
                    new long[]{0L, 38L, 45L, 52L},
                    new int[]{0, 112, 0, 82});
            return;
        }
        if (type == WIN) {
            vibratePattern(
                    new long[]{0L, 70L, 55L, 115L},
                    new int[]{0, 105, 0, 150});
            return;
        }

        long duration = 12L;
        int amplitude = 30;
        if (type == SELECT) {
            duration = 17L;
            amplitude = 45;
        } else if (type == MOVE) {
            duration = 30L;
            amplitude = 68;
        }
        vibrateOneShot(duration, amplitude);
    }

    private void playTone(int type) {
        if (!soundEnabled || toneGenerator == null) {
            return;
        }

        int tone = ToneGenerator.TONE_PROP_BEEP;
        int duration = 35;
        if (type == SELECT) {
            duration = 24;
        } else if (type == MOVE) {
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

    private void vibrateOneShot(long duration, int amplitude) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
        } else {
            vibrator.vibrate(duration);
        }
    }

    private void vibratePattern(long[] timings, int[] amplitudes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            vibrator.vibrate(timings, -1);
        }
    }

    void release() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}
