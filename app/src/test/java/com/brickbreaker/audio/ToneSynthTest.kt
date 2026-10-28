package com.brickbreaker.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneSynthTest {
    @Test
    fun toneHasRequestedLength() {
        val t = ToneSynth.tone(440f, 440f, 100)
        assertEquals(ToneSynth.SAMPLE_RATE / 10, t.size)
    }

    @Test
    fun toneStaysWithinVolumeAndIsAudible() {
        val t = ToneSynth.tone(440f, 880f, 100, volume = 0.5f)
        val peak = t.maxOf { abs(it.toInt()) }
        assertTrue("should be audible", peak > 1000)
        assertTrue("should respect volume", peak <= Short.MAX_VALUE * 0.5f + 1)
    }

    @Test
    fun toneFadesOutToSilence() {
        val t = ToneSynth.tone(440f, 440f, 100)
        val tail = t.takeLast(20).maxOf { abs(it.toInt()) }
        assertTrue(tail < 1000)
    }

    @Test
    fun sequenceConcatenates() {
        val a = ToneSynth.tone(300f, 300f, 20)
        val b = ToneSynth.tone(600f, 600f, 30)
        val s = ToneSynth.sequence(a, b)
        assertEquals(a.size + b.size, s.size)
        assertEquals(b[5], s[a.size + 5])
    }
}
