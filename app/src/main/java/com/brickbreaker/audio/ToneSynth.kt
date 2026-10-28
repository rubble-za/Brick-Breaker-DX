package com.brickbreaker.audio

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Generates the game's sound effects as 16-bit mono PCM, so the app needs no audio files. */
object ToneSynth {
    const val SAMPLE_RATE = 22050
    private const val ATTACK_MS = 4

    /** A tone gliding from [startHz] to [endHz] over [ms], fading out linearly. [volume] is 0..1. */
    fun tone(startHz: Float, endHz: Float, ms: Int, volume: Float = 0.5f): ShortArray {
        val count = SAMPLE_RATE * ms / 1000
        val attack = SAMPLE_RATE * ATTACK_MS / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val hz = startHz + (endHz - startHz) * t
            phase += 2 * PI * hz / SAMPLE_RATE
            // A little third harmonic gives a slightly chiptune edge to the plain sine.
            val wave = sin(phase) + 0.25 * sin(3 * phase)
            val envelope = min(1f, i.toFloat() / attack) * (1f - t) // short attack avoids clicks
            out[i] = (wave / 1.25 * envelope * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    fun sequence(vararg parts: ShortArray): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) { p.copyInto(out, at); at += p.size }
        return out
    }
}
