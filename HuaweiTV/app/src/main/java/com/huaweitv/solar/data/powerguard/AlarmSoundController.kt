package com.huaweitv.solar.data.powerguard

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Zero-dependency local acoustic alarm controller using [ToneGenerator].
 * Gracefully handles devices/TV boxes without audio hardware.
 */
class AlarmSoundController {

    private var toneGenerator: ToneGenerator? = null
    private var initFailed = false

    private fun getOrCreateGenerator(): ToneGenerator? {
        if (initFailed) return null
        val existing = toneGenerator
        if (existing != null) return existing
        return try {
            ToneGenerator(AudioManager.STREAM_ALARM, 85).also { toneGenerator = it }
        } catch (_: Throwable) {
            try {
                ToneGenerator(AudioManager.STREAM_MUSIC, 85).also { toneGenerator = it }
            } catch (_: Throwable) {
                initFailed = true
                null
            }
        }
    }

    fun playWarningBeep() {
        runCatching {
            getOrCreateGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        }
    }

    fun playCriticalBeep() {
        runCatching {
            getOrCreateGenerator()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
        }
    }

    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }
}
