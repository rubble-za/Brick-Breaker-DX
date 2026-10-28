package com.brickbreaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.brickbreaker.engine.GameEvent
import java.util.EnumMap
import java.util.concurrent.Executors

/** Plays the synthesised effect for each [GameEvent]. Each effect is a preloaded static [AudioTrack]. */
class SoundBoard {
    private val tracks = EnumMap<GameEvent, AudioTrack>(GameEvent::class.java)

    init {
        for (event in GameEvent.entries) tracks[event] = build(samplesFor(event))
    }

    // AudioTrack stop/reload/play can block for several milliseconds, which shows up as a hitch if it
    // runs on the game loop, so every call is handed to this single background thread (order preserved).
    private val worker = Executors.newSingleThreadExecutor()

    fun play(event: GameEvent) {
        val track = tracks[event] ?: return
        worker.execute {
            try {
                track.stop()
                track.reloadStaticData()
                track.play()
            } catch (_: IllegalStateException) {
                // The audio system can reject a call mid-teardown; a missed blip isn't worth crashing for.
            }
        }
    }

    fun release() {
        worker.execute {
            tracks.values.forEach { it.release() }
            tracks.clear()
        }
        worker.shutdown()
    }

    private fun build(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    companion object {
        fun samplesFor(event: GameEvent): ShortArray = when (event) {
            GameEvent.LAUNCH -> ToneSynth.tone(300f, 500f, 80)
            GameEvent.WALL_HIT -> ToneSynth.tone(220f, 220f, 30, 0.3f)
            GameEvent.PADDLE_HIT -> ToneSynth.tone(440f, 440f, 50)
            GameEvent.BRICK_HIT -> ToneSynth.tone(520f, 520f, 40)
            GameEvent.STEEL_HIT -> ToneSynth.tone(180f, 160f, 60)
            GameEvent.BRICK_BREAK -> ToneSynth.tone(700f, 900f, 70)
            GameEvent.POWERUP -> ToneSynth.tone(500f, 1000f, 150)
            GameEvent.SHOOT -> ToneSynth.tone(1000f, 600f, 40, 0.3f)
            GameEvent.STICK -> ToneSynth.tone(250f, 330f, 60)
            GameEvent.SPLIT -> ToneSynth.tone(600f, 1100f, 60, 0.4f)
            GameEvent.LIFE_LOST -> ToneSynth.tone(400f, 100f, 400)
            GameEvent.LEVEL_CLEAR -> ToneSynth.sequence(
                ToneSynth.tone(523f, 523f, 90),
                ToneSynth.tone(659f, 659f, 90),
                ToneSynth.tone(784f, 784f, 90),
                ToneSynth.tone(1047f, 1047f, 200),
            )
            GameEvent.GAME_OVER -> ToneSynth.sequence(
                ToneSynth.tone(300f, 200f, 200),
                ToneSynth.tone(200f, 100f, 400),
            )
        }
    }
}
