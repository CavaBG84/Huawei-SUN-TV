package com.huaweitv.solar.powerguard;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Zero-dependency local acoustic alarm controller using {@link ToneGenerator}.
 * Compatible with Android 4.4.4 KitKat (API 19+) and safe on devices without audio hardware.
 */
public class AlarmSoundController {

    private ToneGenerator toneGenerator;
    private boolean initFailed = false;

    private ToneGenerator getOrCreateGenerator() {
        if (initFailed) return null;
        if (toneGenerator != null) return toneGenerator;
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 85);
            return toneGenerator;
        } catch (Throwable t1) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
                return toneGenerator;
            } catch (Throwable t2) {
                initFailed = true;
                return null;
            }
        }
    }

    public void playWarningBeep() {
        try {
            ToneGenerator gen = getOrCreateGenerator();
            if (gen != null) {
                gen.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
            }
        } catch (Throwable ignored) {}
    }

    public void playCriticalBeep() {
        try {
            ToneGenerator gen = getOrCreateGenerator();
            if (gen != null) {
                gen.startTone(ToneGenerator.TONE_PROP_BEEP2, 260);
            }
        } catch (Throwable ignored) {}
    }

    public void release() {
        if (toneGenerator != null) {
            try {
                toneGenerator.release();
            } catch (Throwable ignored) {}
            toneGenerator = null;
        }
    }
}
