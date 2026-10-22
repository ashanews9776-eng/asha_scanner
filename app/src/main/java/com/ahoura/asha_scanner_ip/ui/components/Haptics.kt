package com.ahoura.asha_scanner_ip.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * CyberHaptics provides crisp, mechanical tactile feedback throughout the app,
 * giving buttons and state transitions a premium physical feel.
 */
object CyberHaptics {

    /**
     * Tactile click for mechanical-like actions (e.g. big power button toggle).
     */
    fun click(haptic: HapticFeedback) {
        runCatching {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Subtle tick for tab switches, segmented modes, and toggle changes.
     */
    fun tick(haptic: HapticFeedback) {
        runCatching {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Vibrator-based success waveform (two crisp micro-pulses) when a scan
     * completes or when VPN successfully establishes a tunnel.
     */
    fun success(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 30, 50, 40)
                val amplitudes = intArrayOf(0, 180, 0, 240)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 30, 50, 40), -1)
            }
        }
    }

    /**
     * Crisp pulse when copying IP or config link.
     */
    fun copySuccess(context: Context, haptic: HapticFeedback) {
        tick(haptic)
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(35, 160))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(35)
            }
        }
    }

    /**
     * Error rumble pulse.
     */
    fun error(context: Context) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 60, 40, 60)
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 60, 40, 60), -1)
            }
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
