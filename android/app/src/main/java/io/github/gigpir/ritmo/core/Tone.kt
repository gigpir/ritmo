package io.github.gigpir.ritmo.core

import kotlin.math.PI
import kotlin.math.sin

/** Beeps generated as 16-bit mono samples, so the app needs no sound files. */
object Tone {
    const val SAMPLE_RATE = 44_100

    fun beep(frequency: Double, seconds: Double): ShortArray {
        val count = (seconds * SAMPLE_RATE).toInt()
        val ramp = SAMPLE_RATE / 200.0 // 5 ms fade in and out, so the beep doesn't click
        return ShortArray(count) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val fade = minOf(1.0, i / ramp, (count - i) / ramp)
            (sin(2 * PI * frequency * t) * fade * 0.8 * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
