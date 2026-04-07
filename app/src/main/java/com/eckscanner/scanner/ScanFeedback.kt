package com.eckscanner.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object ScanFeedback {

    /** Short vibration + beep for successful scan */
    fun success(context: Context) {
        vibrate(context, 50)
        tone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    /** Double vibration for product not found */
    fun error(context: Context) {
        vibrate(context, longArrayOf(0, 80, 60, 80))
        tone(ToneGenerator.TONE_PROP_NACK, 100)
    }

    /** Single short vibration for duplicate item */
    fun duplicate(context: Context) {
        vibrate(context, 30)
    }

    private fun vibrate(context: Context, millis: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(millis)
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun tone(toneType: Int, durationMs: Int) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            toneGen.startTone(toneType, durationMs)
        } catch (_: Exception) { }
    }
}
